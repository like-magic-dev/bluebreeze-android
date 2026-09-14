import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("signing")
}

val currentVersion = "1.0.1"

android {
    namespace = "dev.likemagic.bluebreeze"
    version = currentVersion
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests {
            // Production code calls android.util.Log and constructs android.os.ParcelUuid;
            // without this, either hitting Android's un-mocked stub jar in a JVM unit test
            // throws "... not mocked" instead of running.
            isReturnDefaultValues = true
            isIncludeAndroidResources = false
        }
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

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
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