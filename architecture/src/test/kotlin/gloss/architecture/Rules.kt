package gloss.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * The rules, apart from the classes they are checked against, so each can be
 * shown to fail on a deliberate violation before it is trusted to pass.
 */
internal object Rules {
    /** Every package that means a framework, a database or the network. */
    private val FRAMEWORKS =
        arrayOf(
            "org.springframework..",
            "jakarta..",
            "org.hibernate..",
            "java.sql..",
            "javax.sql..",
            "okhttp3..",
            "com.openai..",
            "reactor..",
            "org.jooq..",
        )

    /** Core holds the rules; I/O belongs to server, the only module that knows Spring. */
    fun frameworkFree(): ArchRule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*FRAMEWORKS)
            .because("core holds the rules, and a framework on its classpath is how I/O leaks into them")
}
