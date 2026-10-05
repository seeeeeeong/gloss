package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.springframework.modulith.core.ApplicationModules

internal object Modularity {
    /**
     * Packages directly under the application's that declared no module. With explicitly
     * annotated detection such a package is checked by nothing, so it must not exist.
     */
    fun undeclaredModulePackages(
        application: Class<*>,
        modules: ApplicationModules,
        importOption: ImportOption = ImportOption.DoNotIncludeTests(),
    ): Set<String> {
        val root = application.packageName
        val packages =
            ClassFileImporter()
                .withImportOption(importOption)
                .importPackages(root)
                .map {
                    it.packageName
                        .removePrefix(root)
                        .removePrefix(".")
                        .substringBefore('.')
                }.filter { it.isNotEmpty() }
                .map { "$root.$it" }
                .toSet()
        val declared = modules.map { it.basePackage.name }.toSet()
        return packages - declared
    }
}
