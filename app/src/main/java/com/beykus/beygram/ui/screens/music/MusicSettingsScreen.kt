package com.beykus.beygram.ui.screens.music

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.canvas.AppCanvasRepository
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasSettingsStore
import com.beykus.beygram.data.lyrics.AppLyricsRepository
import com.beykus.beygram.data.lyrics.LyricsSettingsStore
import com.beykus.beygram.media.bgbeat.BgBeatSettingsStore
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.media.bgrhythm.BgRhythmMode
import com.beykus.beygram.media.bgrhythm.BgRhythmSettingsStore
import com.beykus.beygram.playback.TrackTransitionSettings
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.playback.LyricsAlignment
import com.beykus.beygram.ui.screens.playback.LyricsMotionStyle
import com.beykus.beygram.ui.screens.playback.LyricsStyleStore
import com.beykus.beygram.ui.screens.playback.LyricsTextSize
import com.beykus.beygram.ui.screens.playback.LyricsVisualStyle
import com.beykus.beygram.ui.screens.settings.CrossfadeLengthRow
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import com.beykus.beygram.ui.screens.settings.SettingsLink
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.screens.settings.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class MusicSettingsSection {
    Hub,
    Playback,
    Appearance,
    Lyrics,
    Storage,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    initialSection: MusicSettingsSection = MusicSettingsSection.Hub,
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(initialSection) }

    BackHandler {
        if (section != MusicSettingsSection.Hub) section = MusicSettingsSection.Hub
        else onBack()
    }

    remember(context) { BgMixSettingsStore.load(context) }
    remember(context) { TrackTransitionSettings.load(context) }
    val bgMixEnabled by BgMixSettingsStore.enabled.collectAsStateWithLifecycle()
    val bgBeatStore = remember(context) { BgBeatSettingsStore.getInstance(context) }
    val bgBeatEnabled by bgBeatStore.enabled.collectAsStateWithLifecycle()
    val crossfade by TrackTransitionSettings.crossfade.collectAsStateWithLifecycle()
    val skipSilence by TrackTransitionSettings.skipSilence.collectAsStateWithLifecycle()
    val crossfadeSeconds by TrackTransitionSettings.crossfadeSeconds.collectAsStateWithLifecycle()

    val rhythmFlow = remember(context) { BgRhythmSettingsStore.flow(context) }
    val rhythmMode by rhythmFlow.collectAsStateWithLifecycle()
    val canvasFlow = remember(context) { CanvasSettingsStore.flow(context) }
    val canvasSettings by canvasFlow.collectAsStateWithLifecycle()

    val lyricsStyleFlow = remember(context) { LyricsStyleStore.flow(context) }
    val lyricsStyle by lyricsStyleFlow.collectAsStateWithLifecycle()
    val lyricsSourcesFlow = remember(context) { LyricsSettingsStore.flow(context) }
    val lyricsSources by lyricsSourcesFlow.collectAsStateWithLifecycle()

    val titleText = when (section) {
        MusicSettingsSection.Hub -> stringResource(R.string.music_settings_title)
        MusicSettingsSection.Playback -> stringResource(R.string.music_settings_card_playback_title)
        MusicSettingsSection.Appearance -> stringResource(R.string.music_settings_card_appearance_title)
        MusicSettingsSection.Lyrics -> stringResource(R.string.music_settings_card_lyrics_title)
        MusicSettingsSection.Storage -> stringResource(R.string.music_settings_card_storage_title)
    }

    val panelMotion = rememberMotionSpec(MotionToken.NavigationPush)

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarSurface(),
                title = {
                    Text(
                        titleText,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (section != MusicSettingsSection.Hub) section = MusicSettingsSection.Hub
                        else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.common_back),
                            tint = palette.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                if (panelMotion.isInstant) {
                    fadeIn(androidx.compose.animation.core.snap()) togetherWith
                        fadeOut(androidx.compose.animation.core.snap())
                } else {
                    fadeIn(panelMotion.animationSpec()) togetherWith
                        fadeOut(panelMotion.animationSpec())
                }
            },
            label = "musicSettingsSection",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { activeSection ->
            when (activeSection) {
                MusicSettingsSection.Hub -> MusicSettingsHub(
                    bgMixEnabled = bgMixEnabled,
                    bgBeatEnabled = bgBeatEnabled,
                    crossfade = crossfade,
                    rhythmMode = rhythmMode,
                    canvasMode = canvasSettings.mode,
                    lyricsVisual = lyricsStyle.visual,
                    externalLyricsEnabled = lyricsSources.externalLookupEnabled,
                    onSelectSection = { section = it },
                )
                MusicSettingsSection.Playback -> MusicPlaybackSettingsPage(
                    bgMixEnabled = bgMixEnabled,
                    bgBeatEnabled = bgBeatEnabled,
                    skipSilence = skipSilence,
                    crossfade = crossfade,
                    crossfadeSeconds = crossfadeSeconds,
                    onBgMixChange = { BgMixSettingsStore.setEnabled(context, it) },
                    onBgBeatChange = { bgBeatStore.setEnabled(it) },
                    onSkipSilenceChange = { TrackTransitionSettings.setSkipSilence(context, it) },
                    onCrossfadeChange = { TrackTransitionSettings.setCrossfade(context, it) },
                    onCrossfadeSecondsChange = { TrackTransitionSettings.setCrossfadeSeconds(context, it) },
                    onOpenEqualizer = onOpenEqualizer,
                )
                MusicSettingsSection.Appearance -> MusicAppearanceSettingsPage(
                    rhythmMode = rhythmMode,
                    bgBeatEnabled = bgBeatEnabled,
                    canvasMode = canvasSettings.mode,
                    respectDataSaver = canvasSettings.respectDataSaver,
                    onRhythmModeChange = { BgRhythmSettingsStore.setMode(context, it) },
                    onEnableBgBeat = { bgBeatStore.setEnabled(true) },
                    onCanvasModeChange = { CanvasSettingsStore.setMode(context, it) },
                    onDataSaverChange = { CanvasSettingsStore.setRespectDataSaver(context, it) },
                )
                MusicSettingsSection.Lyrics -> MusicLyricsSettingsPage(
                    style = lyricsStyle,
                    sources = lyricsSources,
                    onVisualChange = { v -> LyricsStyleStore.update(context) { it.copy(visual = v) } },
                    onMotionChange = { m -> LyricsStyleStore.update(context) { it.copy(motion = m) } },
                    onSizeChange = { s -> LyricsStyleStore.update(context) { it.copy(size = s) } },
                    onAlignmentChange = { a -> LyricsStyleStore.update(context) { it.copy(alignment = a) } },
                    onEmbeddedChange = { e -> LyricsSettingsStore.update(context) { it.copy(embeddedTagsEnabled = e) } },
                    onYandexChange = { y -> LyricsSettingsStore.update(context) { it.copy(yandexEnabled = y) } },
                    onExternalChange = { ext -> LyricsSettingsStore.setExternalLookupEnabled(context, ext) },
                )
                MusicSettingsSection.Storage -> MusicStorageSettingsPage()
            }
        }
    }
}

