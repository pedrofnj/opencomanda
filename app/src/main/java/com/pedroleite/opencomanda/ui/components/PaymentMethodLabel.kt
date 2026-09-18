package com.pedroleite.opencomanda.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.domain.PaymentMethod

/** Localized label for a [PaymentMethod] — shared by Quick Sale and Comanda closing, the two
 *  places an operator picks an immediate payment method. */
@Composable
fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> stringResource(R.string.payment_method_cash)
    PaymentMethod.DEBIT -> stringResource(R.string.payment_method_debit)
    PaymentMethod.CREDIT -> stringResource(R.string.payment_method_credit)
    PaymentMethod.PIX -> stringResource(R.string.payment_method_pix)
    PaymentMethod.OTHER -> stringResource(R.string.payment_method_other)
}
