package com.pedroleite.opencomanda.ui.comandas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.data.repository.OrderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class OpenComandasUiState(
    val isLoading: Boolean = true,
    val comandas: List<OrderDao.ComandaSummary> = emptyList(),
)

/** Backs the Open Comandas list — a live Room query, so creating, closing or cancelling a
 *  Comanda anywhere in the app updates this list automatically. */
class OpenComandasViewModel(orderRepository: OrderRepository) : ViewModel() {

    val uiState: StateFlow<OpenComandasUiState> = orderRepository.getOpenComandas()
        .map { comandas -> OpenComandasUiState(isLoading = false, comandas = comandas) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OpenComandasUiState(),
        )
}
