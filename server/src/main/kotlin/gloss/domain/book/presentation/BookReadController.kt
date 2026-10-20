package gloss.domain.book.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.book.dto.request.BookReadRequest
import gloss.domain.book.dto.response.BookReadResponse
import gloss.domain.book.dto.response.RecentBookResponse
import gloss.domain.book.service.BookReadService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/books")
class BookReadController(
    private val bookReadService: BookReadService,
) {
    @GetMapping("/recent")
    fun recent(
        @CurrentAccount account: SessionAccount,
    ): List<RecentBookResponse> = bookReadService.recent(account.id)

    @GetMapping("/{bookId}/read")
    fun find(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): BookReadResponse = bookReadService.find(account.id, bookId)

    @PutMapping("/{bookId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun keep(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
        @Valid @RequestBody request: BookReadRequest,
    ) {
        bookReadService.keep(account.id, bookId, request)
    }
}
