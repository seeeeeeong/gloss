package gloss.domain.book.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.book.dto.request.BookSourceAddRequest
import gloss.domain.book.dto.response.BookCandidateResponse
import gloss.domain.book.dto.response.BookResponse
import gloss.domain.book.service.BookSourceService
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
