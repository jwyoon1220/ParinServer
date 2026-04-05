plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "8.3.6"
    application
}

group = "io.github.jwyoon1220"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val minestomVersion = "2026.03.25-1.21.11"
val coroutinesVersion = "1.9.0"

dependencies {
    // Minestom — full, self-contained server library (bundles fastutil 8.5.18 + slf4j 2.0.17)
    implementation("net.minestom:minestom:$minestomVersion")

    // Kotlin Coroutines for async chunk / entity / fluid pipeline
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // FastUtil — high-performance primitive collections (also bundled in Minestom,
    // declared explicitly here so it is on the compile classpath)
    implementation("it.unimi.dsi:fastutil:8.5.18")

    // Logging — logback-classic bridges to the SLF4J API already in Minestom
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // YAML configuration
    implementation("org.yaml:snakeyaml:2.3")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("io.github.jwyoon1220.MainKt")
}

tasks.shadowJar {
    archiveClassifier.set("")           // produces ParinServer-<version>.jar
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "io.github.jwyoon1220.MainKt"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}