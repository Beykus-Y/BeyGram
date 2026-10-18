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
    val wallpaper: ChatWallpaper = ChatWallpaper.None
)
