package gloss.domain.account.infrastructure

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** Passwords common in breaches, from the NCSC top 100,000 (see passwords/LICENSE-SecLists). */
@Component
class CommonPasswords {
    private val passwords: Set<String> =
        ClassPathResource("passwords/common.txt").inputStream.bufferedReader(Charsets.UTF_8).useLines {
            it.filter(String::isNotEmpty).toSet()
        }

    fun contains(password: String): Boolean = password.lowercase() in passwords
}
