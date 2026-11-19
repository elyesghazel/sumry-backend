package ch.sumry.core.security

import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletRequestWrapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The one place that decides which address a request comes from. Every per-address limit
 * uses it, so a client cannot pick its own address by sending a header.
 *
 * - [header] set (e.g. `CF-Connecting-IP`): that header's value, the socket address when it
 *   is missing. Only safe when the origin accepts connections from that proxy alone.
 * - [trustedProxyHops] > 0: the X-Forwarded-For entry that many hops from the right. Each
 *   trusted proxy appends the address it saw, so entries left of that are client-supplied.
 * - Neither: the socket address.
 *
 * Headers and the socket address are read from the container's own request, under any
 * wrappers. With `server.forward-headers-strategy=framework`, Spring's ForwardedHeaderFilter
 * replaces the wrapped remote address with the leftmost X-Forwarded-For entry, which is
 * exactly the client-controlled value this class must not use.
 */
@Component
class ClientIpResolver(
    @Value("\${sumry.client-ip.header:}") header: String?,
    @Value("\${sumry.client-ip.trusted-proxy-hops:0}") trustedProxyHops: Int?,
) {
    private val header: String? = header?.trim()?.takeIf { it.isNotEmpty() }
    private val trustedProxyHops: Int = maxOf(trustedProxyHops ?: 0, 0)

    fun resolve(request: HttpServletRequest): String {
        val raw = unwrap(request)
        val socket = raw.remoteAddr
        header?.let { name ->
            return raw.getHeader(name)?.trim()?.takeIf { it.isNotEmpty() } ?: socket
        }
        if (trustedProxyHops == 0) return socket
        val entries = raw.getHeaders(FORWARDED_FOR)?.toList().orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (entries.isEmpty()) return socket
        return entries[maxOf(entries.size - trustedProxyHops, 0)]
    }

    private fun unwrap(request: HttpServletRequest): HttpServletRequest {
        var current: ServletRequest = request
        while (current is ServletRequestWrapper) {
            val inner = current.request
            if (inner !is HttpServletRequest) break
            current = inner
        }
        return current as HttpServletRequest
    }

    companion object {
        private const val FORWARDED_FOR = "X-Forwarded-For"
    }
}
