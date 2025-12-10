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
    private val jwtService: JwtService
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

        val token = authHeader.substring(7)

        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            if (jwtService.validateToken(token)) {
                // 1. Extract Subject (UUID or Email, depending on your setup)
                val userId = jwtService.getMailFromToken(token) // Assuming you use the UUID as the subject

                // 2. Extract the role claim from the JWT payload
                // Assuming jwtService has a method to get the claims map
                val claims = jwtService.extractAllClaims(token)
                val roleString = claims["role"] as String? // Assuming the key is "role"

                // 3. Format roles into Spring Security Authorities
                val authorities = if (roleString != null) {
                    // Spring Security expects roles to start with "ROLE_"
                    // e.g., "ADMIN" becomes "ROLE_ADMIN"
                    listOf(SimpleGrantedAuthority("ROLE_$roleString"))
                } else {
                    // Default to a basic role if one isn't present
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                }

                // 4. Create Authentication Token with Authorities
                val authToken = UsernamePasswordAuthenticationToken(
                    userId,      // Principal (User ID/Email)
                    null,        // Credentials (set to null after token verification)
                    authorities  // ⬅️ CRUCIAL: Pass the roles here
                )

                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        } catch (e: Exception) {
            // Log the error (optional)
            System.err.println("JWT processing error: ${e.message}")
        }

        filterChain.doFilter(request, response)
    }
}