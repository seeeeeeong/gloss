package gloss.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.library.Architectures.layeredArchitecture

internal object Rules {
    // An allow list, not a deny list: a library nobody thought to name, or a project type
    // that holds a connection, is caught as well.
    private val PURE =
        arrayOf(
            "java.lang..",
            // java.util itself and its pure subpackages; not logging, prefs, jar or zip.
            "java.util",
            "java.util.function..",
            "java.util.stream..",
            "java.util.regex..",
            "java.time..",
            "java.text..",
            "java.math..",
            "java.nio.charset..",
            "java.security..",
            "javax.crypto..",
            "kotlin..",
            "org.jetbrains.annotations..",
        )

    private val RULE_NAME = Regex("\\w+(Policy|Decision|Parser|Prompt|Check|Cipher)")

    // Inside the packages above, but with side effects: the environment, processes, threads,
    // reflection, standard output.
    private val IMPURE =
        arrayOf("java.lang.reflect..", "kotlin.io..", "kotlin.system..", "kotlin.concurrent..")
    private val IMPURE_TYPES =
        setOf(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.Thread",
            "java.lang.ClassLoader",
        )

    // Only our own classes count: a framework type may end in Decision too.
    private val OWN = arrayOf("gloss..", "fixtures..")

    // A value class's companion holds its factory, so it counts as the value class.
    private fun isValueClass(type: JavaClass): Boolean =
        type.isAnnotatedWith(JvmInline::class.java) || type.enclosingClass.map(::isValueClass).orElse(false)

    // A nested class is Outer$AccessPolicy on the JVM; its own name decides. A value class
    // (LoginId, Password) holds a rule too.
    private fun isRule(type: JavaClass) =
        resideInAnyPackage(*OWN).test(type) &&
            (
                isValueClass(type) ||
                    type.name
                        .substringAfterLast('.')
                        .split('$')
                        .any { RULE_NAME.matches(it) }
            )

    private val ruleClasses =
        DescribedPredicate.describe<JavaClass>(
            "named as a rule (Policy, Decision, Parser, Prompt, Check, Cipher) or a value class",
        ) {
            isRule(it)
        }

    // An array counts as its element type: an enum's values() returns one.
    private val allowed =
        DescribedPredicate.describe<JavaClass>("in the JDK's pure packages, Kotlin, or another rule class") {
            val type = if (it.isArray) it.baseComponentType else it
            type.isPrimitive || isRule(type) || isPure(type)
        }

    private const val PRESENTATION = "presentation"
    private const val SERVICE = "service"
    private const val DTO = "dto"
    private const val DOMAIN = "domain"
    private const val INFRASTRUCTURE = "infrastructure"

    // The model may carry its mapping, its Spring Data repository and its module boundary, nothing
    // that talks HTTP or SQL.
    private val DOMAIN_FRAMEWORKS =
        arrayOf(
            "jakarta.persistence..",
            "org.hibernate.annotations..",
            "org.springframework.data..",
            "org.springframework.modulith",
        )

    private fun isPure(type: JavaClass) =
        resideInAnyPackage(*PURE).test(type) && !resideInAnyPackage(*IMPURE).test(type) && type.name !in IMPURE_TYPES

    private fun modules(root: String) = "$root.$DOMAIN.*"

    // Of our own code, only other models and the shared base entity and ids; not common's
    // security or error handling, which speak HTTP and sessions.
    private fun domainProjectPackages(root: String) =
        arrayOf("${modules(root)}.$DOMAIN..", "$root.common.entity..", "$root.common.util..")

    /**
     * presentation → service → domain ← infrastructure in every module under [root]'s domain
     * package, with dto between presentation and service and reading only the domain.
     */
    fun layers(root: String): ArchRule =
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(PRESENTATION)
            .definedBy("${modules(root)}.$PRESENTATION..")
            .layer(SERVICE)
            .definedBy("${modules(root)}.$SERVICE..")
            .layer(DTO)
            .definedBy("${modules(root)}.$DTO..")
            .layer(DOMAIN)
            .definedBy("${modules(root)}.$DOMAIN..")
            .layer(INFRASTRUCTURE)
            .definedBy("${modules(root)}.$INFRASTRUCTURE..")
            .whereLayer(PRESENTATION)
            .mayNotBeAccessedByAnyLayer()
            .whereLayer(SERVICE)
            .mayOnlyBeAccessedByLayers(PRESENTATION)
            .whereLayer(DTO)
            .mayOnlyBeAccessedByLayers(PRESENTATION, SERVICE)
            .whereLayer(INFRASTRUCTURE)
            .mayOnlyBeAccessedByLayers(SERVICE)
            .whereLayer(DOMAIN)
            .mayOnlyBeAccessedByLayers(PRESENTATION, SERVICE, DTO, INFRASTRUCTURE)

    fun domainFrameworks(root: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("${modules(root)}.$DOMAIN..")
            .should()
            .onlyDependOnClassesThat(
                DescribedPredicate.describe(
                    "in the pure JDK, Kotlin, JPA, Spring Data, Modulith, a model, or common entity and util",
                ) {
                    val type = if (it.isArray) it.baseComponentType else it
                    type.isPrimitive ||
                        isPure(type) ||
                        resideInAnyPackage(*DOMAIN_FRAMEWORKS).test(type) ||
                        resideInAnyPackage(*domainProjectPackages(root)).test(type)
                },
            ).because("the model holds the rules; HTTP, SQL and sessions belong to the layers around it")

    fun pure(): ArchRule =
        classes()
            .that(ruleClasses)
            .should()
            .onlyDependOnClassesThat(allowed)
            .because("rules are pure, and anything past the JDK reachable from them is how I/O leaks in")
}
