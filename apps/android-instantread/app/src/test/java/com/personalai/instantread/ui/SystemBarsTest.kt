package com.personalai.instantread.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemBarsTest {
    @Test
    fun appliesReadableWhiteSystemBarsWithoutFullscreenBlocks() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()

        try {
            SystemBars.applyFigmaCanvas(activity)

            assertEquals(Color.WHITE, activity.window.statusBarColor)
            assertTrue(
                activity.window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
            )
            assertFalse(
                activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            )
            assertFalse(
                activity.window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
