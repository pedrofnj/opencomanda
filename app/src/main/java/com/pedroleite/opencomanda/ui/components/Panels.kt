package com.pedroleite.opencomanda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Screens wider than this (tablets, foldables, phones in landscape) put their content side by side
 *  instead of in one stacked column. */
val WideLayoutMinWidth = 600.dp

/** A white card with a hairline border on the paper ground — lists, forms, summaries. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** The graphite card reserved for money that matters right now — takings, cash in the drawer. */
@Composable
fun InkPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val inner: @Composable () -> Unit = { Column(modifier = Modifier.padding(20.dp), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            content = inner,
        )
    }
}
