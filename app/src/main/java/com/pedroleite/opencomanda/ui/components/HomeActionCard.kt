package com.pedroleite.opencomanda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Visual weight of a [HomeActionCard]: how strongly it should stand out on the Home screen. */
enum class ActionEmphasis { Primary, Secondary, Tertiary }

/**
 * A single tappable Home action (Quick Sale, Products, ...). One reusable composable covers
 * all three emphasis tiers so the Home screen's visual hierarchy stays consistent instead of
 * hand-tuning near-duplicate cards per section.
 */
@Composable
fun HomeActionCard(
    label: String,
    icon: ImageVector,
    emphasis: ActionEmphasis,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (emphasis) {
        ActionEmphasis.Primary -> MaterialTheme.colorScheme.primaryContainer
        ActionEmphasis.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        ActionEmphasis.Tertiary -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (emphasis) {
        ActionEmphasis.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
        ActionEmphasis.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        ActionEmphasis.Tertiary -> MaterialTheme.colorScheme.onSurface
    }
    val minHeight = when (emphasis) {
        ActionEmphasis.Primary -> 128.dp
        ActionEmphasis.Secondary -> 96.dp
        ActionEmphasis.Tertiary -> 80.dp
    }
    val iconSize = if (emphasis == ActionEmphasis.Primary) 36.dp else 28.dp
    val textStyle = if (emphasis == ActionEmphasis.Primary) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleSmall
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 152.dp, max = 320.dp)
            .heightIn(min = minHeight),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = label,
                style = textStyle,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
