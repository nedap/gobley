/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.gradle.build

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention

abstract class GobleyGradleBuildExtension(private val project: Project) {
    val uniffiBindgenManifest = CargoManifest.fromFile(
        project.rootProject.layout.projectDirectory.file(
            "../crates/gobley-uniffi-bindgen/Cargo.toml"
        ).asFile
    )

    val wasmTransformerManifest = CargoManifest.fromFile(
        project.rootProject.layout.projectDirectory.file(
            "../crates/gobley-wasm-transformer/Cargo.toml"
        ).asFile
    )

    fun configureGobleyGradleProject(
        description: String,
        gradlePlugin: Boolean = false,
        signing: Boolean = needsSigning(),
    ) {
        project.configureGobleyGradleProject(description, gradlePlugin, signing)
    }

    private fun needsSigning(): Boolean {
        return project.findProperty("signingInMemoryKey") != null
    }
}

private fun Project.configureGobleyGradleProject(
    description: String,
    gradlePlugin: Boolean,
    signing: Boolean,
) {
    configureProjectProperties(description)
    configureKotlinProperties()
    tasks.withType<Test> {
        useJUnitPlatform()
        reports {
            junitXml.required.set(true)
        }
    }
    configureMavenCentralPublishing(gradlePlugin, signing)
    configureJfrogPublishing()
}

private fun Project.configureProjectProperties(
    description: String
) {
    val bindgenManifest = CargoManifest.fromFile(
        rootProject.layout.projectDirectory.file(
            "../crates/gobley-uniffi-bindgen/Cargo.toml"
        ).asFile
    )
    group = "com.nedap.healthcare.gobley"
    version = when {
        bindgenManifest.version.contains('-') -> bindgenManifest.version.substringBefore('-') + "-SNAPSHOT"
        else -> bindgenManifest.version
    }
    this.description = description
}

private fun Project.configureKotlinProperties() {
    extensions.configure(KotlinJvmProjectExtension::class.java) {
        jvmToolchain(17)
    }
}

private fun Project.configureMavenCentralPublishing(gradlePlugin: Boolean, signing: Boolean) {
    extensions.configure(MavenPublishBaseExtension::class.java) {
        configure(
            if (gradlePlugin) {
                GradlePlugin(
                    javadocJar = JavadocJar.Javadoc(),
                    sourcesJar = true,
                )
            } else {
                KotlinJvm(
                    javadocJar = JavadocJar.Empty(),
                    sourcesJar = true,
                )
            }
        )
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        if (signing) {
            signAllPublications()
        }
        coordinates(group.toString(), name, version.toString())
        pom {
            name.set(project.name)
            description.set(project.description)
            inceptionYear.set("2023")
            url.set(propertyOrEnv("gobley.projects.gradle.pom.url"))
            licenses {
                license {
                    name.set(propertyOrEnv("gobley.projects.gradle.pom.license.name"))
                    url.set(propertyOrEnv("gobley.projects.gradle.pom.license.url"))
                }
            }
            developers {
                for (developerIdx in generateSequence(0) { it + 1 }) {
                    val propertyNamePrefix = "gobley.projects.gradle.pom.developer$developerIdx"
                    val developerId = propertyOrEnv("$propertyNamePrefix.id") ?: break
                    val developerName = propertyOrEnv("$propertyNamePrefix.name") ?: break
                    developer {
                        id.set(developerId)
                        name.set(developerName)
                    }
                }
            }
            scm {
                url.set(propertyOrEnv("gobley.projects.gradle.pom.scm.url"))
                connection.set(propertyOrEnv("gobley.projects.gradle.pom.scm.connection"))
                developerConnection.set(propertyOrEnv("gobley.projects.gradle.pom.scm.developerConnection"))
            }
        }
    }
}

private fun Project.configureJfrogPublishing() {
    plugins.apply("com.jfrog.artifactory")
    
    afterEvaluate {
        val artifactoryCredentials = getArtifactoryCredentials()
        val isSnapshot = version.toString().contains("-SNAPSHOT") || version.toString().contains("-")
        val repositoryPath = if (isSnapshot) "libs-snapshots-local" else "libs-releases-local"
        
        extensions.configure<ArtifactoryPluginConvention> {
            setContextUrl("https://nedap.jfrog.io/nedap")
            publish {
                repository {
                    repoKey = repositoryPath
                    username = artifactoryCredentials.first
                    password = artifactoryCredentials.second
                }
                defaults {
                    publications("ALL_PUBLICATIONS")
                }
            }
        }
    }
}

private fun Project.getArtifactoryCredentials(): Pair<String?, String?> {
    return findProperty("artifactoryUser") as String? to 
           findProperty("artifactoryPassword") as String?
}

private fun Project.propertyOrEnv(propertyName: String, envName: String = propertyName): String? {
    return findProperty(propertyName)?.toString()
        ?: rootProject.findProperty(propertyName)?.toString()
        ?: System.getenv(envName)
}
