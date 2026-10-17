package com.beykus.beygram.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableApkSignerPolicyTest {
    @Test fun onlyAndroid10OrEarlierCanUseLegacyFallbackWhenModernSignersMissing() {
        assertTrue(StableApkSignerPolicy.allowLegacyFallback(29, false))
        assertTrue(StableApkSignerPolicy.allowLegacyFallback(28, false))
        assertFalse(StableApkSignerPolicy.allowLegacyFallback(29, true))
        assertFalse(StableApkSignerPolicy.allowLegacyFallback(30, false))
        assertFalse(StableApkSignerPolicy.allowLegacyFallback(36, false))
    }

    @Test fun missingOrUnknownSignerAlwaysRejects() {
        assertFalse(StableApkSignerPolicy.compatible(null, setOf("trusted"), null))
        assertFalse(StableApkSignerPolicy.compatible(setOf("trusted"), null, null))
        assertFalse(StableApkSignerPolicy.compatible(emptySet(), setOf("trusted"), null))
        assertFalse(StableApkSignerPolicy.compatible(setOf("trusted"), setOf("attacker"), null))
        assertFalse(StableApkSignerPolicy.compatible(setOf("trusted"), setOf("attacker"), setOf("attacker")))
    }

    @Test fun sameSignerAndMultipleSignerSetsMustMatch() {
        assertTrue(StableApkSignerPolicy.compatible(setOf("trusted"), setOf("trusted"), null))
        assertTrue(StableApkSignerPolicy.compatible(setOf("alpha", "beta"), setOf("beta", "alpha"), null))
        assertFalse(StableApkSignerPolicy.compatible(setOf("alpha", "beta"), setOf("alpha"), setOf("alpha", "beta")))
    }

    @Test fun singleSignerRotationRequiresArchiveHistory() {
        assertTrue(StableApkSignerPolicy.compatible(
            setOf("old"), setOf("new"), setOf("old", "new")
        ))
        assertFalse(StableApkSignerPolicy.compatible(
            setOf("old"), setOf("new"), null
        ))
        assertFalse(StableApkSignerPolicy.compatible(
            setOf("old"), setOf("new"), setOf("old")
        ))
    }
}
