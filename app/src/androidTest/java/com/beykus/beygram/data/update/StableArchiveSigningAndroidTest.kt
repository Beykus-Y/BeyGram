package com.beykus.beygram.data.update

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test fun android10LegacyArchiveCertificatesMatchInstalledPackage() {
        if (Build.VERSION.SDK_INT != 29) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val fromApk = pm.getPackageArchiveInfo(
            context.applicationInfo.sourceDir, PackageManager.GET_SIGNATURES
        )?.signatures?.map { it.toCharsString() }?.toSet()
        @Suppress("DEPRECATION")
        val fromInstalled = pm.getPackageInfo(
            context.packageName, PackageManager.GET_SIGNATURES
        ).signatures?.map { it.toCharsString() }?.toSet()
        assertTrue("GET_SIGNATURES did not expose archive signers on API 29", !fromApk.isNullOrEmpty())
        assertEquals(fromInstalled, fromApk)
    }
}
