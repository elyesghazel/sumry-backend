package ch.sumry.core.exception

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.DateTimeException
import kotlin.reflect.KClass

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)


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
        val errorBody = mapOf("error" to "Not Found", "message" to ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND) // 404 not found
            .body(errorBody)
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDeniedException(ex: AuthorizationDeniedException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(mapOf("error" to "Forbidden", "message" to (ex.message ?: "Access Denied")))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "Bad Request", "message" to (ex.message ?: "Invalid Argument")))
    }

    @ExceptionHandler(NumberFormatException::class)
    fun handleNumberFormatException(ex: NumberFormatException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "Bad Request", "message" to (ex.message ?: "Invalid Argument")))
    }

    @ExceptionHandler(NullPointerException::class)
    fun handleNullPointerException(ex: NullPointerException): ResponseEntity<Map<String, String>> {
        // The detail goes to the log, not to the caller: a 500 body is not the place for internals.
        log.error("Unhandled null", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "Internal Server Error"))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(ex: MissingServletRequestParameterException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "error" to "Bad Request",
                    "message" to "Missing required parameter: ${ex.parameterName}"
                )
            )

    }

    @ExceptionHandler(DateTimeException::class)
    fun handleDateTimeException(ex: DateTimeException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "error" to "Bad Request",
                    "message" to (ex.message ?: "Invalid date or time value")
                )
            )

    }

}
