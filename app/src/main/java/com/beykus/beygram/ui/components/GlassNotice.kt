package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.usesGlassPanels

/** What a [GlassNotice] reports. The tone decides the tint, never the shape or the layout. */
enum class NoticeTone {
    /** Neutral information: a retry hint, a captcha notice, "messages will be sent when the network is back". */
    Info,

    /** A warning that does not block an action: no location permission yet, a packed queue. */
    Warning,

    /** A failure. It is still the notice surface, not a dialog. */
    Danger
}

/** Opacity of the tone wash when the notice is drawn on a glass plane it does not own (no backdrop to sample). */
private const val NoticeOnSharedGlassAlpha = 0.42f

/**
 * An app-owned notice: an error, a warning or an informational line.
 *
 * In the Liquid Glass style it is a real pane of the material, like the sheet and composer it sits next to. When the
 * notice is drawn on a shared glass plane (the chat composer), there is no backdrop of its own to sample and a second
 * pane would double the blur, so it becomes a translucent tone wash over the plane it already sits on. Every other
 * style keeps the flat warning surface it has always used, so their design does not change.
 *
 * The tone keeps errors and warnings distinguishable, and the text colour comes from the palette, so contrast, TalkBack
 * and the live-region announcements of the call sites are unaffected: a caller announces the row, not this surface.
 */
@Composable
fun GlassNotice(
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Warning,
    shape: Shape = styledShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    /**
     * True when the notice already sits on a glass pane it does not own (the chat header or the shared composer). A
     * second pane there would sample the same backdrop twice and stack two tints and blurs on one surface, so the
     * notice becomes a tone wash over the pane it is drawn on instead.
     */
    onGlassPlane: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    val backdrop = LocalGlassBackdrop.current
    val tint = when (tone) {
        NoticeTone.Info -> palette.surface2
        NoticeTone.Warning -> palette.warningSurface
        NoticeTone.Danger -> palette.warningSurface
    }
    val tintText = when (tone) {
        NoticeTone.Info -> palette.textSecondary
        NoticeTone.Warning -> palette.warningText
        NoticeTone.Danger -> palette.danger
    }
    val rim = palette.textSecondary
    if (design.usesGlassPanels && !onGlassPlane && backdrop != null) {
        GlassPanel(
            design = design,
            tint = tint,
            rim = rim,
            // A recorded list may leave gaps; the pane starts from the screen colour, never from sharp content.
            backdropBase = palette.screen,
            shape = shape,
            modifier = modifier.testTag("glassNoticePanel"),
            contentPadding = contentPadding
        ) {
            CompositionLocalProvider(LocalContentColor provides tintText) {
                Row(Modifier.fillMaxWidth(), content = content)
            }
        }
        return
    }
    val wash = if (design.usesGlassPanels) tint.copy(alpha = NoticeOnSharedGlassAlpha) else tint
    Box(
        modifier
            .testTag("glassNoticeWash")
            .clip(shape)
            .background(wash)
            .then(
                if (design.usesGlassPanels) Modifier.border(1.dp, rim.copy(alpha = 0.16f), shape)
                else Modifier
            )
            .padding(contentPadding)
    ) {
        CompositionLocalProvider(LocalContentColor provides tintText) {
            Row(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** The tint a notice of [tone] uses for an icon, matching the wash of [GlassNotice]. */
@Composable
fun noticeIconTint(tone: NoticeTone): Color {
    val palette = beyGramPalette()
    return when (tone) {
        NoticeTone.Info -> palette.textSecondary
        NoticeTone.Warning -> palette.warningText
        NoticeTone.Danger -> palette.danger
    }
}