package gloss.account.security

import gloss.account.AccountService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.DefaultApplicationArguments

class FirstOperatorInitializerTest {
    private val accounts = mock(AccountService::class.java)
    private val args = DefaultApplicationArguments()

    @Test
    fun `without a login id nothing is created`() {
        FirstOperatorInitializer(accounts, "", "", "").run(args)

        verifyNoInteractions(accounts)
    }

    @Test
    fun `a missing password is the service's to judge, since an operator may already exist`() {
        FirstOperatorInitializer(accounts, "root", "", "Operator").run(args)

        verify(accounts).ensureFirstOperator("root", "", "Operator")
    }

    @Test
    fun `the display name falls back to the login id`() {
        FirstOperatorInitializer(accounts, "root", "correct horse battery", "").run(args)

        verify(accounts).ensureFirstOperator("root", "correct horse battery", "root")
    }
}
