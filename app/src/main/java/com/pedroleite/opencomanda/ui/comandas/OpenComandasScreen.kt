package com.pedroleite.opencomanda.ui.comandas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.ui.rememberAppContainer

object OpenComandasTestTags {
    const val CREATE_FAB = "open_comandas_create_fab"
}

@Composable
private fun rememberOpenComandasViewModel(): OpenComandasViewModel {
    val container = rememberAppContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { OpenComandasViewModel(container.orderRepository) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenComandasScreen(
    onBack: () -> Unit,
    onCreateComanda: () -> Unit,
    onOpenComanda: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenComandasViewModel = rememberOpenComandasViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_open_comandas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateComanda,
                modifier = Modifier.testTag(OpenComandasTestTags.CREATE_FAB),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.comanda_list_empty_cta),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Unit
            uiState.comandas.isEmpty() -> OpenComandasEmptyState(
                onCreateComanda = onCreateComanda,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> OpenComandasList(
                comandas = uiState.comandas,
                onOpenComanda = onOpenComanda,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OpenComandasEmptyState(onCreateComanda: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Receipt,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.comanda_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.comanda_list_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onCreateComanda) {
                Text(stringResource(R.string.comanda_list_empty_cta))
            }
        }
    }
}

@Composable
private fun OpenComandasList(
    comandas: List<OrderDao.ComandaSummary>,
    onOpenComanda: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(comandas, key = { it.order.id }) { comanda ->
                OpenComandaRow(comanda = comanda, onClick = { onOpenComanda(comanda.order.id) })
            }
        }
    }
}

@Composable
private fun OpenComandaRow(comanda: OrderDao.ComandaSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = comanda.order.displayName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.comanda_item_count, formatQuantity(comanda.itemCount, locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money(comanda.totalCents).format(locale),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
