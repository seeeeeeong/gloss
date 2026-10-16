package gloss.domain.book.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Isbn13Test {
    @Test
    fun `an isbn as printed, with hyphens or spaces, is kept as its digits`() {
        assertThat(Isbn13.of("978-89-6626-247-2")?.value).isEqualTo("9788966262472")
        assertThat(Isbn13.of(" 979 11 5839 146 1 ")?.value).isEqualTo("9791158391461")
    }

    @Test
    fun `a wrong check digit is no isbn`() {
        assertThat(Isbn13.of("9788966262473")).isNull()
    }

    @Test
    fun `an isbn-10, another prefix or a stray character is no isbn-13`() {
        assertThat(Isbn13.of("8966262473")).isNull()
        assertThat(Isbn13.of("9770306406157")).isNull()
        assertThat(Isbn13.of("978030640615X")).isNull()
    }

    @Test
    fun `an isbn copied with no-break or full-width spaces, or with an en dash, is kept as its digits`() {
        assertThat(Isbn13.of("978\u00a089\u00a06626\u00a0247\u00a02")?.value).isEqualTo("9788966262472")
        assertThat(Isbn13.of("978\u300089\u30006626\u3000247\u30002")?.value).isEqualTo("9788966262472")
        assertThat(Isbn13.of("978–89–6626–247–2")?.value).isEqualTo("9788966262472")
    }
}
