package gloss.post.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.post.application.PostGenerationService
import gloss.post.application.dto.PostGenerationResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/books/{bookId}/post/generation")
class PostGenerationController(
    private val postGenerationService: PostGenerationService,
) {
    // A POST even to look: asking is what moves the work on, from the plan to the post.
    @PostMapping
    fun request(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
        @RequestParam itemKey: String,
        @RequestParam(defaultValue = "false") retry: Boolean,
    ): PostGenerationResponse =
        postGenerationService.request(accountId = account.id, bookId = bookId, itemKey = itemKey, retry = retry)
}
