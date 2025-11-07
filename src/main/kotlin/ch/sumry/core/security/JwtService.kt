package ch.sumry.core.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.stereotype.Service
import java.util.*

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
            .signWith(SignatureAlgorithm.HS256, jwtProperties.secret)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .setSigningKey(jwtProperties.secret)
                .parseClaimsJws(token)
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun getMailFromToken(token: String): String {
        val claims: Claims = Jwts.parser()
            .setSigningKey(jwtProperties.secret)
            .parseClaimsJws(token)
            .body
        return claims.subject
    }
}
