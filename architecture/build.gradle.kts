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
    systemProperty("gloss.root", rootDir.absolutePath)
}
