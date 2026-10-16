package gloss.domain.post.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.post.dto.response.PostResponse
import gloss.domain.post.dto.response.PostSummaryResponse
import gloss.domain.post.service.PostService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/books/{bookId}")
class PostController(
    private val postService: PostService,
) {
    @GetMapping("/posts")
    fun list(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): List<PostSummaryResponse> = postService.list(account.id, bookId)

    // The key is a parameter, not a path segment: it is made from a title, so it can hold a slash.
    @GetMapping("/post")
    fun get(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
        @RequestParam itemKey: String,
    ): PostResponse = postService.get(account.id, bookId, itemKey)
}
