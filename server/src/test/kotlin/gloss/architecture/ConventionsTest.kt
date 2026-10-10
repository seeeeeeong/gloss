package gloss.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoTypeAliasDeclaration
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ConventionsTest {
    @Test
    fun `a file is named after the first type it holds`() {
        Conventions.filesNamedAfterFirstType(Konsist.scopeFromProduction())
    }

    @Test
    fun `controllers return response types, not a ResponseEntity`() {
        Conventions.controllersReturnResponses(Konsist.scopeFromProduction())
    }

    @Test
    fun `injected dependencies are named after their types`() {
        Conventions.dependenciesNamedAfterTypes(Konsist.scopeFromProduction())
    }

    @Test
    fun `tests mirror the file they test`() {
        Conventions.testsMirrorProduction(
            // These tests check rules, and the fixtures are examples, not tests of a production file.
            tests =
                Konsist.scopeFromTest().slice {
                    it.packagee?.name != "gloss.architecture" && "/src/test/resources/" !in it.path
                },
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

    private val BEANS = setOf("Service", "RestController", "RestControllerAdvice", "Component", "Configuration")
    private val VALUES = setOf("String", "Int", "Long", "Boolean", "Double")
    private val TYPE_ARGUMENT = Regex("<\\s*(\\w+)")

    private val RESPONSE = Regex("Unit|\\w+Response|List<\\w+Response>")

    /** Returns a response type or nothing: never an entity, a raw value or a ResponseEntity. */
    fun controllersReturnResponses(scope: KoScope) {
        scope
            .classes()
            .filter { it.name.endsWith("Controller") }
            .flatMap { it.functions() }
            .assertTrue(additionalMessage = "Return an XxxResponse or nothing; throw a GlossException for an error.") {
                val type = it.returnType?.name
                if (type == null) it.hasBlockBody else RESPONSE.matches(type)
            }
    }

    /**
     * Constructor and @Bean method parameters: `accountService: AccountService`. A generic one
     * starts with its argument: `handlers: List<Handler>`.
     */
    fun dependenciesNamedAfterTypes(scope: KoScope) {
        scope
            .classes()
            .filter { bean -> bean.annotations.any { it.name in BEANS } }
            .flatMap { bean ->
                val injected = bean.functions().filter { function -> function.annotations.any { it.name == "Bean" } }
                bean.primaryConstructor?.parameters.orEmpty() + injected.flatMap { it.parameters }
            }.filter { param -> param.annotations.none { it.name == "Value" } && param.type.name !in VALUES }
            .assertTrue(additionalMessage = "Name a dependency after its type: accountService: AccountService.") {
                val type = it.type.name
                val argument = TYPE_ARGUMENT.find(type)?.groupValues?.get(1)
                val expected = (argument ?: type).replaceFirstChar(Char::lowercase)
                if (argument == null) it.name == expected else it.name.startsWith(expected)
            }
    }

    /**
     * Closely related types share a file, the one it is named after first: `Password.kt` opens
     * with `Password`, then `PasswordDecision`. A `dto` package groups a controller's requests
     * and responses as `XxxDto.kt`.
     */
    fun filesNamedAfterFirstType(scope: KoScope) {
        scope.files.assertTrue(
            additionalMessage = "Name the file after its first type, or XxxDto in a dto package.",
        ) { file ->
            val types =
                file
                    .declarations(includeNested = false, includeLocal = false)
                    .filter {
                        it is KoClassDeclaration ||
                            it is KoInterfaceDeclaration ||
                            it is KoObjectDeclaration ||
                            it is KoTypeAliasDeclaration
                    }.map { (it as KoNameProvider).name }
            val dto = file.name.endsWith("Dto") && file.packagee?.name?.endsWith(".dto") == true
            types.isEmpty() || types.first() == file.name || dto
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
