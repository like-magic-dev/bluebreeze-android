import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("signing")
}

val currentVersion = "0.0.12"

android {
    namespace = "dev.likemagic.bluebreeze"
    version = currentVersion
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
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
    implementation(libs.jetbrains.kotlinx.coroutines.core)
    implementation(libs.appcompat.v7)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
//    testImplementation("junit:junit")
//    androidTestImplementation("com.android.support.test:runner")
//    androidTestImplementation("com.android.support.test.espresso:espresso-core")
}

afterEvaluate {
    mavenPublishing {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()

        coordinates(
            groupId = "dev.likemagic",
            artifactId = "bluebreeze",
            version = currentVersion
        )

        pom {
            name = "BlueBreeze"
            description = "BlueBreeze Android SDK - A modern Bluetooth LE library"
            version = currentVersion

            url = "https://likemagic.dev"

            packaging = "aar"

            // Your choosen license
            // Use https://choosealicense.com/ to decide, if you need help.
            licenses {
                license {
                    name = "The MIT License"
                    url = "https://opensource.org/license/mit"
                }
            }

            scm {
                url = "https://github.com/like-magic-dev/bluebreeze-android"
                connection = "scm:git://github.com:like-magic-dev/bluebreeze-android.git"
                developerConnection = "scm:git://github.com:like-magic-dev/bluebreeze-android.git"
            }

            developers {
                developer {
                    id = "amulloni"
                    name = "Alessandro Mulloni"
                    email = "ale@likemagic.dev"
                    organizationUrl = "https://likemagic.dev/"
                }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}