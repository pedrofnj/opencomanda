package com.pedroleite.opencomanda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity

/** One sellable product in a picking grid (Quick Sale, adding to a Comanda): tap the card to add one; once in the cart a stepper appears beneath
 *  and the card gets a teal ring, so what's already in the sale stands out while scanning. */
@Composable
fun ProductPickCard(
    product: ProductEntity,
    categoryName: String?,
    quantity: Double,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val addDescription = stringResource(R.string.quicksale_add_action, product.name)
    // A tracked product at zero can't be added. The repository still enforces this; the card just
    // says so in words instead of letting the operator find out at confirmation.
    val outOfStock = product.trackStock && product.stockQuantity <= 0.0
    val addBlocked = outOfStock && quantity <= 0.0
    val inCart = quantity > 0.0
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onAdd,
        enabled = !addBlocked,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        border = if (inCart) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = Money(product.priceCents).format(locale),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    )
                }
                if (!inCart) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (addBlocked) colors.surfaceContainer else colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (addBlocked) {
                                stringResource(R.string.stock_product_out_of_stock_description, product.name)
                            } else {
                                addDescription
                            },
                            modifier = Modifier.size(22.dp),
                            tint = if (addBlocked) colors.onSurfaceVariant else colors.onPrimaryContainer,
                        )
                    }
                }
            }
            if (outOfStock) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.stock_out_of_stock),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.error,
                )
            } else if (categoryName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (inCart) {
                Spacer(Modifier.height(8.dp))
                ProductQuantityStepper(
                    productName = product.name,
                    quantity = quantity,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProductQuantityStepper(
    productName: String,
    quantity: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val decreaseDescription = stringResource(R.string.quicksale_decrease_quantity, productName)
    val increaseDescription = stringResource(R.string.quicksale_increase_quantity, productName)
    val quantityDescription = stringResource(R.string.quicksale_quantity_label, productName, formatQuantity(quantity, locale))

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDecrement, modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.small) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = decreaseDescription)
        }
        Text(
            text = formatQuantity(quantity, locale),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = quantityDescription },
            textAlign = TextAlign.Center,
        )
        FilledIconButton(onClick = onIncrement, modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.small) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = increaseDescription)
        }
    }
}
