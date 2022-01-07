import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import java.util.*

plugins {
    `maven-publish`
    signing
}

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

fun loadSecrets(secretPropsFile: File) {
    if (secretPropsFile.exists()) {
        secretPropsFile.reader().use {
            Properties().apply {
                load(it)
            }
        }.onEach { (name, value) ->
            ext[name.toString()] = value
        }
    }
}

// Grabbing secrets from gradle.properties file or from environment variables, which could be used on CI
loadSecrets(project.rootProject.file("gradle.properties"))
loadSecrets(File("${project.gradle.gradleUserHomeDir}/gradle.properties"))
if (getExtraString("signing.keyId") == null) {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_KEY_PASSWORD")

    val pgpKeyContent = System.getenv("SIGNING_PRIVATE_KEY_BASE64")
    if (pgpKeyContent != null) {
        val tmpDir = File("${project.rootProject.rootDir}/tmp")
        mkdir(tmpDir)
        val keyFile = File("$tmpDir/key.pgp")
        keyFile.createNewFile()
        val os = keyFile.outputStream()
        os.write(Base64.getDecoder().decode(pgpKeyContent))
        os.close()

        ext["signing.secretKeyRingFile"] = keyFile.absolutePath
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

fun getExtraString(name: String) = ext[name]?.toString()

// If not release build add SNAPSHOT suffix
fun getVersionName() =
    if (hasProperty("release"))
        getExtraString("VERSION_NAME")
    else
        getExtraString("VERSION_NAME") + "-SNAPSHOT"

afterEvaluate {
    publishing {
        // Configure all publications
        publications.withType<MavenPublication> {
            groupId = getExtraString("GROUP")
            artifactId = getExtraString("POM_ARTIFACT_ID")
            version = getVersionName()

            // Stub javadoc.jar artifact
            artifact(javadocJar.get())

            // Provide artifacts information requited by Maven Central
            pom {
                name.set(getExtraString("POM_NAME"))
                description.set(getExtraString("POM_DESCRIPTION"))
                url.set(getExtraString("POM_URL"))

                licenses {
                    license {
                        name.set(getExtraString("POM_LICENCE_NAME"))
                        url.set(getExtraString("POM_LICENCE_URL"))
                        distribution.set(getExtraString("POM_LICENCE_DIST"))
                    }
                }

                developers {
                    developer {
                        id.set(getExtraString("POM_DEVELOPER_ID"))
                        name.set(getExtraString("POM_DEVELOPER_NAME"))
                    }
                }

                scm {
                    url.set(getExtraString("POM_SCM_URL"))
                    connection.set(getExtraString("POM_SCM_CONNECTION"))
                    developerConnection.set(getExtraString("POM_SCM_DEV_CONNECTION"))
                }

            }
        }
    }

    signing {
        sign(publishing.publications)
    }
}


tasks.getByName("publish") {
    dependsOn("build")
}

tasks.getByName("publishToMavenLocal") {
    dependsOn("build")
}

tasks.getByName("publishToSonatype") {
    dependsOn("publish")
}

