package gloss.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID
import org.springframework.web.ErrorResponse as SpringErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(GlossException::class)
    fun handle(exception: GlossException): ResponseEntity<ErrorResponse> = exception.error.toResponseEntity()

    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun handleInvalidInput(): ResponseEntity<ErrorResponse> = CommonError.INVALID_INPUT.toResponseEntity()

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        // Spring's own 4xx (no route, wrong method, wrong media type) keep their status instead of becoming a 500.
        val status = (exception as? SpringErrorResponse)?.statusCode?.value()
        return when {
            status == HttpStatus.NOT_FOUND.value() -> {
                CommonError.NOT_FOUND.toResponseEntity()
            }

            status == HttpStatus.METHOD_NOT_ALLOWED.value() -> {
                CommonError.METHOD_NOT_ALLOWED.toResponseEntity()
            }

            status == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> {
                CommonError.UNSUPPORTED_MEDIA_TYPE.toResponseEntity()
            }

            status != null && status < HttpStatus.INTERNAL_SERVER_ERROR.value() -> {
                ResponseEntity.status(status).body(CommonError.INVALID_INPUT.toResponse())
            }

            else -> {
                internal(exception)
            }
        }
    }

    private fun internal(exception: Exception): ResponseEntity<ErrorResponse> {
        val incident = UUID.randomUUID().toString().take(INCIDENT_LENGTH)
        // Types and frames only: a message can carry a password or a decrypted key.
        log.error("Unhandled exception [{}]{}", incident, trace(exception))
        val error = CommonError.INTERNAL
        return ResponseEntity
            .status(error.status)
            .body(ErrorResponse(error.code, "${error.message} (오류 번호 $incident)"))
    }

    private fun trace(exception: Throwable): String =
        generateSequence(exception) { it.cause }.joinToString("") { cause ->
            "\n${cause.javaClass.name}" + cause.stackTrace.joinToString("") { "\n\tat $it" }
        }

    private companion object {
        // Eight hex digits are enough to find one failure in a day's log.
        const val INCIDENT_LENGTH = 8
    }
}
