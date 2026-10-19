package com.personalai.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityIdentityTest {
    @Test
    fun launcherResolvesToInstantReadMainActivity() {
        val application = RuntimeEnvironment.getApplication()
        val launchIntent = application.packageManager
            .getLaunchIntentForPackage(application.packageName)
        assertNotNull(launchIntent)
        val className = requireNotNull(launchIntent?.component).className

        assertEquals("com.personalai.instantread.MainActivity", className)
        assertFalse(className.contains("glimpse", ignoreCase = true))
        assertEquals(
            className,
            Class.forName(className, false, javaClass.classLoader).name
        )
    }
}
