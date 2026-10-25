package com.beykus.beygram.data.media

import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaKind

/**
 * Network classes that the auto-download policy distinguishes. Only [Wifi] is treated as unmetered;
 * [Other] (for example an external VPN or Ethernet) deliberately follows the mobile policy because the
 * underlying transport cannot always be verified.
 */
enum class AutoDownloadNetwork(val metered: Boolean) {
    Wifi(false),
    /** Wi-Fi that Android reports as metered (hotspot, tethering); it follows the Wi-Fi rule but data saver applies. */
    WifiMetered(true),
    Mobile(true),
    Roaming(true),
    Other(true),
    None(true)
}

enum class AutoDownloadCategory { Photos, Videos, Files, Music }

/**
 * One category on one network. [maxBytes] is the largest file that may be downloaded automatically;
 * [Long.MAX_VALUE] means no size limit. A disabled rule never downloads anything automatically.
 */
data class AutoDownloadRule(
    val enabled: Boolean = false,
    val maxBytes: Long = Long.MAX_VALUE
) {
    fun allows(sizeBytes: Long): Boolean = enabled && sizeBytes > 0L && sizeBytes <= maxBytes
}

/** Rules for photo, video, document and music files on one network. */
data class AutoDownloadNetworkPolicy(
    val photos: AutoDownloadRule = AutoDownloadRule(),
    val videos: AutoDownloadRule = AutoDownloadRule(),
    val files: AutoDownloadRule = AutoDownloadRule(),
    val music: AutoDownloadRule = AutoDownloadRule()
) {
    fun rule(category: AutoDownloadCategory): AutoDownloadRule = when (category) {
        AutoDownloadCategory.Photos -> photos
        AutoDownloadCategory.Videos -> videos
        AutoDownloadCategory.Files -> files
        AutoDownloadCategory.Music -> music
    }

    fun with(category: AutoDownloadCategory, rule: AutoDownloadRule): AutoDownloadNetworkPolicy = when (category) {
        AutoDownloadCategory.Photos -> copy(photos = rule)
        AutoDownloadCategory.Videos -> copy(videos = rule)
        AutoDownloadCategory.Files -> copy(files = rule)
        AutoDownloadCategory.Music -> copy(music = rule)
    }

    val hasAnyEnabled: Boolean
        get() = listOf(photos, videos, files, music).any { it.enabled }
}

/**
 * Local auto-download preferences. TDLib receives a projection of these values for its own preloading,
 * while the app consults the same policy before requesting a file, so a manual download is never blocked.
 */
data class AutoDownloadSettings(
    val wifi: AutoDownloadNetworkPolicy,
    val mobile: AutoDownloadNetworkPolicy,
    val roaming: AutoDownloadNetworkPolicy,
    /** On metered networks this turns every automatic download off; the manual button still works. */
    val dataSaver: Boolean = false
) {
    fun policyFor(network: AutoDownloadNetwork): AutoDownloadNetworkPolicy = when (network) {
        AutoDownloadNetwork.Wifi, AutoDownloadNetwork.WifiMetered -> wifi
        AutoDownloadNetwork.Mobile, AutoDownloadNetwork.Other -> mobile
        AutoDownloadNetwork.Roaming -> roaming
        AutoDownloadNetwork.None -> AutoDownloadNetworkPolicy()
    }

    /** The policy actually used for a decision: data saver disables automatic downloads on metered networks. */
    fun effectivePolicyFor(network: AutoDownloadNetwork): AutoDownloadNetworkPolicy =
        if (dataSaver && network.metered) AutoDownloadNetworkPolicy() else policyFor(network)

    fun withPolicy(network: AutoDownloadNetwork, policy: AutoDownloadNetworkPolicy): AutoDownloadSettings = when (network) {
        AutoDownloadNetwork.Wifi, AutoDownloadNetwork.WifiMetered -> copy(wifi = policy)
        AutoDownloadNetwork.Mobile -> copy(mobile = policy)
        AutoDownloadNetwork.Roaming -> copy(roaming = policy)
        AutoDownloadNetwork.Other, AutoDownloadNetwork.None -> this
    }

    companion object {
        const val MIB = 1024L * 1024L

        /** Conservative defaults: Wi-Fi downloads everything within a reasonable limit, mobile only small photos. */
        fun defaults(): AutoDownloadSettings = AutoDownloadSettings(
            wifi = AutoDownloadNetworkPolicy(
                photos = AutoDownloadRule(true, 10 * MIB),
                videos = AutoDownloadRule(true, 50 * MIB),
                files = AutoDownloadRule(true, 10 * MIB),
                music = AutoDownloadRule(true, 20 * MIB)
            ),
            mobile = AutoDownloadNetworkPolicy(
                photos = AutoDownloadRule(true, 1 * MIB),
                videos = AutoDownloadRule(false, 5 * MIB),
                files = AutoDownloadRule(false, 1 * MIB),
                music = AutoDownloadRule(false, 5 * MIB)
            ),
            roaming = AutoDownloadNetworkPolicy(),
            dataSaver = false
        )
    }
}

