package gloss.common.error

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import tools.jackson.databind.ObjectMapper

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
