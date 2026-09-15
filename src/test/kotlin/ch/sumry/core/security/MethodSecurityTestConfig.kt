package ch.sumry.core.security

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/** Turns on @PreAuthorize in slice tests that exclude the real SecurityConfig. */
@Configuration
@EnableMethodSecurity
class MethodSecurityTestConfig
