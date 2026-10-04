package demo

@RestController
class AccountController(
    private val accountRepository: AccountRepository,
) {
    @GetMapping("/accounts/{id}")
    fun account(id: UUID): Account = accountRepository.findById(id)

    @GetMapping("/accounts/count")
    fun total() = accountRepository.count()
}
