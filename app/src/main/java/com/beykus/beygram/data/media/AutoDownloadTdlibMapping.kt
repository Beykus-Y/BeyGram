package com.beykus.beygram.data.media

import org.drinkless.tdlib.TdApi

/**
 * Projects the local settings onto TDLib's own auto-download/preload settings. TDLib receives only
 * size limits and preload switches; it never receives account credentials or file content.
 *
 * TDLib has one "other" bucket, while the UI separates documents from music, so the larger enabled
 * limit is sent for both and the exact per-category decision remains local.
 */
internal object AutoDownloadTdlibMapping {
    val configuredNetworks: List<Pair<AutoDownloadNetwork, TdApi.NetworkType>> = listOf(
        AutoDownloadNetwork.Wifi to TdApi.NetworkTypeWiFi(),
        AutoDownloadNetwork.Mobile to TdApi.NetworkTypeMobile(),
        AutoDownloadNetwork.Roaming to TdApi.NetworkTypeMobileRoaming()
    )

    fun toTdlib(
        settings: AutoDownloadSettings,
        network: AutoDownloadNetwork,
        template: TdApi.AutoDownloadSettings
    ): TdApi.AutoDownloadSettings {
        val policy = settings.effectivePolicyFor(network)
        val otherLimit = listOf(policy.files, policy.music)
            .filter { it.enabled }
            .maxOfOrNull { it.maxBytes }
            ?: 0L
        return TdApi.AutoDownloadSettings(
            /* isAutoDownloadEnabled = */ policy.hasAnyEnabled,
            /* maxPhotoFileSize       = */ policy.photos.limitAsInt(),
            /* maxVideoFileSize       = */ policy.videos.limitOrZero(),
            /* maxOtherFileSize       = */ otherLimit,
            /* videoUploadBitrate     = */ template.videoUploadBitrate,
            /* preloadLargeVideos     = */ policy.videos.enabled,
            /* preloadNextAudio       = */ policy.music.enabled,
            /* preloadStories         = */ false,
            /* useLessDataForCalls    = */ settings.dataSaver
        )
    }

    private fun AutoDownloadRule.limitOrZero(): Long = if (enabled) maxBytes.coerceAtLeast(0L) else 0L

    private fun AutoDownloadRule.limitAsInt(): Int = when {
        !enabled -> 0
        maxBytes >= Int.MAX_VALUE -> Int.MAX_VALUE
        else -> maxBytes.coerceAtLeast(0L).toInt()
    }
}
