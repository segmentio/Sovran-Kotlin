val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = getVersionName()

plugins {
    // Apply snyk plugin to test vulnerability
    id("io.snyk.gradle.plugin.snykplugin") version "0.4"

    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"

}

repositories {
    // Use JCenter for resolving dependencies.
    mavenCentral()
    gradlePluginPortal()
}

configure<io.snyk.gradle.plugin.SnykExtension> {
    // setSeverity("high")
    setAutoDownload(true)
    setAutoUpdate(true)
    setArguments("--all-sub-projects --fail-on=upgradable")
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

fun getVersionName() =
    if (hasProperty("release"))
        VERSION_NAME
    else
        "$VERSION_NAME-SNAPSHOT"
