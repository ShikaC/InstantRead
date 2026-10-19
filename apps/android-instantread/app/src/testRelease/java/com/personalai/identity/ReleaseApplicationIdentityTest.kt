package com.personalai.identity

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseApplicationIdentityTest {
    @Test
    fun releaseBuildUsesPermanentInstantReadPackage() {
        assertEquals(
            "com.personalai.instantread",
            RuntimeEnvironment.getApplication().packageName
        )
    }
}
