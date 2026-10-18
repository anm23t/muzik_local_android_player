package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/**
 * One button of [BottomNav]. [label] is read by accessibility services.
 *
 * @property enabled false for sections that arrive in later versions (artists, playlists, statistics).
 */
@Immutable
data class BottomNavItem(
    @DrawableRes val icon: Int,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)
