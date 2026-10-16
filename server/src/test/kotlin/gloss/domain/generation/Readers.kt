package gloss.domain.generation

import gloss.domain.account.Members
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/** A new active account's id: only an active reader may be given a job. */
fun Members.newReader(jdbc: JdbcClient): UUID {
    val loginId = create()
    return jdbc
        .sql("select id from account where login_id = :loginId")
        .param("loginId", loginId)
        .query(UUID::class.java)
        .single()
}
