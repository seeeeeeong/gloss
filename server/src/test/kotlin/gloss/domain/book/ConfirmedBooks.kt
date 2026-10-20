package gloss.domain.book

import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.response.BookResponse
import gloss.domain.book.service.BookService
import gloss.domain.book.service.ContentsVersion
import gloss.domain.book.service.TocService
import gloss.domain.book.service.TypedBook
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/** Books with their contents confirmed as setup confirms them, since no endpoint confirms contents. */
class ConfirmedBooks(
    private val jdbc: JdbcClient,
    private val bookService: BookService,
    private val tocService: TocService,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun confirmedBooks(
            jdbc: JdbcClient,
            bookService: BookService,
            tocService: TocService,
        ) = ConfirmedBooks(jdbc, bookService, tocService)
    }

    /** The book as added, its first version confirmed. */
    fun add(
        accountId: UUID,
        request: BookCreateRequest,
    ): BookResponse {
        val book = bookService.create(accountId, request)
        tocService.confirmContents(accountId, book.id, book.toc.id)
        return book
    }

    fun addTyped(
        accountId: UUID,
        typed: TypedBook,
        style: WritingStyle,
    ): ContentsVersion = bookService.createConfirmed(accountId, typed, style)

    /** A new version of the contents, confirmed in place of the one in use. */
    fun revise(
        accountId: UUID,
        bookId: UUID,
        toc: String,
    ): ContentsVersion {
        val revised = bookService.reviseToc(accountId, bookId, toc)
        return tocService.confirmContents(accountId, bookId, revised.toc.id)
    }

    /** For a book added over HTTP, whose reader's id the test does not hold. */
    fun confirm(
        bookId: String,
        tocId: String,
    ): ContentsVersion {
        val book = UUID.fromString(bookId)
        val account =
            jdbc
                .sql("select account_id from book where id = :id")
                .param("id", book)
                .query(UUID::class.java)
                .single()
        return tocService.confirmContents(account, book, UUID.fromString(tocId))
    }
}
