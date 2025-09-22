plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

android {
    namespace = "rust.nostr.android.signer.proxy"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("androidx.appcompat:appcompat:1.7.1")
}

val version: String = "0.43.0-alpha.2"
val isSnapshot: Boolean = version.contains("SNAPSHOT")

mavenPublishing {
    configure(com.vanniktech.maven.publish.AndroidMultiVariantLibrary(
        sourcesJar = true,
        publishJavadocJar = true,
    ))

    publishToMavenCentral(automaticRelease = !isSnapshot)

    signAllPublications()

    coordinates("org.rust-nostr", "android-signer-proxy", version)

    pom {
        name.set("android-signer-proxy")
        description.set("Android Kotlin implementation that acts as a proxy/bridge for NIP-55 communication.")
        url.set("https://rust-nostr.org")
        licenses {
            license {
                name.set("MIT")
                url.set("https://rust-nostr.org/license")
            }
        }
        developers {
            developer {
                id.set("yukibtc")
                name.set("Yuki Kishimoto")
                email.set("yukikishimoto@protonmail.com")
            }
        }
        scm {
            connection.set("scm:git:github.com/rust-nostr/android-signer.git")
            developerConnection.set("scm:git:ssh://github.com/rust-nostr/android-signer.git")
            url.set("https://github.com/rust-nostr/android-signer")
        }
    }
}
