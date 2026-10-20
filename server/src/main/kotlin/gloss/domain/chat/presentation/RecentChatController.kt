package gloss.domain.chat.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.chat.dto.response.ChatSessionResponse
import gloss.domain.chat.service.ChatSessionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/chat")
class RecentChatController(
    private val chatSessionService: ChatSessionService,
) {
    @GetMapping("/recent")
    fun recent(
        @CurrentAccount account: SessionAccount,
        @RequestParam bookIds: List<UUID>,
    ): List<ChatSessionResponse> = chatSessionService.latestOf(account.id, bookIds)
}