@Composable
private fun MusicSettingsHub(
    bgMixEnabled: Boolean,
    bgBeatEnabled: Boolean,
    crossfade: Boolean,
    rhythmMode: BgRhythmMode,
    canvasMode: BgCanvasMode,
    lyricsVisual: LyricsVisualStyle,
    externalLyricsEnabled: Boolean,
    onSelectSection: (MusicSettingsSection) -> Unit,
) {
    val palette = beyGramPalette()
    val onLabel = stringResource(R.string.music_settings_state_on)
    val offLabel = stringResource(R.string.music_settings_state_off)

    val playbackSummary = listOf(
        "BG Mix: ${if (bgMixEnabled) onLabel else offLabel}",
        "BG Beat: ${if (bgBeatEnabled) onLabel else offLabel}",
        "${stringResource(R.string.settings_crossfade_title)}: ${if (crossfade) onLabel else offLabel}",
    ).joinToString(" · ")

    val rhythmLabel = when (rhythmMode) {
        BgRhythmMode.Off -> stringResource(R.string.rhythm_mode_off)
        BgRhythmMode.Calm -> stringResource(R.string.rhythm_mode_calm)
        BgRhythmMode.Dynamic -> stringResource(R.string.rhythm_mode_dynamic)
    }
    val canvasLabel = when (canvasMode) {
        BgCanvasMode.Off -> stringResource(R.string.canvas_mode_off)
        BgCanvasMode.Card -> stringResource(R.string.canvas_mode_card)
        BgCanvasMode.Fullscreen -> stringResource(R.string.canvas_mode_fullscreen)
    }
    val appearanceSummary = "BG Rhythm: $rhythmLabel · BG Canvas: $canvasLabel"

    val lyricsStyleLabel = when (lyricsVisual) {
        LyricsVisualStyle.Editorial -> stringResource(R.string.lyrics_style_editorial)
        LyricsVisualStyle.Focus -> stringResource(R.string.lyrics_style_focus)
        LyricsVisualStyle.Reading -> stringResource(R.string.lyrics_style_reading)
    }
    val lyricsSummary = "$lyricsStyleLabel · LRCLIB: ${if (externalLyricsEnabled) onLabel else offLabel}"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(styledShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                palette.accent.copy(alpha = 0.22f),
                                palette.accentSoft.copy(alpha = 0.35f),
                                palette.surface,
                            )
                        )
                    )
                    .border(1.dp, palette.divider.copy(alpha = 0.7f), styledShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(styledShape(14.dp))
                            .background(palette.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = palette.accentInk, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.music_settings_hero_title),
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.music_settings_hero_subtitle),
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
        item(key = "playback") {
            MusicTechCard(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.music_settings_card_playback_title),
                description = stringResource(R.string.music_settings_card_playback_desc),
                statusSummary = playbackSummary,
                onClick = { onSelectSection(MusicSettingsSection.Playback) },
            )
        }
        item(key = "appearance") {
            MusicTechCard(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.music_settings_card_appearance_title),
                description = stringResource(R.string.music_settings_card_appearance_desc),
                statusSummary = appearanceSummary,
                onClick = { onSelectSection(MusicSettingsSection.Appearance) },
            )
        }
        item(key = "lyrics") {
            MusicTechCard(
                icon = Icons.Rounded.Lyrics,
                title = stringResource(R.string.music_settings_card_lyrics_title),
                description = stringResource(R.string.music_settings_card_lyrics_desc),
                statusSummary = lyricsSummary,
                onClick = { onSelectSection(MusicSettingsSection.Lyrics) },
            )
        }
        item(key = "storage") {
            MusicTechCard(
                icon = Icons.Rounded.Storage,
                title = stringResource(R.string.music_settings_card_storage_title),
                description = stringResource(R.string.music_settings_card_storage_desc),
                statusSummary = stringResource(R.string.music_settings_storage_bgbeat),
                onClick = { onSelectSection(MusicSettingsSection.Storage) },
            )
        }
    }
}

