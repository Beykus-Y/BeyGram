package com.beykus.beygram.data.storage

import com.beykus.beygram.domain.model.CachePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSettingsTest {
    @Test fun defaultsKeepTenGigabytesAndNeverExpire() {
        assertEquals(CachePolicy(maxBytes = 10L * 1024 * 1024 * 1024, maxAgeSeconds = null), StorageSettings().policy())
    }

    @Test fun retentionDaysBecomeSeconds() {
        assertEquals(CachePolicy(maxBytes = null, maxAgeSeconds = 3 * 86_400), StorageSettings(StorageSizeLimit.Unlimited, StorageRetention.ThreeDays).policy())
        assertEquals(CachePolicy(maxBytes = null, maxAgeSeconds = 7 * 86_400), StorageSettings(StorageSizeLimit.Unlimited, StorageRetention.Week).policy())
        assertEquals(CachePolicy(maxBytes = null, maxAgeSeconds = 30 * 86_400), StorageSettings(StorageSizeLimit.Unlimited, StorageRetention.Month).policy())
    }

    @Test fun unlimitedSizeAndForeverMeanNoPolicy() {
        assertEquals(CachePolicy(maxBytes = null, maxAgeSeconds = null), StorageSettings(StorageSizeLimit.Unlimited, StorageRetention.Forever).policy())
    }
}
