package com.pedroleite.opencomanda.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A single-line search box with a leading search icon and, once something is typed, a clear
 *  button. Shared by the Customers and Stock lists; callers add their own test tags through
 *  [modifier] and [clearButtonModifier]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    clearButtonModifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = clearButtonModifier) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = clearDescription)
                }
            }
        } else {
            null
        },
    )
}
