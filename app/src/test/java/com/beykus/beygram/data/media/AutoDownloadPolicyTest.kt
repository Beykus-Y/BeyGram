package com.beykus.beygram.data.media

import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPolicyTest {
    private val settings = AutoDownloadSettings.defaults()

    private fun decide(
        kind: MediaKind,
        size: Long,
        network: AutoDownloadNetwork,
        policy: AutoDownloadSettings? = null,
        secret: Boolean = false,
        secretChat: Boolean = false,
        spoiler: Boolean = false
    ) = decideAutoDownload(
        policy ?: settings, network,
        AutoDownloadRequest(1, kind, size, isSecret = secret, isSecretChat = secretChat, hasSpoiler = spoiler)
    )

    private fun reason(decision: AutoDownloadDecision): AutoDownloadDenial =
        (decision as AutoDownloadDecision.Denied).reason

    @Test fun wifiPhotoWithinLimitIsAllowed() {
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Photo, 2 * AutoDownloadSettings.MIB, AutoDownloadNetwork.Wifi))
    }

    @Test fun wifiVideoRespectsItsOwnLimit() {
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Video, 40 * AutoDownloadSettings.MIB, AutoDownloadNetwork.Wifi))
        assertEquals(
            AutoDownloadDenial.TooLarge,
            reason(decide(MediaKind.Video, 60 * AutoDownloadSettings.MIB, AutoDownloadNetwork.Wifi))
        )
    }

    @Test fun mobileVideoIsDisabledByDefault() {
        assertEquals(
            AutoDownloadDenial.Disabled,
            reason(decide(MediaKind.Video, 1 * AutoDownloadSettings.MIB, AutoDownloadNetwork.Mobile))
        )
    }

    @Test fun roamingDownloadsNothingByDefault() {
        assertEquals(
            AutoDownloadDenial.Disabled,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.Roaming))
        )
    }

    @Test fun mobilePhotoAboveLimitIsRejected() {
        assertEquals(
            AutoDownloadDenial.TooLarge,
            reason(decide(MediaKind.Photo, 2 * AutoDownloadSettings.MIB, AutoDownloadNetwork.Mobile))
        )
    }

    @Test fun dataSaverDisablesMeteredDownloadsButKeepsWifi() {
        val saver = settings.copy(dataSaver = true)
        assertEquals(
            AutoDownloadDenial.DataSaver,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.Mobile, saver))
        )
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.Wifi, saver))
        assertEquals(
            AutoDownloadDenial.DataSaver,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.WifiMetered, saver))
        )
    }

    @Test fun secretAndSpoilerContentNeverAutoDownloads() {
        assertEquals(
            AutoDownloadDenial.SecretContent,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.Wifi, secret = true))
        )
        assertEquals(
            AutoDownloadDenial.SecretChat,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.Wifi, secretChat = true))
        )
        assertEquals(
            AutoDownloadDenial.Spoiler,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.Wifi, spoiler = true))
        )
    }

    @Test fun unknownSizeIsNeverAssumedSmall() {
        assertEquals(
            AutoDownloadDenial.SizeUnknown,
            reason(decide(MediaKind.Photo, 0, AutoDownloadNetwork.Wifi))
        )
    }

    @Test fun offlineNeverDownloads() {
        assertEquals(
            AutoDownloadDenial.NoNetwork,
            reason(decide(MediaKind.Photo, 100_000, AutoDownloadNetwork.None))
        )
    }

    @Test fun channelsMapToTheExpectedCategories() {
        assertEquals(AutoDownloadCategory.Photos, MediaKind.Photo.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Videos, MediaKind.Video.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Videos, MediaKind.Animation.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Videos, MediaKind.VideoNote.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Music, MediaKind.Audio.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Music, MediaKind.Voice.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Files, MediaKind.Document.autoDownloadCategory())
        assertEquals(AutoDownloadCategory.Files, MediaKind.Sticker.autoDownloadCategory())
    }

    @Test fun stickersDownloadOnEveryNetwork() {
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Sticker, 64 * 1024, AutoDownloadNetwork.Mobile))
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Sticker, 64 * 1024, AutoDownloadNetwork.Roaming))
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Sticker, 0, AutoDownloadNetwork.Mobile, secretChat = true))
        assertEquals(AutoDownloadDenial.SecretContent, reason(decide(MediaKind.Sticker, 64 * 1024, AutoDownloadNetwork.Wifi, secret = true)))
        assertEquals(AutoDownloadDenial.NoNetwork, reason(decide(MediaKind.Sticker, 64 * 1024, AutoDownloadNetwork.None)))
    }

    @Test fun unlimitedRuleAllowsAnyKnownSize() {
        val unlimited = settings.copy(
            wifi = settings.wifi.with(AutoDownloadCategory.Photos, AutoDownloadRule(true, Long.MAX_VALUE))
        )
        assertTrue(unlimited.effectivePolicyFor(AutoDownloadNetwork.Wifi).photos.allows(Long.MAX_VALUE))
        assertEquals(AutoDownloadDecision.Allowed, decide(MediaKind.Photo, 90 * 1024 * 1024, AutoDownloadNetwork.Wifi, unlimited))
    }

    @Test fun editingOneNetworkDoesNotChangeAnother() {
        val changed = settings.withPolicy(
            AutoDownloadNetwork.Mobile,
            AutoDownloadNetworkPolicy(photos = AutoDownloadRule(true, 42))
        )
        assertEquals(42L, changed.mobile.photos.maxBytes)
        assertEquals(settings.wifi, changed.wifi)
        assertEquals(settings.roaming, changed.roaming)
    }
}
