package gloss.domain.chat.infrastructure

import gloss.common.util.UuidV7
import gloss.domain.chat.domain.ChatHistoryPolicy
import gloss.domain.chat.domain.ChatLine
import gloss.domain.chat.domain.ChatRole
import gloss.domain.chat.domain.ChatSession
import gloss.domain.chat.domain.ChatStatus
import gloss.domain.chat.domain.ChatUsage
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/** A conversation's messages. Append-only: what was said stays said. */
@Repository
class JdbcChatLines(
    private val jdbcClient: JdbcClient,
) {
    /** The caller holds the conversation's lock and numbers [line] after its last. */
    fun append(
        session: ChatSession,
        line: ChatLine,
    ) {
        val asked = line.asked
        val answered = line.answered
        val usage = answered?.usage
        jdbcClient
            .sql(
                """
                insert into chat_message (id, session_id, account_id, seq, role, item_key, quote, text, prompt,
                                          post_version_id, turn_id, plan_id, history_from, reply_to, status, finish_reason, error_code, model,
                                          prompt_version, cache_hit_tokens, cache_miss_tokens, completion_tokens,
                                          reasoning_tokens, reservation_id, created_at)
                values (:id, :session, :account, :seq, :role, :itemKey, :quote, :text, :prompt,
                        :postVersionId, :turnId, :planId, :historyFrom, :replyTo, :status, :finishReason, :errorCode, :model,
                        :promptVersion, :cacheHit, :cacheMiss, :completion,
                        :reasoning, :reservationId, :at)
                """.trimIndent(),
            ).param("id", UuidV7.at(line.createdAt))
            .param("session", session.id)
            .param("account", session.accountId)
            .param("seq", line.seq)
            .param("role", line.role.code)
            .param("itemKey", line.itemKey)
            .param("quote", asked?.quote)
            .param("text", line.text)
            .param("prompt", asked?.prompt)
            .param("postVersionId", asked?.postVersionId)
            .param("turnId", asked?.turnId)
            .param("planId", asked?.planId)
            .param("historyFrom", asked?.historyFrom)
            .param("replyTo", answered?.replyTo)
            .param("status", answered?.status?.code)
            .param("finishReason", answered?.finishReason)
            .param("errorCode", answered?.errorCode)
            .param("model", answered?.model)
            .param("promptVersion", answered?.promptVersion)
            .param("cacheHit", usage?.cacheHitTokens)
            .param("cacheMiss", usage?.cacheMissTokens)
            .param("completion", usage?.completionTokens)
            .param("reasoning", usage?.reasoningTokens)
            .param("reservationId", answered?.reservationId)
            .param("at", Timestamp.from(line.createdAt))
            .update()
    }

    /** The last message's number, 0 before the first. */
    fun lastSeq(
        sessionId: UUID,
        accountId: UUID,
    ): Int =
        jdbcClient
            .sql("select coalesce(max(seq), 0) from chat_message where session_id = :session and account_id = :account")
            .param("session", sessionId)
            .param("account", accountId)
            .query(Int::class.java)
            .single()

    /** The last turn the reader asked: where its history started and the plan it was under. */
    fun last(
        sessionId: UUID,
        accountId: UUID,
    ): ChatHistoryPolicy.Last? =
        jdbcClient
            .sql(
                """
                select history_from, plan_id from chat_message
                where session_id = :session and account_id = :account and role = 'reader'
                order by seq desc
                limit 1
                """.trimIndent(),
            ).param("session", sessionId)
            .param("account", accountId)
            .query {
                row,
                _,
                ->
                ChatHistoryPolicy.Last(row.getInt("history_from"), row.getObject("plan_id", UUID::class.java))
            }.optional()
            .orElse(null)

    /**
     * The turns Gloss finished from [from] on, under [planId], oldest first: only what the history
     * can hold is read, however long the conversation has grown.
     */
    fun finished(
        session: ChatSession,
        from: Int,
        planId: UUID,
    ): List<ChatHistoryPolicy.Exchange> =
        jdbcClient
            .sql(
                """
                select asked.seq, asked.prompt, asked.post_version_id, reply.text as reply
                from chat_message reply
                join chat_message asked on asked.session_id = reply.session_id and asked.seq = reply.reply_to
                where reply.session_id = :session and reply.account_id = :account and reply.status = 'done'
                  and reply.reply_to >= :from and asked.plan_id = :plan
                order by asked.seq
                """.trimIndent(),
            ).param("session", session.id)
            .param("account", session.accountId)
            .param("from", from)
            .param("plan", planId)
            .query { row, _ ->
                ChatHistoryPolicy.Exchange(
                    seq = row.getInt("seq"),
                    prompt = row.getString("prompt"),
                    reply = row.getString("reply"),
                    postVersionId = row.getObject("post_version_id", UUID::class.java),
                )
            }.list()

    fun list(
        sessionId: UUID,
        accountId: UUID,
    ): List<ChatLine> =
        jdbcClient
            .sql(
                """
                select seq, role, item_key, quote, text, prompt, post_version_id, turn_id, plan_id, history_from, reply_to, status,
                       finish_reason,
                       error_code, model, prompt_version, cache_hit_tokens, cache_miss_tokens, completion_tokens,
                       reasoning_tokens, reservation_id, created_at
                from chat_message
                where session_id = :session and account_id = :account
                order by seq
                """.trimIndent(),
            ).param("session", sessionId)
            .param("account", accountId)
            .query { row, _ -> lineOf(row) }
            .list()

    private fun lineOf(row: ResultSet): ChatLine {
        val role = ChatRole.of(row.getString("role"))
        return ChatLine(
            seq = row.getInt("seq"),
            role = role,
            itemKey = row.getString("item_key"),
            text = row.getString("text"),
            createdAt = row.getTimestamp("created_at").toInstant(),
            asked = if (role == ChatRole.READER) askedOf(row) else null,
            answered = if (role == ChatRole.GLOSS) answeredOf(row) else null,
        )
    }

    private fun askedOf(row: ResultSet) =
        ChatLine.Asked(
            turnId = row.getObject("turn_id", UUID::class.java),
            quote = row.getString("quote"),
            prompt = row.getString("prompt"),
            postVersionId = row.getObject("post_version_id", UUID::class.java),
            planId = row.getObject("plan_id", UUID::class.java),
            historyFrom = row.getInt("history_from"),
        )

    private fun answeredOf(row: ResultSet) =
        ChatLine.Answered(
            replyTo = row.getInt("reply_to"),
            status = ChatStatus.of(row.getString("status")),
            finishReason = row.getString("finish_reason"),
            errorCode = row.getString("error_code"),
            model = row.getString("model"),
            promptVersion = row.getString("prompt_version"),
            usage = usageOf(row),
            reservationId = row.getObject("reservation_id", UUID::class.java),
        )

    private fun usageOf(row: ResultSet): ChatUsage? {
        val hit = longOrNull(row, "cache_hit_tokens") ?: return null
        return ChatUsage(
            cacheHitTokens = hit,
            cacheMissTokens = row.getLong("cache_miss_tokens"),
            completionTokens = row.getLong("completion_tokens"),
            reasoningTokens = longOrNull(row, "reasoning_tokens"),
        )
    }

    private fun longOrNull(
        row: ResultSet,
        column: String,
    ): Long? = (row.getObject(column) as Number?)?.toLong()
}
