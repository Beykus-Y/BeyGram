package com.beykus.beygram.data.canvas

enum class BgCanvasMode {
    Off,
    Card,
    Fullscreen,
}

data class CanvasSourceSettings(
    val mode: BgCanvasMode = BgCanvasMode.Card,
    val respectDataSaver: Boolean = true,
)

data class CanvasAsset(
    val playbackKey: String,
    val localFilePath: String,
    val videoId: String?,
    val sizeBytes: Long,
)

enum class CanvasUnavailableReason {
    Disabled,
    DataSaver,
    RestrictedPrivacy,
    NotAvailable,
    Failed,
}

sealed interface CanvasLoadState {
    object Idle : CanvasLoadState
    object Loading : CanvasLoadState
    data class Ready(val asset: CanvasAsset) : CanvasLoadState
    data class Unavailable(val reason: CanvasUnavailableReason) : CanvasLoadState
}
