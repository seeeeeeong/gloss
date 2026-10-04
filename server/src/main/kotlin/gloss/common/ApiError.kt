package gloss.common

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper

data class ApiError(
    val code: String,
    val message: String,
)

fun HttpServletResponse.writeApiError(
    json: ObjectMapper,
    status: HttpStatus,
    error: ApiError,
) {
    this.status = status.value()
    contentType = MediaType.APPLICATION_JSON_VALUE
    json.writeValue(outputStream, error)
}
