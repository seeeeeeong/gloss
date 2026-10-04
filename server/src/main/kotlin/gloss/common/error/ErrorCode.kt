package gloss.common.error

import org.springframework.http.HttpStatus

/** Each module keeps its own enum of these, so no module edits another's list. */
interface ErrorCode {
    val status: HttpStatus
    val code: String
    val message: String
}
