package com.pedroleite.opencomanda.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.ui.components.ActionEmphasis
import com.pedroleite.opencomanda.ui.components.HomeActionCard
import com.pedroleite.opencomanda.ui.navigation.Destination

/**
 * OpenComanda's Home/dashboard. Venda Rápida and Nova Comanda (the two actions a busy owner
 * reaches for constantly) get the strongest visual weight; day-to-day operations (open
 * comandas, cash register) come next; catalog/management screens are least prominent since
 * they're touched far less often during service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (Destination) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeActionCard(
                        label = stringResource(R.string.action_quick_sale),
                        icon = Icons.Filled.Bolt,
                        emphasis = ActionEmphasis.Primary,
                        onClick = { onNavigate(Destination.QuickSale) },
                    )
                    HomeActionCard(
                        label = stringResource(R.string.action_new_comanda),
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        emphasis = ActionEmphasis.Primary,
                        onClick = { onNavigate(Destination.NewComanda) },
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeActionCard(
                        label = stringResource(R.string.action_open_comandas),
                        icon = Icons.Filled.TableRestaurant,
                        emphasis = ActionEmphasis.Secondary,
                        onClick = { onNavigate(Destination.OpenComandas) },
                    )
                    HomeActionCard(
                        label = stringResource(R.string.action_cash_register),
                        icon = Icons.Filled.PointOfSale,
                        emphasis = ActionEmphasis.Secondary,
                        onClick = { onNavigate(Destination.CashRegister) },
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeActionCard(
                        label = stringResource(R.string.action_products),
                        icon = Icons.Filled.Inventory2,
                        emphasis = ActionEmphasis.Tertiary,
                        onClick = { onNavigate(Destination.Products) },
                    )
                    HomeActionCard(
                        label = stringResource(R.string.action_customers),
                        icon = Icons.Filled.People,
                        emphasis = ActionEmphasis.Tertiary,
                        onClick = { onNavigate(Destination.Customers) },
                    )
                    HomeActionCard(
                        label = stringResource(R.string.action_fiado),
                        icon = Icons.Filled.Handshake,
                        emphasis = ActionEmphasis.Tertiary,
                        onClick = { onNavigate(Destination.Fiado) },
                    )
                }
            }
        }
    }
}
