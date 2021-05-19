import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "1.4.32"
    id("com.github.gmazzo.buildconfig") version "3.0.0"
}

group = "com.github.srtobi"
version = "1.3"

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

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

fun runCmd(vararg args: String): String? {
    val stdout = ByteArrayOutputStream()
    val result = exec {
        commandLine(*args)
        standardOutput = stdout
        isIgnoreExitValue = true
    }

    return if (result.exitValue == 0)
        stdout.toString("UTF-8").trim()
    else null
}


fun gitObjHash(objName: String): String =
    runCmd("git", "rev-parse", "--short", objName) ?: throw Exception("Git object $objName does not exist")

fun tagName(): String = "v${project.version}"

fun makeVersionBanner(): String {
    val tagName = tagName()
    val tagHash = gitObjHash(tagName())
    val versionName =
        if (tagHash == gitObjHash("HEAD")) tagName
        else "<dev>"
    return "$versionName ($tagHash)"
}

buildConfig {
    buildConfigField("String", "VersionBanner") {
        "\"${makeVersionBanner()}\""
    }
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "cherryup.MainKt"
        )
    }

    doFirst {
        require(!makeVersionBanner().startsWith("<dev>")) { "Package only in correctly annotated version!" }
        require(runCmd("git", "diff-index", "--quiet", "HEAD") != null) { "Package Jar only in clean directory" }
    }

    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    archiveFileName.set("cherry-up.jar")

    exclude(
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "META-INF/*.DSA"
    )
}

