package com.pedroleite.opencomanda.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.core.parseCentsInput

/**
 * A money input field: the user only ever types digits, which are read directly as cents (e.g.
 * "1250" -> R$ 12,50) and displayed already formatted as currency. This sidesteps decimal
 * separator ambiguity entirely and makes a negative or malformed amount impossible to type —
 * [valueCents] is always a valid non-negative [Long].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyField(
    label: String,
    valueCents: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val locale = LocalLocale.current.platformLocale
    val displayText = Money(valueCents).format(locale)
    val fieldValue = TextFieldValue(text = displayText, selection = TextRange(displayText.length))

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue -> onValueChange(parseCentsInput(newValue.text)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supportingText?.let { { Text(it) } },
    )
}
