package com.beykus.beygram.data.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run on a disposable ARM64 emulator/device at API 29 and one modern API level.
 * Never run on a phone containing a real Telegram account.
 */
@RunWith(AndroidJUnit4::class)
class StableArchiveSigningAndroidTest {
    @Test fun installedApkArchiveIsAcceptedByTheSameSigningValidationAsUpdates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apk = context.applicationInfo.sourceDir
        assertTrue(
            "PackageManager could not recover a compatible APK certificate on this API level",
            StableUpdateRepository(context).isArchiveSignerCompatible(apk)
        )
    }
}
