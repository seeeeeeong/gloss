package gloss.post.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PostContentTest {
    @Test
    fun `content is kept as written`() {
        assertThat(PostContent.of("# 객체\n\n본문이다.\n")?.value).isEqualTo("# 객체\n\n본문이다.\n")
    }

    @Test
    fun `blank content is no post`() {
        assertThat(PostContent.of("")).isNull()
        assertThat(PostContent.of(" \n\t")).isNull()
    }

    @Test
    fun `content is counted by code point, up to the column's limit`() {
        assertThat(PostContent.of("😀".repeat(PostContent.MAX_LENGTH))).isNotNull()
        assertThat(PostContent.of("가".repeat(PostContent.MAX_LENGTH + 1))).isNull()
    }

    @Test
    fun `content with a NUL is refused, as the database would`() {
        assertThat(PostContent.of("본문\u0000")).isNull()
    }

    @Test
    fun `the hash is of the content's UTF-8 bytes`() {
        assertThat(PostContent.of("abc")?.sha256())
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
