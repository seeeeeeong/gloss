package gloss.book.presentation

import gloss.book.application.BookSourceService
import gloss.book.application.dto.BookCandidateResponse
import gloss.book.application.dto.BookResponse
import gloss.book.application.dto.BookSourceAddRequest
import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/books")
class BookSourceController(
    private val bookSourceService: BookSourceService,
) {
    @GetMapping("/search")
    fun search(
        @CurrentAccount account: SessionAccount,
        @RequestParam query: String,
    ): List<BookCandidateResponse> = bookSourceService.search(account.id, query)

    @PostMapping("/from-source")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: BookSourceAddRequest,
    ): BookResponse = bookSourceService.add(account.id, request)
}
