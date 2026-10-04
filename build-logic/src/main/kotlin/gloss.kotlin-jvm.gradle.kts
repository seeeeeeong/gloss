// What every module compiles and tests with, so no module can quietly opt out.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("gloss.quality")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    // 25 rather than 21: tutor replies and generation jobs hold model streams open on
    // virtual threads, and from JDK 24 (JEP 491) a virtual thread blocking inside
    // `synchronized` no longer pins its carrier. On 21, a library that synchronizes
    // around a slow socket read quietly turns each open stream into a platform thread.
    jvmToolchain(25)
    compilerOptions {
        // A warning left in place is a warning read by nobody.
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
