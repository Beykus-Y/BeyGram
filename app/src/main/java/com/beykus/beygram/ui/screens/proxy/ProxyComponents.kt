package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.TelegramProxyKind
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.pluralForm

/** Название типа прокси, как в макете. */
internal val TelegramProxyKind.label: String
    @Composable get() = when (this) {
        TelegramProxyKind.MTPROTO -> stringResource(R.string.proxy_kind_mtproto)
        TelegramProxyKind.SOCKS5 -> stringResource(R.string.proxy_kind_socks5)
        TelegramProxyKind.HTTP -> stringResource(R.string.proxy_kind_http)
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
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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

/** Вкладки-таблетки: выбранная подсвечена акцентом. */
@Composable
internal fun ProxyPillTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) palette.accentSoft else palette.surface2)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) palette.accentInk else palette.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Двухсторонний переключатель внутри светлой плашки. */
@Composable
internal fun ProxySegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) palette.surface else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) palette.accentInk else palette.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
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
        shape = RoundedCornerShape(12.dp),
        colors = beyGramTextFieldColors()
    )
}

/** Белая карточка со строками. Разделители между строками ставит вызывающий код. */
@Composable
internal fun ProxyCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(beyGramPalette().surface)
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
internal fun ProxySectionLabel(text: String) {
    Text(
        text,
        color = beyGramPalette().textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

/** Строка с радио-кнопкой: название, необязательная подпись и правая часть (пинг, кнопки). */
@Composable
internal fun ProxyRadioRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    badge: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val palette = beyGramPalette()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                badge?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it,
                        color = palette.successText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.successSurface)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            subtitle?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp) }
        }
        trailing()
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
        shape = RoundedCornerShape(16.dp),
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
