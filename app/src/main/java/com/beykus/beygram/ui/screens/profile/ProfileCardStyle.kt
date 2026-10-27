package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared card rhythm for the profile, Premium, Stars and gifts screens. */
private val CardScreenInset = 16.dp
private val CardRadius = 20.dp
private val CardGap = 12.dp

/**
 * A grouped surface card. Each card gets half the gap above and below, so two neighbouring cards keep a 12 dp
 * gap and their rounded corners never touch.
 */
@Stable
internal fun Modifier.profileCardSurface(color: Color): Modifier =
    padding(horizontal = CardScreenInset, vertical = CardGap / 2)
        .clip(RoundedCornerShape(CardRadius))
        .background(color)
