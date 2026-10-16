plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

kotlin {
    // Not 21: before JEP 491 (JDK 24) a virtual thread holding a model stream pins its carrier inside `synchronized`.
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
    }
}

// Hibernate proxies entities by subclassing them, which Kotlin's final classes forbid.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.modulith.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.session.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.kotlin.jdsl.jpql.dsl)
    implementation(libs.kotlin.jdsl.jpql.render)
    implementation(libs.kotlin.jdsl.spring.data.jpa.boot4.support)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.events.core)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit)
    testImplementation(libs.konsist)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Plain `detekt` skips every type-resolved rule silently, the println ban included.
tasks.named("detekt") { enabled = false }
tasks.named("check") { dependsOn("detektMain", "detektTest") }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // A schema test mirrors a migration file, which ConventionsTest finds from here.
    systemProperty("gloss.root", rootDir.absolutePath)
}

tasks.bootRun {
    // .env lives at the repository root.
    workingDir = rootDir
}
