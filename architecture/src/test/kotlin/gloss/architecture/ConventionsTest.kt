package gloss.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Conventions from docs/design/conventions.md that live in source text rather than
 * bytecode: comments, file layout, and where tests are.
 */
class ConventionsTest {
    @Test
    fun `constants say why they have their value`() {
        Conventions.constantsExplained(Konsist.scopeFromProduction())
    }

    @Test
    fun `a file holds one type, named after it`() {
        Conventions.oneTypePerFile(Konsist.scopeFromProduction())
    }

    @Test
    fun `tests mirror the file they test`() {
        Conventions.testsMirrorProduction(
            // This module's tests check rules, not a production file of their own.
            tests = Konsist.scopeFromTest() - Konsist.scopeFromModule("architecture"),
            production = Konsist.scopeFromProduction(),
            migrations = migrations(),
        )
    }

    private fun migrations(): Set<String> {
        val root = checkNotNull(System.getProperty("gloss.root")) { "architecture/build.gradle.kts sets gloss.root" }
        return File(root, "server/src/main/resources/db/migration").list().orEmpty().toSet()
    }
}

internal object Conventions {
    private val UPPER_SNAKE = Regex("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*")

    /** Flyway's naming: `V2__chat.sql` holds the schema `ChatSchemaTest` checks. */
    private val MIGRATION = Regex("V\\d+__([a-z0-9_]+)\\.sql")

    /**
     * A limit is usually derived from something outside the code: a model's
     * price, a monthly budget, a cache lifetime. `MAX_TURNS = 20` means nothing
     * without the cost it keeps under control.
     */
    fun constantsExplained(scope: KoScope) {
        scope
            .properties()
            .filter { UPPER_SNAKE.matches(it.name) }
            .assertTrue(additionalMessage = "A constant needs a KDoc saying why it has this value.") { it.hasKDoc }
    }

    /**
     * `Reservation` lives in `Reservation.kt`, not above the service that first used it:
     * a type is found by its name, not by knowing which file it sits in. Nested types
     * stay nested, and a top-level function may share the file of the type it serves.
     */
    fun oneTypePerFile(scope: KoScope) {
        scope.files.assertTrue(additionalMessage = "A file holds one top-level type, named after it.") { file ->
            val types =
                file.classes(includeNested = false, includeLocal = false).map { it.name } +
                    file.interfaces(includeNested = false).map { it.name } +
                    file.objects(includeNested = false).map { it.name } +
                    file.typeAliases.map { it.name }
            types.isEmpty() || types == listOf(file.name)
        }
    }

    /**
     * `ChatService.kt` is tested in `ChatServiceTest.kt`, in the same package.
     * A test named after nothing is a test nobody finds when the code changes.
     *
     * A schema is SQL, not Kotlin, so `ChatSchemaTest` mirrors the migration that
     * creates it, `V2__chat.sql`, instead of a Kotlin file.
     */
    fun testsMirrorProduction(
        tests: KoScope,
        production: KoScope,
        migrations: Set<String> = emptySet(),
    ) {
        val sources = production.files.map { "${it.packagee?.name}.${it.name}" }.toSet()
        val schemas = migrations.mapNotNull { MIGRATION.matchEntire(it)?.groupValues?.get(1) }.toSet()

        tests.files
            .filter { it.name.endsWith("Test") }
            .assertTrue(additionalMessage = "No production file or migration matches this test's name.") {
                if (it.name.endsWith("SchemaTest")) {
                    snakeCase(it.name.removeSuffix("SchemaTest")) in schemas
                } else {
                    val subject = it.name.removeSuffix("Test").removeSuffix("Integration")
                    "${it.packagee?.name}.$subject" in sources
                }
            }
    }

    private fun snakeCase(pascal: String) = pascal.replace(Regex("(?<=[a-z0-9])([A-Z])"), "_$1").lowercase()
}
