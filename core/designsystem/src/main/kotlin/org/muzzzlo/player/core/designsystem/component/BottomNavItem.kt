package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/** One button of [BottomNav]. [label] is read by accessibility services. */
@Immutable
data class BottomNavItem(
    @DrawableRes val icon: Int,
    val label: String,
    val onClick: () -> Unit,
)
