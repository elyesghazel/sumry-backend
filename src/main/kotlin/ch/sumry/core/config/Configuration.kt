package ch.sumry.core.config

import ch.sumry.core.security.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
annotation class Configuration