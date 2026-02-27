plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.boj"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation(kotlin("test"))
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2024.3")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

intellijPlatform {
    pluginConfiguration {
        name = "BOJ IntelliJ"
        description = "BOJ problem fetch and sample-run helper plugin scaffold."
        ideaVersion {
            sinceBuild = "213"
        }
    }
}
