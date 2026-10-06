package com.foleyit.itflow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Small tinted status chip, same idiom as [PriorityBadge]: a 15% wash of [color] behind text in [color].
 * Text wraps instead of clipping at large font sizes.
 */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
