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
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.kotlin.jdsl.jpql.dsl)
    implementation(libs.kotlin.jdsl.jpql.render)
    implementation(libs.kotlin.jdsl.spring.data.jpa.boot4.support)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.bootRun {
    // .env lives at the repository root.
    workingDir = rootDir
}
