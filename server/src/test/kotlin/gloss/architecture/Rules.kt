package gloss.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

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

    // A nested class is Outer$AccessPolicy on the JVM; its own name decides.
    private fun isRule(type: JavaClass) =
        type.name
            .substringAfterLast('.')
            .split('$')
            .any { RULE_NAME.matches(it) } &&
            resideInAnyPackage(*OWN).test(type)

    private val ruleClasses =
        DescribedPredicate.describe<JavaClass>("named as a rule (Policy, Decision, Parser, Prompt, Check, Cipher)") {
            isRule(it)
        }

    // An array counts as its element type: an enum's values() returns one.
    private val allowed =
        DescribedPredicate.describe<JavaClass>("in the JDK's pure packages, Kotlin, or another rule class") {
            val type = if (it.isArray) it.baseComponentType else it
            val pure =
                resideInAnyPackage(*PURE).test(type) &&
                    !resideInAnyPackage(*IMPURE).test(type) &&
                    type.name !in IMPURE_TYPES
            type.isPrimitive || isRule(type) || pure
        }

    fun pure(): ArchRule =
        classes()
            .that(ruleClasses)
            .should()
            .onlyDependOnClassesThat(allowed)
            .because("rules are pure, and anything past the JDK reachable from them is how I/O leaks in")
}
