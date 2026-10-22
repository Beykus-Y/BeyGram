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
    val messageTextSizeSp: Int = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    /** Bubble corner radius in dp chosen in the editor, or null to keep the radius of the bubble shape. */
    val bubbleCornerRadiusDp: Int? = null,
    val messageLineSpacing: MessageLineSpacing = MessageLineSpacing.Normal,
    /** Text colour of messages in bubbles, or null for the theme's text colour. */
    val messageTextColor: Int? = null,
    /** Colour of the time and status in bubbles, or null for the theme's muted colour. */
    val messageTimeColor: Int? = null
)

const val DEFAULT_MESSAGE_TEXT_SIZE_SP = 16
const val MIN_MESSAGE_TEXT_SIZE_SP = 12
const val MAX_MESSAGE_TEXT_SIZE_SP = 22

/** Invalid persisted values never reach Compose's bubble measurement. */
fun validMessageTextSize(size: Int): Int = size.coerceIn(MIN_MESSAGE_TEXT_SIZE_SP, MAX_MESSAGE_TEXT_SIZE_SP)

const val MIN_BUBBLE_CORNER_RADIUS_DP = 4
const val MAX_BUBBLE_CORNER_RADIUS_DP = 24

/** Null stays null, which means the radius of the bubble shape. Any other value is kept inside the allowed range. */
fun validBubbleCornerRadius(dp: Int?): Int? = dp?.coerceIn(MIN_BUBBLE_CORNER_RADIUS_DP, MAX_BUBBLE_CORNER_RADIUS_DP)
