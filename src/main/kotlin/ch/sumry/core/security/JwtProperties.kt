package ch.sumry.core.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {
    lateinit var secret: String
    var expirationMs: Long = 24 * 60 * 60 * 1000 // default 24h
}