package ch.sumry.core.security
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@Configuration
@EnableMethodSecurity // Nur Method Security aktivieren
class MethodSecurityTestConfig