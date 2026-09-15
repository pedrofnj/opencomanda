package com.pedroleite.opencomanda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual weight of a [HomeActionCard]: how strongly it should stand out on the Home screen. */
enum class ActionEmphasis { Primary, Secondary, Tertiary }

/**
 * A single tappable Home action (Quick Sale, Products, ...). One reusable composable covers all
 * three emphasis tiers — each with its own size, internal layout and color treatment — so the
 * Home screen's visual hierarchy stays consistent instead of hand-tuning near-duplicate cards
 * per section. Varying layout (not just color) between tiers is what keeps the screen from
 * reading as a uniform stack of same-shaped rectangles.
 */
@Composable
fun HomeActionCard(
    label: String,
    icon: ImageVector,
    emphasis: ActionEmphasis,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    when (emphasis) {
        ActionEmphasis.Primary -> PrimaryActionCard(label, supportingText, icon, onClick, modifier)
        ActionEmphasis.Secondary -> SecondaryActionCard(label, icon, onClick, modifier)
        ActionEmphasis.Tertiary -> TertiaryActionCard(label, icon, onClick, modifier)
    }
}

/** Large, vertical card with an icon badge, bold title and short supporting line. */
@Composable
private fun PrimaryActionCard(
    label: String,
    supportingText: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 180.dp, max = 320.dp)
            .heightIn(min = 140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            ActionIconBadge(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                badgeSize = 48.dp,
                iconSize = 24.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (supportingText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Medium, horizontal card: icon badge beside the label. Deliberately different from the
 *  vertical Primary layout so day-to-day operational actions read as their own visual group. */
@Composable
private fun SecondaryActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 160.dp, max = 320.dp)
            .heightIn(min = 76.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconBadge(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                badgeSize = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Small, compact horizontal card for management/configuration actions used less often. */
@Composable
private fun TertiaryActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 108.dp, max = 200.dp)
            .heightIn(min = 64.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconBadge(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                badgeSize = 32.dp,
                iconSize = 16.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** A small circular color badge behind an action's icon, giving it a stronger, more product-like
 *  presence than a bare tinted icon. */
@Composable
private fun ActionIconBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    badgeSize: Dp,
    iconSize: Dp,
) {
    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
