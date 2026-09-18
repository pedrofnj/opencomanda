package com.pedroleite.opencomanda.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.domain.DebtStatus

/** Localized label for a Fiado debt's status — status is never shown as a raw enum value or
 *  communicated by color alone (see the status text always shown alongside it). */
@Composable
fun debtStatusLabel(status: DebtStatus): String = when (status) {
    DebtStatus.OPEN -> stringResource(R.string.fiado_debt_status_open)
    DebtStatus.PARTIALLY_PAID -> stringResource(R.string.fiado_debt_status_partial)
    DebtStatus.PAID -> stringResource(R.string.fiado_debt_status_paid)
}
