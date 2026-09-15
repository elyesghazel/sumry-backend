package ch.sumry.core

import ch.sumry.core.security.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
@EnableScheduling
class CoreApplication

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}