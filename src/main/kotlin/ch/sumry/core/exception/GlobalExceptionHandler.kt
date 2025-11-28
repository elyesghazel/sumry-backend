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
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
    ): ResponseEntity<Map<String, Any>> {

        // Collect all validation errors
        val errors = ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to it.defaultMessage)
        }

        val errorBody = mapOf(
            "status" to HttpStatus.BAD_REQUEST.value(),
            "error" to "Bad Request",
            "validationErrors" to errors
        )

        return ResponseEntity(errorBody, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class as KClass<out Throwable>)
    fun handleMessageNotReadable(ex: Exception): ResponseEntity<Map<String, String>> {
        val body = mapOf("error" to "Invalid JSON format")
        return ResponseEntity(body, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<Any> {
        // You can return a custom error body here, but for simplicity, we return a standard 404
        val errorBody = mapOf("error" to "Not Found", "message" to ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND) // Sets the 404 status code
            .body(errorBody)
    }

}