@Composable
private fun MusicTechCard(
    icon: ImageVector,
    title: String,
    description: String,
    statusSummary: String,
    onClick: () -> Unit,
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val shape = styledShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(design, palette.surface, palette.divider, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(styledShape(12.dp))
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            Text(
                statusSummary,
                color = palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(styledShape(8.dp))
                    .background(palette.accentSoft.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textSecondary)
    }
}

@Composable
private fun MusicPlaybackSettingsPage(
    bgMixEnabled: Boolean,
    bgBeatEnabled: Boolean,
    skipSilence: Boolean,
    crossfade: Boolean,
    crossfadeSeconds: Int,
    onBgMixChange: (Boolean) -> Unit,
    onBgBeatChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onCrossfadeChange: (Boolean) -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val palette = beyGramPalette()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsGroup(stringResource(R.string.settings_experiments_group_music)) {
            SettingsToggle(
                icon = Icons.Rounded.SwapHoriz,
                title = stringResource(R.string.settings_bgmix_title),
                subtitle = stringResource(R.string.settings_bgmix_desc),
                checked = bgMixEnabled,
                onCheckedChange = onBgMixChange,
            )
            SettingsRowDivider()
            SettingsToggle(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.settings_bgbeat_title),
                subtitle = stringResource(R.string.settings_bgbeat_desc),
                checked = bgBeatEnabled,
                onCheckedChange = onBgBeatChange,
            )
        }
        SettingsGroup(stringResource(R.string.settings_experiments_group_transitions)) {
            SettingsToggle(
                icon = Icons.Rounded.SkipNext,
                title = stringResource(R.string.settings_skip_silence_title),
                subtitle = stringResource(R.string.settings_skip_silence_desc),
                checked = skipSilence,
                onCheckedChange = onSkipSilenceChange,
            )
            SettingsRowDivider()
            SettingsToggle(
                icon = Icons.Rounded.SwapHoriz,
                title = stringResource(R.string.settings_crossfade_title),
                subtitle = stringResource(R.string.settings_crossfade_desc),
                checked = crossfade,
                onCheckedChange = onCrossfadeChange,
            )
            if (crossfade) {
                CrossfadeLengthRow(seconds = crossfadeSeconds, onChange = onCrossfadeSecondsChange)
            }
        }
        SettingsGroup(stringResource(R.string.player_equalizer)) {
            SettingsLink(
                icon = Icons.Rounded.Equalizer,
                title = stringResource(R.string.player_equalizer),
                onClick = onOpenEqualizer,
            )
        }
        Text(
            stringResource(R.string.settings_experiments_transitions_note),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MusicAppearanceSettingsPage(
    rhythmMode: BgRhythmMode,
    bgBeatEnabled: Boolean,
    canvasMode: BgCanvasMode,
    respectDataSaver: Boolean,
    onRhythmModeChange: (BgRhythmMode) -> Unit,
    onEnableBgBeat: () -> Unit,
    onCanvasModeChange: (BgCanvasMode) -> Unit,
    onDataSaverChange: (Boolean) -> Unit,
) {
    val palette = beyGramPalette()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsGroup(stringResource(R.string.rhythm_section_title)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsChip(stringResource(R.string.rhythm_mode_off), rhythmMode == BgRhythmMode.Off) {
                    onRhythmModeChange(BgRhythmMode.Off)
                }
                SettingsChip(stringResource(R.string.rhythm_mode_calm), rhythmMode == BgRhythmMode.Calm) {
                    onRhythmModeChange(BgRhythmMode.Calm)
                }
                SettingsChip(stringResource(R.string.rhythm_mode_dynamic), rhythmMode == BgRhythmMode.Dynamic) {
                    onRhythmModeChange(BgRhythmMode.Dynamic)
                }
            }
            if (rhythmMode != BgRhythmMode.Off && !bgBeatEnabled) {
                Text(
                    stringResource(R.string.rhythm_needs_bgbeat_hint),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    SettingsChip(stringResource(R.string.rhythm_enable_bgbeat), selected = false, onClick = onEnableBgBeat)
                }
            }
        }
        SettingsGroup(stringResource(R.string.canvas_section_title)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsChip(stringResource(R.string.canvas_mode_off), canvasMode == BgCanvasMode.Off) {
                    onCanvasModeChange(BgCanvasMode.Off)
                }
                SettingsChip(stringResource(R.string.canvas_mode_card), canvasMode == BgCanvasMode.Card) {
                    onCanvasModeChange(BgCanvasMode.Card)
                }
                SettingsChip(stringResource(R.string.canvas_mode_fullscreen), canvasMode == BgCanvasMode.Fullscreen) {
                    onCanvasModeChange(BgCanvasMode.Fullscreen)
                }
            }
            SettingsRowDivider()
            SettingsToggle(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.music_settings_canvas_datasaver_title),
                subtitle = stringResource(R.string.music_settings_canvas_datasaver_desc),
                checked = respectDataSaver,
                onCheckedChange = onDataSaverChange,
            )
        }
    }
}

