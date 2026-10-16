package gloss.domain.setup.service

import gloss.common.error.GlossException
import gloss.domain.book.service.BookSourceService
import gloss.domain.book.service.Edition
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.Recommended
import gloss.domain.setup.domain.SetupMessage
import gloss.domain.setup.domain.repository.SetupSessionRepository
import gloss.domain.setup.dto.response.SetupEditionResponse
import gloss.domain.setup.infrastructure.JdbcSetupMessages
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * The editions a session's search found or its model recommended, described as the source does now.
 * A session keeps only the words and the refs it was shown; the rest is asked again, from the book
 * module's short memory of recent searches or from the source, and never kept.
 */
@Service
class SetupEditionsService(
    private val setupSessionRepository: SetupSessionRepository,
    private val jdbcSetupMessages: JdbcSetupMessages,
    private val bookSourceService: BookSourceService,
) {
    /**
     * A search's editions in the order they were shown, or a recommendation's in its order; one the
     * source no longer finds for the same words is left out.
     */
    fun books(
        accountId: UUID,
        sessionId: UUID,
        seq: Int,
    ): List<SetupEditionResponse> {
        val session =
            setupSessionRepository.findByIdAndAccountId(sessionId, accountId)
                ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
        val messages = jdbcSetupMessages.list(session.id, accountId)
        return when (val content = messages.firstOrNull { it.seq == seq }?.content) {
            is BooksFound -> shown(accountId, content)
            is Recommended -> recommended(accountId, messages, content)
            else -> throw GlossException(SetupError.MESSAGE_NOT_FOUND)
        }
    }

    private fun shown(
        accountId: UUID,
        found: BooksFound,
    ): List<SetupEditionResponse> {
        val now = bookSourceService.editions(accountId, found.query).associateBy { it.ref }
        // Whether each has contents as it was offered, so what is shown can be picked as shown.
        return found.books.mapNotNull { offered ->
            now[offered.ref]?.let { editionOf(it).copy(hasContents = offered.hasContents) }
        }
    }

    /** Each book is asked for again by the latest search that found it, each search once. */
    private fun recommended(
        accountId: UUID,
        messages: List<SetupMessage>,
        recommendation: Recommended,
    ): List<SetupEditionResponse> {
        val searches = messages.map { it.content }.filterIsInstance<BooksFound>()
        val foundBy =
            recommendation.books.associate { book ->
                book.ref to searches.lastOrNull { found -> found.books.any { it.ref == book.ref } }
            }
        val described =
            foundBy.values
                .filterNotNull()
                .distinctBy { it.query }
                .associate { found -> found.query to shown(accountId, found).associateBy { it.ref } }
        return recommendation.books.mapNotNull { book ->
            foundBy[book.ref]?.let { described[it.query]?.get(book.ref) }
        }
    }
}

/**
 * The held edition as the source describes it now, found again by the latest search that showed it.
 * Null when it cannot be asked: the held contents are still shown, only without the source's details.
 */
internal fun describe(
    bookSourceService: BookSourceService,
    accountId: UUID,
    messages: List<SetupMessage>,
    ref: String,
): SetupEditionResponse? {
    val search =
        messages
            .map { it.content }
            .filterIsInstance<BooksFound>()
            .lastOrNull { found -> found.books.any { it.ref == ref } } ?: return null
    return try {
        bookSourceService.editions(accountId, search.query).firstOrNull { it.ref == ref }?.let(::editionOf)
    } catch (_: GlossException) {
        null
    }
}

private fun editionOf(edition: Edition) =
    SetupEditionResponse(
        ref = edition.ref,
        source = edition.source,
        url = edition.url,
        title = edition.title,
        authors = edition.authors,
        publisher = edition.publisher,
        publishedOn = edition.publishedOn,
        kind = edition.kind,
        cover = edition.cover,
        hasContents = edition.hasContents,
    )
