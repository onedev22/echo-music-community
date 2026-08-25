import com.android.build.gradle.internal.tasks.CompileArtProfileTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.amurayada.playlistTransfer"
        compileSdk = 36
        minSdk = 26
    }

    val xcfName = "playlistTransferKit"

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(projects.ktorExt)
                implementation(projects.common)
                implementation(projects.domain)
                implementation(projects.spotify)
                implementation(projects.kotlinYtmusicScraper)
                
                // Networking and Serialization
                implementation(libs.ktor.client.encoding)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)

                implementation(libs.kotlin.reflect)
                implementation(libs.kotlin.test)

                implementation(libs.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
            }
        }

        iosMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
            }
        }
    }
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
