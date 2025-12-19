package ch.sumry.core.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtProperties: JwtProperties) {

    fun generateToken(subject: String, extraClaims: Map<String, Any> = emptyMap()): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.expirationMs)


        return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key())
            .compact()
    }

    private fun key(): SecretKey {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))
    }

    fun validateToken(token: String?): Boolean {
        if (token == null) return false
        return try {
            // FIX: Use the new parserBuilder() API
            val claims: Claims = Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .body

            !claims.expiration.before(Date())
        } catch (ex: Exception) {
            // Will catch expired, malformed, etc.
            false
        }
    }

    fun getMailFromToken(token: String): String {
        // FIX: Use the new parserBuilder() API
        // This will throw an exception if the token is invalid,
        // which is expected (it should be called after validateToken)
        val claims: Claims = Jwts.parserBuilder()
            .setSigningKey(key())
            .build()
            .parseClaimsJws(token)
            .body
        return claims.subject
    }

    fun extractAllClaims(token: String): Map<String, Any> {
        val claims = Jwts.parserBuilder()
            .setSigningKey(key()) // Use the key that signed the token
            .build()
            .parseClaimsJws(token) // Parses, validates the signature, and checks expiration
            .body

        // Convert the Claims object (a map-like structure) to a standard Kotlin Map
        return claims.toMap()
    }
}