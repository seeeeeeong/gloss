// Rules about the code rather than its behaviour, checked in CI instead of left
// to review. Test-only; it depends on every module so the rules see all of them.
plugins {
    id("gloss.kotlin-jvm")
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":server"))
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.core)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.archunit)
    testImplementation(libs.konsist)
}

tasks.test {
    // A schema test mirrors a migration file, which the convention finds from here.
    systemProperty("gloss.root", rootDir.absolutePath)
}
