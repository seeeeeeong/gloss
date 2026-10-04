package gloss.account.security

import gloss.account.AccountService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.DefaultApplicationArguments

class FirstOperatorInitializerTest {
    private val accountService = mock(AccountService::class.java)
    private val args = DefaultApplicationArguments()

    @Test
    fun `without a login id nothing is created`() {
        FirstOperatorInitializer(
            accountService = accountService,
            loginId = "",
            password = "",
            displayName = "",
        ).run(args)

        verifyNoInteractions(accountService)
    }

    @Test
    fun `a missing password is the service's to judge, since an operator may already exist`() {
        FirstOperatorInitializer(
            accountService = accountService,
            loginId = "root",
            password = "",
            displayName = "Operator",
        ).run(args)

        verify(accountService).ensureFirstOperator("root", "", "Operator")
    }

    @Test
    fun `the display name falls back to the login id`() {
        FirstOperatorInitializer(
            accountService = accountService,
            loginId = "root",
            password = "correct horse battery",
            displayName = "",
        ).run(args)

        verify(accountService).ensureFirstOperator("root", "correct horse battery", "root")
    }
}
