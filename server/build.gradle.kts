// The running backend: web endpoints, persistence, workers and external clients.
// The only module that knows Spring exists; the rules it applies live in core.
plugins {
    id("gloss.kotlin-jvm")
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
}

// Hibernate proxies entities by subclassing them, which Kotlin's final classes forbid.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.modulith.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    // Request and response bodies are Kotlin data classes.
    implementation(libs.jackson.module.kotlin)
    // Features are application modules: each hides its sub-packages from the others,
    // and ModularityTest fails a dependency no module declared.
    implementation(libs.spring.modulith.starter.core)
    // Entities and their queries. Append-only ledgers and the job queue use
    // JdbcClient instead (docs/design/decisions.md T4).
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.kotlin.jdsl.jpql.dsl)
    implementation(libs.kotlin.jdsl.jpql.render)
    implementation(libs.kotlin.jdsl.spring.data.jpa.boot4.support)
    // The schema is written by hand in db/migration and applied at startup, so the
    // database a test runs against is the database production runs against.
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.modulith.starter.test)
    // A real PostgreSQL per test run: the append-only rules are triggers, and an
    // in-memory stand-in would pass tests the real one fails.
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.bootRun {
    // application.yml imports .env from the working directory, and .env lives at the
    // repository root. Gradle would otherwise run the server in server/.
    workingDir = rootDir
}
