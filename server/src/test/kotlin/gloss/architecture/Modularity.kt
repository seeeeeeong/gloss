package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.springframework.modulith.core.ApplicationModules

internal object Modularity {
    /** Holds the feature modules; a package, not a module itself. */
    private const val CONTAINER = "domain"

    /**
     * Packages directly under the application's, or directly under its domain package, that
     * declared no module. With explicitly annotated detection such a package is checked by
     * nothing, so it must not exist.
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
                .mapNotNull { modulePackage(root, it.packageName) }
                .toSet()
        val declared = modules.map { it.basePackage.name }.toSet()
        return packages - declared
    }

    // A class right in the container counts as the container, which is never a module.
    private fun modulePackage(
        root: String,
        packageName: String,
    ): String? {
        val segments = packageName.removePrefix(root).split('.').filter { it.isNotEmpty() }
        val first = segments.firstOrNull() ?: return null
        val module = if (first == CONTAINER) segments.take(2) else listOf(first)
        return (listOf(root) + module).joinToString(".")
    }
}
