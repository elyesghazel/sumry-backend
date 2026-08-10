package ch.sumry.core.config

import ch.sumry.core.security.JwtAuthenticationFilter
import ch.sumry.core.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.http.HttpStatus


@Configuration
@EnableMethodSecurity // ⬅️ MUST be enabled for @PreAuthorize to work
class SecurityConfig(
    private val jwtService: JwtService
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {

        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it
                    // CORS preflight must reach the browser unauthenticated.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // The only two endpoints reachable without a token.
                    .requestMatchers(HttpMethod.POST, "/api/users/register", "/api/users/login").permitAll()
                    // Liveness for Traefik/Uptime Kuma. Other actuator endpoints stay protected.
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    // API docs, only served at all when springdoc is explicitly enabled.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // Everything else requires a valid JWT.
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                // Without this an anonymous request to a protected route 500s on the missing
                // Principal instead of answering 401.
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        http.addFilterBefore(authFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun authFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(jwtService)
    }
}
