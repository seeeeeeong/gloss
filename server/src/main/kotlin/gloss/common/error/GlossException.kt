package gloss.common.error

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import tools.jackson.databind.ObjectMapper

/** [cause] keeps what went wrong underneath for the log; the reader only sees [error]. */
open class GlossException(
    val error: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(error.code, cause)

/** Each module keeps its own enum of these, so no module edits another's list. */
interface ErrorCode {
    val status: HttpStatus
    val code: String
    val message: String
}

data class ErrorResponse(
    val code: String,
    val message: String,
)

fun ErrorCode.toResponse() = ErrorResponse(code, message)

fun ErrorCode.toResponseEntity(): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(toResponse())

/** For filters, which run before any controller advice can answer. */
fun HttpServletResponse.writeError(
    json: ObjectMapper,
    error: ErrorCode,
) {
    status = error.status.value()
    contentType = MediaType.APPLICATION_JSON_VALUE
    json.writeValue(outputStream, error.toResponse())
}
