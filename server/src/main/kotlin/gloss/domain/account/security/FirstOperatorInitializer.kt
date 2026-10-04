package gloss.domain.account.security

import gloss.domain.account.service.AccountService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class FirstOperatorInitializer(
    private val accountService: AccountService,
    @param:Value("\${gloss.first-operator.login-id:}") private val loginId: String,
    @param:Value("\${gloss.first-operator.password:}") private val password: String,
    @param:Value("\${gloss.first-operator.display-name:}") private val displayName: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (loginId.isBlank()) return
        if (accountService.ensureFirstOperator(loginId, password, displayName.ifBlank { loginId })) {
            log.info("Created the first operator, {}", loginId)
        }
    }
}
