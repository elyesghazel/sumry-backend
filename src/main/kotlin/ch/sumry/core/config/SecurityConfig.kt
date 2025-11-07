package ch.sumry.core.config

import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory.disable
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // disable CSRF for API testing
            .authorizeHttpRequests {
                it
                    .anyRequest().permitAll()
            }
            .httpBasic { disable() } // optional: disable if you only want JSON API


        return http.build()
    }
}