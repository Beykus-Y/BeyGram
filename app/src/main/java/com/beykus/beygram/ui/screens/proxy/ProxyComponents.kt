package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.TelegramProxyKind
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.ChipSelectionTone
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.corner
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.text.pluralForm

/** Название типа прокси, как в макете. */
internal val TelegramProxyKind.label: String
    @Composable get() = when (this) {
        TelegramProxyKind.MTPROTO -> stringResource(R.string.proxy_kind_mtproto)
        TelegramProxyKind.SOCKS5 -> stringResource(R.string.proxy_kind_socks5)
        TelegramProxyKind.HTTP -> stringResource(R.string.proxy_kind_http)
    }

/** Короткое имя протокола для плашки в строке списка: MTProto, SOCKS5 или HTTP. */
internal val TelegramProxyKind.badge: String
    @Composable get() = when (this) {
        TelegramProxyKind.MTPROTO -> stringResource(R.string.proxy_badge_mtproto)
        TelegramProxyKind.SOCKS5 -> stringResource(R.string.proxy_badge_socks5)
        TelegramProxyKind.HTTP -> stringResource(R.string.proxy_badge_http)
    }

/** Пинг до 100 мс зелёный, дальше жёлтый: та же шкала во всех списках прокси. */
@Composable internal fun latencyStyle(delayMillis: Long, palette: BeyGramPalette): Pair<String, Color> =
    stringResource(R.string.proxy_latency_ms, delayMillis) to if (delayMillis < 100) palette.successText else palette.warningText

/** Лучший доступный сервер подписки: имя и пинг. */
internal fun bestReachable(probes: Map<String, com.beykus.beygram.domain.model.MihomoProbeResult>): Pair<String, Long>? =
    probes.values
        .mapNotNull { probe -> if (probe.telegramReachable) probe.delayMillis?.let { probe.connectionName to it } else null }
        .minByOrNull { it.second }

/** «1 сервер», «2 сервера», «5 серверов»: форма по языку интерфейса. */
@Composable internal fun serversCountText(count: Int): String {
    val word = stringResource(pluralForm(LocalConfiguration.current.locales[0].language, count, R.string.proxy_servers_one, R.string.proxy_servers_few, R.string.proxy_servers_many))
    return "$count $word"
}

@Composable
internal fun ProxyTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().topAppBarSurface().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = palette.textPrimary)
        }
        Text(
            title,
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/**
 * Вкладки-таблетки. Выбранная вкладка отмечена так, как принято в текущем стиле: заливкой, подчёркиванием, галочкой или
 * чернилами (см. [ChipSelectionTone]), а не только цветом. Радиус берётся у стиля, высота касания не меньше 48 dp.
 */
@Composable
internal fun ProxyPillTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val tone = design.mainScreen.chipSelection
    val shape = RoundedCornerShape(design.shapes.corner(design.mainScreen.chipRadius))
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(design.spacing.sm)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val container: Modifier = when {
                tone == ChipSelectionTone.Underlined -> Modifier
                selected && tone == ChipSelectionTone.Ink -> Modifier.background(palette.textPrimary, shape)
                selected -> Modifier.background(palette.accentSoft, shape)
                tone == ChipSelectionTone.Filter -> Modifier.border(1.dp, palette.divider, shape)
                tone == ChipSelectionTone.Ink -> Modifier
                else -> Modifier.glassCard(design, palette.surface2, palette.textSecondary, shape)
            }
            val labelColor = when {
                selected && tone == ChipSelectionTone.Ink -> palette.screen
                selected -> palette.accentInk
                else -> palette.textSecondary
            }
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(shape)
                    .then(container)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected && tone == ChipSelectionTone.Filter) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = labelColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        label,
                        color = labelColor,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selected && tone == ChipSelectionTone.Underlined) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(palette.accent))
                }
            }
        }
    }
}

/**
 * Переключатель внутри светлой дорожки. Дорожка и выделенный сегмент берут радиус и заливку у текущего стиля;
 * у стиля с чернилами выделенный сегмент тёмный, у тонального светлый с акцентом.
 */
@Composable
internal fun ProxySegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val tone = design.mainScreen.chipSelection
    val track = RoundedCornerShape(design.shapes.button)
    val thumb = RoundedCornerShape(design.shapes.small)
    Row(
        Modifier.fillMaxWidth().glassCard(design, palette.surface2, palette.textSecondary, track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val thumbFill = when {
                !selected -> Color.Transparent
                tone == ChipSelectionTone.Ink -> palette.textPrimary
                tone == ChipSelectionTone.Filter || tone == ChipSelectionTone.Tonal -> palette.accentSoft
                else -> palette.surface
            }
            val labelColor = when {
                selected && tone == ChipSelectionTone.Ink -> palette.screen
                selected -> palette.accentInk
                else -> palette.textSecondary
            }
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(thumb)
                    .background(thumbFill)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = labelColor,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Поле ввода в стиле экранов авторизации: скругление 12 dp, высота 56 dp. */
@Composable
internal fun ProxyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = styledShape(12.dp),
        colors = beyGramTextFieldColors()
    )
}

/**
 * Группа строк. Поверхность, радиус и обводка совпадают с карточками настроек: Material 3 берёт тональный контейнер,
 * Liquid Glass стеклянную карточку, остальные стили свою заливку и радиус. Разделители ставит вызывающий код.
 */
@Composable
internal fun ProxyCard(content: @Composable ColumnScope.() -> Unit) {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
            .padding(vertical = 4.dp),
        content = content
    )
}

/** Заголовок группы: регистр задаёт стиль, как у заголовков карточек настроек. */
@Composable
internal fun ProxySectionLabel(text: String) {
    val design = LocalDesignSystem.current
    Text(
        if (design.mainScreen.sectionLabelUppercase) text.uppercase() else text,
        color = beyGramPalette().textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = design.mainScreen.sectionLabelTracking,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** Плашка у строки: тип протокола, «лучший», «выбран». Цвета задаёт место, где плашка нужна. */
@Composable
internal fun ProxyBadge(text: String, container: Color, contentColor: Color) {
    Text(
        text,
        color = contentColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(styledShape(8.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** Пинг или «нет связи» в строке. У плохого результата есть значок, поэтому состояние читается и без цвета. */
@Composable
internal fun ProxyLatencyText(text: String, color: Color, warning: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (warning) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Пинг ещё измеряется: вращающийся индикатор с описанием для TalkBack. */
@Composable
internal fun ProxyMeasuring() {
    val description = stringResource(R.string.proxy_latency_measuring)
    CircularProgressIndicator(
        Modifier.size(16.dp).semantics { contentDescription = description },
        color = beyGramPalette().textSecondary,
        strokeWidth = 2.dp
    )
}

/**
 * Строка-переключатель: кружок, название и подпись. Область выбора занимает строку целиком, кроме [actions]: кнопки
 * действий стоят отдельно и доступны TalkBack как самостоятельные элементы. [badges] выводятся у названия, [trailing]
 * внутри области выбора (пинг, статус).
 */
@Composable
internal fun ProxyRadioRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    Row(modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = design.spacing.listRowMinHeight)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected && enabled) palette.accent else palette.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = if (enabled) palette.textPrimary else palette.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    badges()
                }
                subtitle?.let {
                    Text(it, color = palette.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
        actions()
    }
}

/** Крупная кнопка действия во всю ширину: макет использует 52 dp и скругление 16 dp. */
@Composable
internal fun ProxyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val palette = beyGramPalette()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = styledShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = palette.onAccent,
            disabledContainerColor = palette.disabled,
            disabledContentColor = palette.textSecondary
        )
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