@Composable
private fun MusicLyricsSettingsPage(
    style: com.beykus.beygram.ui.screens.playback.LyricsStyleConfig,
    sources: com.beykus.beygram.data.lyrics.LyricsSourceSettings,
    onVisualChange: (LyricsVisualStyle) -> Unit,
    onMotionChange: (LyricsMotionStyle) -> Unit,
    onSizeChange: (LyricsTextSize) -> Unit,
    onAlignmentChange: (LyricsAlignment) -> Unit,
    onEmbeddedChange: (Boolean) -> Unit,
    onYandexChange: (Boolean) -> Unit,
    onExternalChange: (Boolean) -> Unit,
) {
    val palette = beyGramPalette()
    val align = if (style.alignment == LyricsAlignment.Center) TextAlign.Center else TextAlign.Start
    val previewSize = when (style.size) {
        LyricsTextSize.Compact -> 14.sp
        LyricsTextSize.Normal -> 16.sp
        LyricsTextSize.Large -> 19.sp
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Live mini-preview reflecting visual style, size, and alignment.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(styledShape(16.dp))
                .background(palette.surface)
                .border(1.dp, palette.divider, styledShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.music_settings_lyrics_preview_line1),
                color = palette.textPrimary,
                fontSize = previewSize,
                fontWeight = if (style.visual == LyricsVisualStyle.Focus) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.music_settings_lyrics_preview_line2),
                color = palette.textSecondary.copy(alpha = if (style.visual == LyricsVisualStyle.Focus) 0.55f else 0.85f),
                fontSize = previewSize,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsGroup(stringResource(R.string.lyrics_style_section)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsChip(stringResource(R.string.lyrics_style_editorial), style.visual == LyricsVisualStyle.Editorial) {
                    onVisualChange(LyricsVisualStyle.Editorial)
                }
                SettingsChip(stringResource(R.string.lyrics_style_focus), style.visual == LyricsVisualStyle.Focus) {
                    onVisualChange(LyricsVisualStyle.Focus)
                }
                SettingsChip(stringResource(R.string.lyrics_style_reading), style.visual == LyricsVisualStyle.Reading) {
                    onVisualChange(LyricsVisualStyle.Reading)
                }
            }
        }
        SettingsGroup(stringResource(R.string.lyrics_motion_section)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsChip(stringResource(R.string.lyrics_motion_smooth), style.motion == LyricsMotionStyle.Smooth) {
                    onMotionChange(LyricsMotionStyle.Smooth)
                }
                SettingsChip(stringResource(R.string.lyrics_motion_dynamic), style.motion == LyricsMotionStyle.Dynamic) {
                    onMotionChange(LyricsMotionStyle.Dynamic)
                }
                SettingsChip(stringResource(R.string.lyrics_motion_calm), style.motion == LyricsMotionStyle.Calm) {
                    onMotionChange(LyricsMotionStyle.Calm)
                }
            }
        }
        SettingsGroup(stringResource(R.string.lyrics_size_section)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsChip(stringResource(R.string.lyrics_size_compact), style.size == LyricsTextSize.Compact) {
                    onSizeChange(LyricsTextSize.Compact)
                }
                SettingsChip(stringResource(R.string.lyrics_size_normal), style.size == LyricsTextSize.Normal) {
                    onSizeChange(LyricsTextSize.Normal)
                }
                SettingsChip(stringResource(R.string.lyrics_size_large), style.size == LyricsTextSize.Large) {
                    onSizeChange(LyricsTextSize.Large)
                }
            }
        }
        SettingsGroup(stringResource(R.string.lyrics_align_section)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsChip(stringResource(R.string.lyrics_align_start), style.alignment == LyricsAlignment.Start) {
                    onAlignmentChange(LyricsAlignment.Start)
                }
                SettingsChip(stringResource(R.string.lyrics_align_center), style.alignment == LyricsAlignment.Center) {
                    onAlignmentChange(LyricsAlignment.Center)
                }
            }
        }
        SettingsGroup(stringResource(R.string.lyrics_sources_section)) {
            SettingsToggle(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.music_settings_lyrics_source_embedded),
                subtitle = null,
                checked = sources.embeddedTagsEnabled,
                onCheckedChange = onEmbeddedChange,
            )
            SettingsRowDivider()
            SettingsToggle(
                icon = Icons.Rounded.Lyrics,
                title = stringResource(R.string.music_settings_lyrics_source_yandex),
                subtitle = null,
                checked = sources.yandexEnabled,
                onCheckedChange = onYandexChange,
            )
            SettingsRowDivider()
            SettingsToggle(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.music_settings_lyrics_source_lrclib),
                subtitle = stringResource(R.string.lyrics_external_disabled),
                checked = sources.externalLookupEnabled,
                onCheckedChange = onExternalChange,
            )
        }
    }
}

