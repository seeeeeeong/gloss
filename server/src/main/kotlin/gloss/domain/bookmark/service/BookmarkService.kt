package gloss.domain.bookmark.service

import gloss.common.error.GlossException
import gloss.domain.book.service.BookOutline
import gloss.domain.book.service.TocService
import gloss.domain.bookmark.domain.Bookmark
import gloss.domain.bookmark.domain.BookmarkDraft
import gloss.domain.bookmark.domain.repository.BookmarkRepository
import gloss.domain.bookmark.dto.request.BookmarkCreateRequest
import gloss.domain.bookmark.dto.response.BookmarkResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** A reader's bookmarks, each saved on an item of a book's confirmed contents. */
@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val tocService: TocService,
    private val clock: Clock,
) {
    /** A book taken off the shelf takes its bookmarks with it, and brings them back when it is added again. */
    @Transactional(readOnly = true)
    fun list(accountId: UUID): List<BookmarkResponse> {
        val bookmarks = bookmarkRepository.findAllByAccountIdOrderByCreatedAtDescIdDesc(accountId)
        val outlines = bookmarks.map { it.bookId }.distinct().associateWith { tocService.outline(accountId, it) }
        return bookmarks.mapNotNull { bookmark -> outlines[bookmark.bookId]?.let { responseOf(bookmark, it) } }
    }

    /** One already saved is returned as it is, so a save the page sent twice keeps one bookmark. */
    @Transactional
    fun save(
        accountId: UUID,
        request: BookmarkCreateRequest,
    ): BookmarkResponse {
        val outline =
            tocService.outline(accountId, request.bookId) ?: throw GlossException(BookmarkError.BOOK_NOT_READY)
        if (outline.items.none { it.itemKey == request.itemKey }) throw GlossException(BookmarkError.ITEM_NOT_FOUND)
        val quote = request.quote?.trim()?.ifEmpty { null }
        val draft = BookmarkDraft(bookId = outline.bookId, itemKey = request.itemKey, quote = quote)
        bookmarkRepository.lockBook(draft.bookId)
        val bookmark = saved(accountId, draft) ?: create(accountId, draft)
        return responseOf(bookmark, outline)
    }

    @Transactional
    fun delete(
        accountId: UUID,
        bookmarkId: UUID,
    ) {
        val bookmark =
            bookmarkRepository.findByIdAndAccountId(bookmarkId, accountId)
                ?: throw GlossException(BookmarkError.NOT_FOUND)
        bookmarkRepository.delete(bookmark)
    }

    private fun saved(
        accountId: UUID,
        draft: BookmarkDraft,
    ): Bookmark? =
        bookmarkRepository.findSame(
            accountId = accountId,
            bookId = draft.bookId,
            itemKey = draft.itemKey,
            quote = draft.quote,
        )

    private fun create(
        accountId: UUID,
        draft: BookmarkDraft,
    ): Bookmark {
        val bookmark = Bookmark.create(accountId = accountId, draft = draft, now = clock.instant())
        bookmarkRepository.save(bookmark)
        return bookmark
    }

    // A key is the path to its item, so its last part still names an item the contents in use left out.
    private fun responseOf(
        bookmark: Bookmark,
        outline: BookOutline,
    ): BookmarkResponse {
        val item = outline.items.firstOrNull { it.itemKey == bookmark.itemKey }
        return BookmarkResponse.from(
            bookmark = bookmark,
            bookTitle = outline.title,
            itemNumber = item?.number,
            itemTitle = item?.title ?: bookmark.itemKey.substringAfterLast(PATH),
        )
    }

    private companion object {
        const val PATH = " > "
    }
}
