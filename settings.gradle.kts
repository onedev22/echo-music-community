@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        maven(url = "https://raw.githubusercontent.com/bravepipeproject/maven-repo/master/repository") {
            content {
                includeGroup("com.github.bravepipeproject")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            setUrl("https://jitpack.io")
            content {
                excludeGroup("com.github.bravepipeproject")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        maven(url = "https://raw.githubusercontent.com/bravepipeproject/maven-repo/master/repository") {
            content {
                includeGroup("com.github.bravepipeproject")
            }
        }
        google()
        mavenCentral()
        maven {
            setUrl("https://jitpack.io")
            content {
                excludeGroup("com.github.bravepipeproject")
            }
        }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "echomusic"
include(
    ":app",
    ":innertube",
    ":paxsenixlyrics",
    ":kugou",
    ":betterlyrics",
    ":lrclib",
    ":simpmusic",
    ":youlyplus",
    ":shazamkit",
    ":artistvideo",
    ":canvas",
    ":echomusiccanvas",
    ":applecanvas",
    ":unison"
)

// prepare for core submodules
val coreDir = File(rootDir, "./core")
val serviceDir = File(rootDir, "./core/service")
val mediaDir = File(rootDir, "./core/media")

include(
    ":common",
    ":data",
    ":domain",
    ":ktorExt",
    ":kotlinYtmusicScraper",
    ":spotify",
    ":playlistTransfer",
    ":lyricsService",
    ":media-jvm",
    ":media-jvm-ui",
    ":media3",
    ":media3-ui",
    ":kizzy"
)

// core modules
project(":common").projectDir = File(coreDir, "common")
project(":data").projectDir = File(coreDir, "data")
project(":domain").projectDir = File(coreDir, "domain")

// service modules
project(":ktorExt").projectDir = File(serviceDir, "ktorExt")
project(":lyricsService").projectDir = File(serviceDir, "lyricsService")
project(":kotlinYtmusicScraper").projectDir = File(serviceDir, "kotlinYtmusicScraper")
project(":spotify").projectDir = File(serviceDir, "spotify")
project(":playlistTransfer").projectDir = File(serviceDir, "playlistTransfer")
project(":kizzy").projectDir = File(serviceDir, "kizzy")

// media modules
project(":media-jvm").projectDir = File(mediaDir, "media-jvm")
project(":media-jvm-ui").projectDir = File(mediaDir, "media-jvm-ui")
project(":media3").projectDir = File(mediaDir, "media3")
project(":media3-ui").projectDir = File(mediaDir, "media3-ui")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
