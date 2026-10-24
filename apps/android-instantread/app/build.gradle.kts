import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFileEnvironmentName = "INSTANTREAD_RELEASE_STORE_FILE"
val releaseStorePasswordEnvironmentName = "INSTANTREAD_RELEASE_STORE_PASSWORD"
val releaseKeyAliasEnvironmentName = "INSTANTREAD_RELEASE_KEY_ALIAS"
val releaseKeyPasswordEnvironmentName = "INSTANTREAD_RELEASE_KEY_PASSWORD"
val releaseSigningEnvironmentNames = listOf(
    releaseStoreFileEnvironmentName,
    releaseStorePasswordEnvironmentName,
    releaseKeyAliasEnvironmentName,
    releaseKeyPasswordEnvironmentName
)
val releaseSigningEnvironment = releaseSigningEnvironmentNames.associateWith { name ->
    providers.environmentVariable(name).orNull?.takeUnless { it.isBlank() }
}
val missingReleaseSigningEnvironmentNames = releaseSigningEnvironmentNames.filter { name ->
    releaseSigningEnvironment[name] == null
}
val resolvedReleaseStoreFile = releaseSigningEnvironment[releaseStoreFileEnvironmentName]
    ?.let { path -> file(path) }
val releaseSigningReady = missingReleaseSigningEnvironmentNames.isEmpty() &&
    resolvedReleaseStoreFile?.isFile == true

android {
    namespace = "com.personalai.instantread"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.personalai.instantread"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = requireNotNull(resolvedReleaseStoreFile)
                storePassword = requireNotNull(
                    releaseSigningEnvironment[releaseStorePasswordEnvironmentName]
                )
                keyAlias = requireNotNull(
                    releaseSigningEnvironment[releaseKeyAliasEnvironmentName]
                )
                keyPassword = requireNotNull(
                    releaseSigningEnvironment[releaseKeyPasswordEnvironmentName]
                )
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty(
                    "maven.repo.local",
                    file("${System.getProperty("user.home")}/.m2/repository").absolutePath
                )
            }
        }
    }
}

val releaseArtifactTaskPaths = setOf(
    "${project.path}:packageRelease",
    "${project.path}:bundleRelease"
)

gradle.taskGraph.whenReady {
    val releaseArtifactRequested = allTasks.any { task ->
        task.path in releaseArtifactTaskPaths
    }
    if (!releaseArtifactRequested) {
        return@whenReady
    }
    if (missingReleaseSigningEnvironmentNames.isNotEmpty()) {
        throw GradleException(
            "Release signing credentials are missing: " +
                missingReleaseSigningEnvironmentNames.joinToString(", ")
        )
    }
    if (!releaseSigningReady || resolvedReleaseStoreFile?.isFile != true) {
        throw GradleException(
            "Release signing credential is invalid: $releaseStoreFileEnvironmentName"
        )
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
