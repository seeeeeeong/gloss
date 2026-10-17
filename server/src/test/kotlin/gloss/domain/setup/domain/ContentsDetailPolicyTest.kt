package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentsDetailPolicyTest {
    @Test
    fun `a short flat list, as a book's parts alone, is too coarse to write from`() {
        assertThat(ContentsDetailPolicy.thin(List(11) { 1 })).isTrue()
        assertThat(ContentsDetailPolicy.thin(List(14) { 1 })).isTrue()
    }

    @Test
    fun `fifteen flat items, or any item under another, are written from as they are`() {
        assertThat(ContentsDetailPolicy.thin(List(15) { 1 })).isFalse()
        assertThat(ContentsDetailPolicy.thin(listOf(1, 2, 1))).isFalse()
        assertThat(ContentsDetailPolicy.thin(emptyList())).isFalse()
    }
}
