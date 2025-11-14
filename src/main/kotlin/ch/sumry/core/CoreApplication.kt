package ch.sumry.core

import ch.sumry.core.security.JwtProperties // <-- IMPORT IT
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties // <-- IMPORT THIS
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class) // <-- ADD THIS ANNOTATION
class CoreApplication

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}