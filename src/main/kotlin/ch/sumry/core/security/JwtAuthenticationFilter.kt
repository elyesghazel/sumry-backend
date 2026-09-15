package ch.sumry.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val revocation: TokenRevocationService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        if (SecurityContextHolder.getContext().authentication == null) {
            val claims = jwtService.parse(authHeader.substring(7))
            if (claims != null && revocation.isActive(claims)) {
                // Spring expects roles prefixed; a token without one is an ordinary user.
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role ?: "USER"}"))
                val authToken = UsernamePasswordAuthenticationToken(claims.subject, null, authorities)
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
                // Kept for the logout endpoint, which revokes exactly the token it was called with.
                request.setAttribute(CLAIMS_ATTRIBUTE, claims)
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val CLAIMS_ATTRIBUTE = "sumry.tokenClaims"
    }
}