/** A single automatic download candidate is [com.beykus.beygram.domain.model.AutoDownloadRequest]. */

sealed interface AutoDownloadDecision {
    data object Allowed : AutoDownloadDecision
    data class Denied(val reason: AutoDownloadDenial) : AutoDownloadDecision
}

enum class AutoDownloadDenial {
    SecretContent,
    SecretChat,
    Spoiler,
    NoNetwork,
    DataSaver,
    SizeUnknown,
    Disabled,
    TooLarge
}

fun MediaKind.autoDownloadCategory(): AutoDownloadCategory = when (this) {
    MediaKind.Photo -> AutoDownloadCategory.Photos
    MediaKind.Video, MediaKind.Animation, MediaKind.VideoNote -> AutoDownloadCategory.Videos
    MediaKind.Audio, MediaKind.Voice -> AutoDownloadCategory.Music
    MediaKind.Document, MediaKind.Sticker -> AutoDownloadCategory.Files
}

/**
 * Pure decision used both by the UI trigger and by the repository that talks to TDLib.
 * Secret chats, disappearing content and unrevealed spoilers are never downloaded automatically;
 * stickers are, on any network.
 */
fun decideAutoDownload(
    settings: AutoDownloadSettings,
    network: AutoDownloadNetwork,
    request: AutoDownloadRequest
): AutoDownloadDecision {
    if (request.isSecret) return AutoDownloadDecision.Denied(AutoDownloadDenial.SecretContent)
    if (network == AutoDownloadNetwork.None) return AutoDownloadDecision.Denied(AutoDownloadDenial.NoNetwork)
    // Stickers and custom emoji are small and are the message itself, so the per-network media rules do not gate them.
    if (request.kind == MediaKind.Sticker) return AutoDownloadDecision.Allowed
    if (request.isSecretChat) return AutoDownloadDecision.Denied(AutoDownloadDenial.SecretChat)
    if (request.hasSpoiler) return AutoDownloadDecision.Denied(AutoDownloadDenial.Spoiler)
    if (settings.dataSaver && network.metered) return AutoDownloadDecision.Denied(AutoDownloadDenial.DataSaver)
    if (request.sizeBytes <= 0L) return AutoDownloadDecision.Denied(AutoDownloadDenial.SizeUnknown)
    val rule = settings.policyFor(network).rule(request.kind.autoDownloadCategory())
    if (!rule.enabled) return AutoDownloadDecision.Denied(AutoDownloadDenial.Disabled)
    if (request.sizeBytes > rule.maxBytes) return AutoDownloadDecision.Denied(AutoDownloadDenial.TooLarge)
    return AutoDownloadDecision.Allowed
}
