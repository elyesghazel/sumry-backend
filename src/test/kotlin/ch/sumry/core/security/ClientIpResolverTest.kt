package ch.sumry.core.security

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.ForwardedHeaderFilter

class ClientIpResolverTest {

    private fun request(xff: String? = null, cf: String? = null) = MockHttpServletRequest().apply {
        remoteAddr = "10.0.0.1"
        xff?.let { addHeader("X-Forwarded-For", it) }
        cf?.let { addHeader("CF-Connecting-IP", it) }
    }

    @Test
    fun `empty config uses the socket address and ignores X-Forwarded-For`() {
        val resolver = ClientIpResolver(null, null)
        assertEquals("10.0.0.1", resolver.resolve(request(xff = "198.51.100.1")))
        assertEquals("10.0.0.1", ClientIpResolver("  ", 0).resolve(request(xff = "198.51.100.1")))
    }

    @Test
    fun `one hop takes the rightmost X-Forwarded-For entry`() {
        val resolver = ClientIpResolver(null, 1)
        assertEquals("203.0.113.7", resolver.resolve(request(xff = "198.51.100.1, 203.0.113.7")))
        assertEquals("203.0.113.7", resolver.resolve(request(xff = "203.0.113.7")))
        assertEquals("10.0.0.1", resolver.resolve(request()))
    }

    @Test
    fun `two hops skip the address the inner proxy appended`() {
        val resolver = ClientIpResolver(null, 2)
        assertEquals("203.0.113.7", resolver.resolve(request(xff = "6.6.6.6, 203.0.113.7, 172.70.0.1")))
        val split = request().apply {
            addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7")
            addHeader("X-Forwarded-For", "172.70.0.1")
        }
        assertEquals("203.0.113.7", resolver.resolve(split))
    }

    @Test
    fun `header mode uses CF-Connecting-IP and falls back to the socket address`() {
        val resolver = ClientIpResolver("CF-Connecting-IP", 1)
        assertEquals("203.0.113.9", resolver.resolve(request(xff = "6.6.6.6, 172.70.0.1", cf = "203.0.113.9")))
        assertEquals("10.0.0.1", resolver.resolve(request(xff = "6.6.6.6, 172.70.0.1")))
    }

    @Test
    fun `reads the container request under Spring's forwarded header filter`() {
        val resolver = ClientIpResolver(null, 1)
        val seen = mutableListOf<String>()
        val chain = MockFilterChain(object : HttpServlet() {
            override fun service(req: HttpServletRequest, res: HttpServletResponse) {
                seen += req.remoteAddr
                seen += resolver.resolve(req)
            }
        })
        ForwardedHeaderFilter().doFilter(request(xff = "6.6.6.6, 203.0.113.7"), MockHttpServletResponse(), chain)
        // The filter alone would report the client-chosen leftmost entry.
        assertEquals(listOf("6.6.6.6", "203.0.113.7"), seen)
    }
}
