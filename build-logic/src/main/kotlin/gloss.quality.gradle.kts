plugins {
    id("com.diffplug.spotless")
    id("dev.detekt")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Plain `detekt` skips every type-resolved rule silently, the println ban included.
tasks.named("detekt") { enabled = false }
tasks.named("check") { dependsOn("detektMain", "detektTest") }
