package ch.sumry.core.exception

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import kotlin.reflect.KClass

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(RequestException::class)
    fun handleRequestException(ex: RequestException): ResponseEntity<Map<String, String>> {
        val body = mapOf("error" to ex.message)
        return ResponseEntity(body, ex.status)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDatabaseConflict(ex: DataIntegrityViolationException): ResponseEntity<Map<String, String>> {
        val body = mapOf("error" to "Duplicate entry or constraint violation")
        return ResponseEntity(body, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val errors = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "Invalid value") }
        return ResponseEntity(errors, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class as KClass<out Throwable>)
    fun handleMessageNotReadable(ex: Exception): ResponseEntity<Map<String, String>> {
        val body = mapOf("error" to "Invalid JSON format")
        return ResponseEntity(body, HttpStatus.BAD_REQUEST)
    }

}
