package gloss.apikey.presentation

import gloss.apikey.application.ApiKeyService
import gloss.apikey.application.dto.ApiKeyResponse
import gloss.apikey.application.dto.ApiKeySaveRequest
import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/api-key")
class ApiKeyController(
    private val apiKeyService: ApiKeyService,
) {
    @GetMapping
    fun current(
        @CurrentAccount account: SessionAccount,
    ): ApiKeyResponse = apiKeyService.current(account.id)

    @PutMapping
    fun save(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: ApiKeySaveRequest,
    ): ApiKeyResponse = apiKeyService.save(account.id, request)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @CurrentAccount account: SessionAccount,
    ) {
        apiKeyService.revoke(account.id)
    }
}
