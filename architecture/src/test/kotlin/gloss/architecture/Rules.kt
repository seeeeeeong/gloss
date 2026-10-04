package gloss.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

internal object Rules {
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

    fun frameworkFree(): ArchRule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*FRAMEWORKS)
            .because("core holds the rules, and a framework on its classpath is how I/O leaks into them")
}
