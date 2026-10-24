package com.personalai.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseBuildConfigurationTest {
    @Test
    fun repositoryIgnoresLocalSigningMaterials() {
        val ignoredEntries = repositoryFile(".gitignore")
            .readLines()
            .map(String::trim)
            .toSet()

        listOf(
            "*.jks",
            "*.keystore",
            "keystore.properties",
            "signing.properties"
        ).forEach { requiredEntry ->
            assertTrue(
                "Repository should ignore local signing material: $requiredEntry",
                requiredEntry in ignoredEntries
            )
        }
    }

    @Test
    fun stableReleaseUsesPromotedVersion() {
        val build = File("build.gradle.kts").readText()

        assertTrue(
            "The stable release should use a versionCode greater than the rc1 candidate.",
            Regex("""(?m)^\s*versionCode\s*=\s*2\s*$""").containsMatchIn(build)
        )
        assertTrue(
            "The stable release should expose the promoted semantic version.",
            build.contains("versionName = \"0.1.0\"")
        )
        assertFalse(
            "The stable release must not keep the rc1 suffix.",
            build.contains("versionName = \"0.1.0-rc1\"")
        )
        assertTrue(
            "Debug builds should keep a visible debug suffix.",
            build.contains("versionNameSuffix = \"-debug\"")
        )
    }

    @Test
    fun releaseArtifactsRequireEnvironmentSigningContract() {
        val build = File("build.gradle.kts").readText()
        val environmentNames = listOf(
            "INSTANTREAD_RELEASE_STORE_FILE",
            "INSTANTREAD_RELEASE_STORE_PASSWORD",
            "INSTANTREAD_RELEASE_KEY_ALIAS",
            "INSTANTREAD_RELEASE_KEY_PASSWORD"
        )

        environmentNames.forEach { name ->
            assertTrue("Release signing contract should reference $name.", build.contains(name))
        }
        listOf(
            "signingConfigs {",
            "create(\"release\")",
            "gradle.taskGraph.whenReady",
            "packageRelease",
            "bundleRelease",
            "Release signing credentials are missing:",
            "Release signing credential is invalid:"
        ).forEach { requiredText ->
            assertTrue(
                "Release signing contract should contain $requiredText.",
                build.contains(requiredText)
            )
        }
        assertTrue(
            "Release artifact tasks must fail when signing was not ready during configuration.",
            Regex(
                """if\s*\(\s*!releaseSigningReady\s*\|\|\s*""" +
                    """resolvedReleaseStoreFile\?\.isFile\s*!=\s*true\s*\)"""
            ).containsMatchIn(build)
        )
        assertTrue(
            "Release build type must bind signing only when release signing is ready.",
            Regex(
                """getByName\("release"\)\s*\{\s*""" +
                    """if\s*\(\s*releaseSigningReady\s*\)\s*\{\s*""" +
                    """signingConfig\s*=\s*signingConfigs\.getByName\("release"\)\s*""" +
                    """\}\s*\}"""
            ).containsMatchIn(build)
        )
        assertFalse(
            "Store passwords must never be hard-coded in the build script.",
            Regex("storePassword\\s*=\\s*\"[^\"]+\"").containsMatchIn(build)
        )
        assertFalse(
            "Key passwords must never be hard-coded in the build script.",
            Regex("keyPassword\\s*=\\s*\"[^\"]+\"").containsMatchIn(build)
        )
    }

    @Test
    fun gradleProjectUsesInstantReadIdentity() {
        val settings = File("../settings.gradle.kts").readText()

        assertTrue(
            "The active Gradle project should not retain the Glimpse product identity.",
            settings.contains("rootProject.name = \"InstantRead\"")
        )
    }

    @Test
    fun uiVersionFootersUseBuildMetadataInsteadOfHardcodedConstants() {
        val home = repositoryFile(
            "apps/android-instantread/app/src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt"
        ).readText()
        val settings = repositoryFile(
            "apps/android-instantread/app/src/main/java/com/personalai/instantread/ui/SettingsViewFactory.kt"
        ).readText()

        assertTrue("Home footer should use the Gradle version name.", home.contains("BuildConfig.VERSION_NAME"))
        assertTrue("Settings footer should use the Gradle version name.", settings.contains("BuildConfig.VERSION_NAME"))
        assertTrue(
            "BuildConfig generation is required for the unified version source.",
            File("build.gradle.kts").readText().contains("buildConfig = true")
        )
        listOf(home, settings).forEach { source ->
            assertFalse(
                "UI footers must not keep a hardcoded version literal.",
                Regex("""版本 \d""").containsMatchIn(source)
            )
        }
    }

    private fun repositoryFile(path: String): File = File("../../..", path).canonicalFile
}
