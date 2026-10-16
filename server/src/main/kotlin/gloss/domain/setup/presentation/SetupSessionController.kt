package gloss.domain.setup.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.setup.dto.request.SetupConfirmRequest
import gloss.domain.setup.dto.request.SetupContentsRequest
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.request.SetupPickRequest
import gloss.domain.setup.dto.request.SetupTypedRequest
import gloss.domain.setup.dto.response.SetupEditionResponse
import gloss.domain.setup.dto.response.SetupSessionResponse
import gloss.domain.setup.service.SetupEditionsService
import gloss.domain.setup.service.SetupSessionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/setup-sessions")
class SetupSessionController(
    private val setupSessionService: SetupSessionService,
    private val setupEditionsService: SetupEditionsService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: SetupMessageRequest,
    ): SetupSessionResponse = setupSessionService.start(account.id, request)

    // A book of the reader's own may be the first thing they say.
    @PostMapping("/typed-contents")
    @ResponseStatus(HttpStatus.CREATED)
    fun startTyped(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: SetupTypedRequest,
    ): SetupSessionResponse = setupSessionService.type(account.id, null, request)

    @GetMapping("/{sessionId}")
    fun get(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
    ): SetupSessionResponse = setupSessionService.get(account.id, sessionId)

    @GetMapping("/{sessionId}/messages/{seq}/books")
    fun books(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
        @PathVariable seq: Int,
    ): List<SetupEditionResponse> = setupEditionsService.books(account.id, sessionId, seq)

    @PostMapping("/{sessionId}/messages")
    fun say(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SetupMessageRequest,
    ): SetupSessionResponse = setupSessionService.say(account.id, sessionId, request)

    @PostMapping("/{sessionId}/pick")
    fun pick(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SetupPickRequest,
    ): SetupSessionResponse = setupSessionService.pick(account.id, sessionId, request)

    @PostMapping("/{sessionId}/typed-contents")
    fun type(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SetupTypedRequest,
    ): SetupSessionResponse = setupSessionService.type(account.id, sessionId, request)

    @PutMapping("/{sessionId}/contents")
    fun edit(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SetupContentsRequest,
    ): SetupSessionResponse = setupSessionService.edit(account.id, sessionId, request)

    @PostMapping("/{sessionId}/confirm")
    fun confirm(
        @CurrentAccount account: SessionAccount,
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: SetupConfirmRequest,
    ): SetupSessionResponse = setupSessionService.confirm(account.id, sessionId, request)
}
