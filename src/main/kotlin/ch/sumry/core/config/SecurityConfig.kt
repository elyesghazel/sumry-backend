package ch.sumry.core.config

import ch.sumry.core.security.JwtAuthenticationFilter
import ch.sumry.core.security.JwtService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter


@Configuration
@EnableMethodSecurity // ⬅️ MUST be enabled for @PreAuthorize to work
class SecurityConfig( private val jwtService: JwtService
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {


        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it
                    .anyRequest().permitAll()

            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) } // Stateless session

        // Add the filter
        http.addFilterBefore(authFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun authFilter(): JwtAuthenticationFilter {
        return JwtAuthenticationFilter(jwtService)
    }
}