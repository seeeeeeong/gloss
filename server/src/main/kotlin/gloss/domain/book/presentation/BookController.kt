package gloss.domain.book.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.TocReviseRequest
import gloss.domain.book.dto.response.BookResponse
import gloss.domain.book.dto.response.BookSummaryResponse
import gloss.domain.book.dto.response.TocResponse
import gloss.domain.book.service.BookService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/books")
class BookController(
    private val bookService: BookService,
) {
    @GetMapping
    fun list(
        @CurrentAccount account: SessionAccount,
    ): List<BookSummaryResponse> = bookService.list(account.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: BookCreateRequest,
    ): BookResponse = bookService.create(account.id, request)

    @GetMapping("/{bookId}")
    fun get(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): BookResponse = bookService.get(account.id, bookId)

    @GetMapping("/{bookId}/toc/current")
    fun currentToc(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): TocResponse = bookService.currentToc(account.id, bookId)

    // A POST: each edit is a new version, so the same request twice makes two.
    @PostMapping("/{bookId}/toc")
    fun reviseToc(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
        @Valid @RequestBody request: TocReviseRequest,
    ): BookResponse = bookService.reviseToc(account.id, bookId, request)

    @PostMapping("/{bookId}/toc/{tocId}/confirm")
    fun confirmToc(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
        @PathVariable tocId: UUID,
    ): BookResponse = bookService.confirmToc(account.id, bookId, tocId)
}
