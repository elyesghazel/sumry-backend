package ch.sumry.core.exception

import org.springframework.http.HttpStatus

class RequestException(
    val status: HttpStatus,
    override val message: String
) : RuntimeException(message)