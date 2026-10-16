package gloss.domain.setup

import gloss.domain.apikey.service.ApiKeyService
import gloss.domain.budget.service.BudgetService
import gloss.domain.llm.domain.LlmGateway
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmReply
import gloss.domain.llm.service.LlmService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import tools.jackson.databind.json.JsonMapper

/**
 * Stands in for the model where a test is about what follows a search, not about the model: every
 * message is a title search (find_books with the reader's last line), and the editions found are
 * shown (show_books); a search that found nothing or failed is answered in words. Nothing is reserved:
 * its tests run in one transaction no key or limit can be read from. SetupTurnServiceTest calls the
 * real service.
 */
class TitleSearchModel(
    apiKeyService: ApiKeyService,
    budgetService: BudgetService,
    llmGateway: LlmGateway,
) : LlmService(apiKeyService, budgetService, llmGateway) {
    private val json = JsonMapper()

    override fun stream(
        call: LlmCall,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): LlmReply {
        val last = call.messages.last()
        if (last.role == Role.USER) return calls(call, "find_books", last.content.lines().last())
        val asked =
            call.messages
                .findLast { it.role == Role.ASSISTANT }
                ?.toolCalls
                ?.lastOrNull()
        val answer = json.readTree(last.content)
        val foundSome = answer.path("books").let { it.isArray && !it.isEmpty }
        return if (asked?.name == "find_books" && foundSome) {
            calls(call, "show_books", json.readTree(asked.arguments).path("query").asString())
        } else {
            LlmReply(text = NOTHING_FOUND, finishReason = FinishReason.STOP)
        }
    }

    private fun calls(
        call: LlmCall,
        tool: String,
        query: String,
    ): LlmReply {
        val arguments = json.writeValueAsString(mapOf("query" to query))
        return LlmReply(
            text = "",
            finishReason = FinishReason.TOOL_CALLS,
            toolCalls = listOf(ToolCall(id = "$tool-${call.messages.size}", name = tool, arguments = arguments)),
        )
    }

    companion object {
        const val NOTHING_FOUND = "찾은 책이 없어요."
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun titleSearchModel(
            apiKeyService: ApiKeyService,
            budgetService: BudgetService,
            llmGateway: LlmGateway,
        ) = TitleSearchModel(apiKeyService, budgetService, llmGateway)
    }
}
