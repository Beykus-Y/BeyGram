package com.beykus.beygram.data.appearance

/** What the user chose about the look of the app. The defaults are the look BeyGram has always had. */
data class AppearanceConfig(
    val preset: ThemePreset = ThemePreset.Warm,
    val mode: ThemeMode = ThemeMode.FollowSystem,
    val bubbleStyle: BubbleStyle = BubbleStyle.Rounded,
    val density: ListDensity = ListDensity.Comfortable,
    /** ARGB accent chosen in the editor, or null to keep the accent of the preset. */
    val accentColor: Int? = null,
    /** ARGB colour of outgoing bubbles chosen in the editor, or null to derive it from the accent. */
    val outgoingBubbleColor: Int? = null,
    val wallpaper: ChatWallpaper = ChatWallpaper.None,
    /** 12–22 sp before Android's system fontScale; 16 sp preserves the legacy appearance. */
    val messageTextSizeSp: Int = DEFAULT_MESSAGE_TEXT_SIZE_SP
)

const val DEFAULT_MESSAGE_TEXT_SIZE_SP = 16
const val MIN_MESSAGE_TEXT_SIZE_SP = 12
const val MAX_MESSAGE_TEXT_SIZE_SP = 22

/** Invalid persisted values never reach Compose's bubble measurement. */
fun validMessageTextSize(size: Int): Int = size.coerceIn(MIN_MESSAGE_TEXT_SIZE_SP, MAX_MESSAGE_TEXT_SIZE_SP)
