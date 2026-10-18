package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Bottom menu of round 48 buttons spread evenly (27:3, 28:697). Every screen keeps its own
 * set of items on purpose (DESIGN.md, section 1).
 */
@Composable
fun BottomNav(
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        items.forEach { item ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                RoundIconButton(
                    icon = item.icon,
                    contentDescription = item.label,
                    onClick = item.onClick,
                    enabled = item.enabled,
                    size = RoundIconButtonSize.Large,
                )
            }
        }
    }
}
