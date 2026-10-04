package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.springframework.modulith.core.ApplicationModules

internal object Modularity {
    /**
     * Packages directly under `domain/` that declared no module. With explicitly annotated
     * detection such a package is checked by nothing, so it must not exist.
     */
    fun undeclaredDomainPackages(
        application: Class<*>,
        modules: ApplicationModules,
    ): Set<String> {
        val domain = application.packageName + ".domain"
        val packages =
            ClassFileImporter()
                .importPackages(domain)
                .map { it.packageName.removePrefix("$domain.").substringBefore('.') }
                .filter { it.isNotEmpty() && it != domain }
                .map { "$domain.$it" }
                .toSet()
        val declared = modules.map { it.basePackage.name }.toSet()
        return packages - declared
    }
}
