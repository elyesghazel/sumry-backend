package ch.sumry.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter


class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        // 1. Check for token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response) // Continue chain
            return // Exit early
        }

        val token = authHeader.substring(7) // "Bearer ".length

        // 2. Check if user is already authenticated
        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return // Exit early
        }

        // 3. Validate token and set authentication
        try {
            if (jwtService.validateToken(token)) {
                val mail = jwtService.getMailFromToken(token)

                // Using your minimal approach (works for .authenticated() but provides no roles)
                val authToken = UsernamePasswordAuthenticationToken(
                    mail, // Principal
                    null, // Credentials
                    emptyList() // Authorities
                )

                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        } catch (e: Exception) {
            // Token was invalid (expired, malformed, etc.)
            // The SecurityContext remains null, so the user is not authenticated.
            // You can log this error if needed.
            println("Cannot set user authentication: ${e.message}")
        }

        // 4. Always continue the filter chain
        filterChain.doFilter(request, response)
    }
}