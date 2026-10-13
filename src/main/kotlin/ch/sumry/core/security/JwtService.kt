package ch.sumry.core.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

/** Everything the filter needs from a token once the signature has checked out. */
data class TokenClaims(
    val subject: String,
    val jti: String,
    val tokenVersion: Int,
    val role: String?,
    val expiresAt: Date,
    /** Null for a normal session; [JwtService.SCOPE_SHORTCUT] for a token limited to /api/shortcut. */
    val scope: String? = null,
)

@Service
class JwtService(private val jwtProperties: JwtProperties) {

    private val key: SecretKey

    init {
        // Fail fast at startup rather than on the first login with a missing or too-short key.
        val decoded = try {
            Decoders.BASE64.decode(jwtProperties.secret)
        } catch (ex: Exception) {
            throw IllegalStateException("jwt.secret (JWT_SECRET) must be valid Base64", ex)
        }
        require(decoded.size >= 32) {
            "jwt.secret (JWT_SECRET) must decode to at least 32 bytes for HS256; got ${decoded.size}"
        }
        key = Keys.hmacShaKeyFor(decoded)
    }

    fun generateToken(
        subject: String,
        tokenVersion: Int,
        extraClaims: Map<String, Any> = emptyMap(),
        lifetimeMs: Long = jwtProperties.expirationMs,
    ): String {
        val now = Date()
        val expiryDate = Date(now.time + lifetimeMs)
        return Jwts.builder()
            .claims(extraClaims)
            .subject(subject)
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_TOKEN_VERSION, tokenVersion)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    /**
     * Parses and verifies a token. Returns null for anything that should not be trusted:
     * bad signature, expired, malformed, or missing the claims this service always writes.
     */
    fun parse(token: String?): TokenClaims? {
        if (token.isNullOrBlank()) return null
        return try {
            val claims: Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
            TokenClaims(
                subject = claims.subject ?: return null,
                jti = claims.id ?: return null,
                tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer::class.java)?.toInt() ?: return null,
                role = claims["role"] as? String,
                expiresAt = claims.expiration ?: return null,
                scope = claims[CLAIM_SCOPE] as? String,
            )
        } catch (ex: Exception) {
            null
        }
    }

    companion object {
        const val CLAIM_TOKEN_VERSION = "tv"
        const val CLAIM_SCOPE = "scope"
        const val SCOPE_SHORTCUT = "shortcut"
        const val SHORTCUT_PATH = "/api/shortcut/"
    }
}
