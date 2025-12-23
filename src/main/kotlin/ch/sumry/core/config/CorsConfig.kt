package ch.sumry.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**") // Erlaube alle Pfade
            .allowedOrigins("http://localhost:5173") // Erlaube dein Frontend
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Erlaube diese Methoden
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}