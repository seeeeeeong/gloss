package gloss.architecture

import com.lemonappdev.konsist.api.Konsist
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConventionsFixturesTest {
    private fun scope(dir: String) = Konsist.scopeFromDirectory("architecture/src/test/resources/konsist/$dir")

    @Test
    fun `a test named after no production file is caught, and only that one`() {
        assertThatThrownBy {
            Conventions.testsMirrorProduction(tests = scope("mirror/test"), production = scope("mirror/main"))
        }.hasMessageContaining("OrphanTest")
            .message()
            .doesNotContain("ChatServiceTest")
    }

    @Test
    fun `a schema test mirrors a migration, and one named after no migration is caught`() {
        assertThatThrownBy {
            Conventions.testsMirrorProduction(
                tests = scope("schema/test"),
                production = scope("mirror/main"),
                migrations = setOf("V1__posts.sql", "V2__review_log.sql"),
            )
        }.hasMessageContaining("OrphanSchemaTest")
            .message()
            .doesNotContain("PostsSchemaTest")
            .doesNotContain("ReviewLogSchemaTest")
    }

    @Test
    fun `a type in a file of its own passes`() {
        assertThatCode { Conventions.oneTypePerFile(scope("onetype/split")) }.doesNotThrowAnyException()
    }

    @Test
    fun `a type sitting above another is caught`() {
        assertThatThrownBy { Conventions.oneTypePerFile(scope("onetype/grouped")) }
            .hasMessageContaining("BudgetService")
    }

    @Test
    fun `a file named after no type it holds is caught`() {
        assertThatThrownBy { Conventions.oneTypePerFile(scope("onetype/misnamed")) }
            .hasMessageContaining("Budget")
    }
}
