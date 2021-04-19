import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.32"
}

group = "me.tobi"
version = "1.0"

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