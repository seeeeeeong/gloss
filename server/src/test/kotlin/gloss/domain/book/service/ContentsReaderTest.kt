package gloss.domain.book.service

import gloss.common.error.GlossException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ContentsReaderTest {
    @Test
    fun `contents are read as a book would read them, each item under its parent's place`() {
        val items = ContentsReader.read("1장 객체\n1.1 티켓\n2장 설계")

        assertThat(items.map { Triple(it.ord, it.parent, it.title) })
            .containsExactly(Triple(0, null, "객체"), Triple(1, 0, "티켓"), Triple(2, null, "설계"))
    }

    @Test
    fun `contents are refused as a book's would be`() {
        assertThatThrownBy { ContentsReader.read("1.2.3.4.5.6.7 너무 깊은 절") }
            .isInstanceOfSatisfying(
                GlossException::class.java,
            ) { assertThat(it.error).isEqualTo(BookError.TOC_TOO_DEEP) }
        assertThatThrownBy { ContentsReader.read("   ") }
            .isInstanceOfSatisfying(
                GlossException::class.java,
            ) { assertThat(it.error).isEqualTo(BookError.INVALID_TOC) }
    }
}
