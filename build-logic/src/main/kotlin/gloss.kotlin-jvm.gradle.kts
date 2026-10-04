plugins {
    id("org.jetbrains.kotlin.jvm")
    id("gloss.quality")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    // Not 21: before JEP 491 (JDK 24) a virtual thread holding a model stream pins its carrier inside `synchronized`.
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
    }
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("assertj-core").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
