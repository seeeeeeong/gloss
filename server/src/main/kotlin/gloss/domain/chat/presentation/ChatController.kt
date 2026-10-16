package gloss.domain.chat.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.chat.dto.request.ChatAskRequest
import gloss.domain.chat.dto.request.ChatStopRequest
import gloss.domain.chat.dto.response.ChatResponse
import gloss.domain.chat.service.ChatService
import gloss.domain.chat.service.TurnService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@RestController
@RequestMapping("/api/me/books/{bookId}/chat")
class ChatController(
    private val chatService: ChatService,
    private val turnService: TurnService,
    private val objectMapper: ObjectMapper,
) {
    @GetMapping
    fun get(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): ChatResponse = chatService.conversation(account.id, bookId)

    // Server-sent events once the reader's message is kept; an error before that is a plain error response.
    @PostMapping("/turns")
    fun ask(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
        @Valid @RequestBody request: ChatAskRequest,
        response: HttpServletResponse,
    ) {
        TurnEventStream(response, objectMapper).use { events ->
            turnService.ask(accountId = account.id, bookId = bookId, request = request, stream = events.stream())
        }
    }

    @PostMapping("/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun stop(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: ChatStopRequest,
    ) {
        chatService.stop(account.id, request)
    }
}
