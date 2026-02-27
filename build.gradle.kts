buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains:markdown:0.7.3")
    }
}

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "app.meot"
version = "1.0.0"

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
        name = "BOJ Helper"

        val githubRawBase = "https://raw.githubusercontent.com/putdata/BOJ-JetBrains-Plugin/main"
        description = providers.fileContents(
            layout.projectDirectory.file("README.md")
        ).asText.map { md ->
            val flavour = org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor()
            val tree = org.intellij.markdown.parser.MarkdownParser(flavour)
                .buildMarkdownTreeFromString(md)
            org.intellij.markdown.html.HtmlGenerator(md, tree, flavour)
                .generateHtml()
                .replace(Regex("</?body>"), "")
                .replace(
                    Regex("""src="(docs/screenshots/[^"]+)""""),
                    """src="$githubRawBase/$1""""
                )
        }

        ideaVersion {
            sinceBuild = "213"
        }
    }
    buildSearchableOptions = false
}
