package gloss.domain.bookmark.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.bookmark.dto.request.BookmarkCreateRequest
import gloss.domain.bookmark.dto.response.BookmarkResponse
import gloss.domain.bookmark.service.BookmarkService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/bookmarks")
class BookmarkController(
    private val bookmarkService: BookmarkService,
) {
    @GetMapping
    fun list(
        @CurrentAccount account: SessionAccount,
    ): List<BookmarkResponse> = bookmarkService.list(account.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun save(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: BookmarkCreateRequest,
    ): BookmarkResponse = bookmarkService.save(account.id, request)

    @DeleteMapping("/{bookmarkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookmarkId: UUID,
    ) {
        bookmarkService.delete(account.id, bookmarkId)
    }
}
