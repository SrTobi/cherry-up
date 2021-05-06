import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.32"
}

group = "com.github.srtobi"
version = "1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jgit", "org.eclipse.jgit", "5.11.0.202103091610-r")
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar>() {
    manifest {
        attributes(
            "Main-Class" to "cherryup.MainKt"
        )
    }

    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })


    exclude(
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "META-INF/*.DSA"
    )
}