@Composable
private fun MusicStorageSettingsPage() {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var bgBeatBytes by remember { mutableLongStateOf(BgBeatSettingsStore.getCacheSizeBytes(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val canvasDiag = remember { AppCanvasRepository.get(context).diagnosticsSummary() }

    val bgBeatClearedMsg = stringResource(R.string.music_settings_storage_bgbeat_cleared)
    val canvasClearedMsg = stringResource(R.string.music_settings_storage_canvas_cleared)
    val lyricsClearedMsg = stringResource(R.string.lyrics_cache_cleared)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SettingsGroup(stringResource(R.string.music_settings_card_storage_title)) {
            SettingsLink(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.music_settings_storage_clear_bgbeat),
                subtitle = "${stringResource(R.string.music_settings_storage_bgbeat)}: ${Formatter.formatShortFileSize(context, bgBeatBytes)}",
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { BgBeatSettingsStore.clearAllCache(context) }
                        bgBeatBytes = BgBeatSettingsStore.getCacheSizeBytes(context)
                        statusMessage = bgBeatClearedMsg
                    }
                },
            )
            SettingsRowDivider()
            SettingsLink(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.music_settings_storage_clear_canvas),
                onClick = {
                    scope.launch {
                        AppCanvasRepository.get(context).clearCacheOffMain()
                        statusMessage = canvasClearedMsg
                    }
                },
            )
            SettingsRowDivider()
            SettingsLink(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.lyrics_clear_cache),
                onClick = {
                    scope.launch {
                        AppLyricsRepository.get(context).clearCacheOffMain()
                        statusMessage = lyricsClearedMsg
                    }
                },
            )
        }
        statusMessage?.let { msg ->
            Text(
                msg,
                color = palette.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        SettingsGroup(stringResource(R.string.music_settings_storage_canvas_diag)) {
            Text(
                canvasDiag ?: stringResource(R.string.music_settings_storage_canvas_diag_empty),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val shape = styledShape(24.dp)
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) palette.accent else Color.Transparent, shape)
            .border(1.dp, palette.divider, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Optional callback to open Music Settings 2.0 from nested player sheets. */
internal val LocalOpenMusicSettings = staticCompositionLocalOf<(() -> Unit)?> { null }
