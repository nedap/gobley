plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `kotlin-dsl`
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.kotlin.jvm))
    compileOnly(plugin(libs.plugins.vanniktech.maven.publish))
    implementation("org.jfrog.buildinfo:build-info-extractor-gradle:${libs.versions.jfrog.artifactory.get()}")
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.tomlkt)
}

// Copied from KotlinDslExtensions.kt
fun DependencyHandler.plugin(dependency: Provider<PluginDependency>): Dependency =
    dependency.get().run { create("$pluginId:$pluginId.gradle.plugin:$version") }

gradlePlugin {
    plugins.create("gobley-gradle-build") {
        id = "gobley-gradle-build"
        implementationClass = "gobley.gradle.build.GobleyGradleBuildPlugin"
    }
}