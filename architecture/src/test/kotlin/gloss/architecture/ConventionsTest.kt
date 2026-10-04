package gloss.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ConventionsTest {
    @Test
    fun `a file holds one type, named after it`() {
        Conventions.oneTypePerFile(Konsist.scopeFromProduction())
    }

    @Test
    fun `tests mirror the file they test`() {
        Conventions.testsMirrorProduction(
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
    private val MIGRATION = Regex("V\\d+__([a-z0-9_]+)\\.sql")

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

    /** A schema is SQL, so `ChatSchemaTest` mirrors `V2__chat.sql` instead of a Kotlin file. */
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
