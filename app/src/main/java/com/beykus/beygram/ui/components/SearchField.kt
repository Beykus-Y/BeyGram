package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/** Поле поиска в виде пилюли. [placeholder] показывается, пока поле пустое. */
@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    val palette = beyGramPalette()
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(palette.surface2).padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, color = palette.textSecondary, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, contentDescription = "Очистить", tint = palette.textSecondary,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onChange("") }.padding(5.dp))
                }
            }
        }
    )
}
