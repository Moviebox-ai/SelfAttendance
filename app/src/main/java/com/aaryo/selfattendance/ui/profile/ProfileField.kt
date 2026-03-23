package com.aaryo.selfattendance.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ProfileField(
    value         : String,
    onValueChange : (String) -> Unit,
    label         : String,
    icon          : ImageVector,
    keyboardType  : KeyboardType = KeyboardType.Text,
    imeAction     : ImeAction    = ImeAction.Next,
    isError       : Boolean      = false,
    enabled       : Boolean      = true,
    initial       : String       = "",
    supportingText: String?      = null,
    // 30-days formula hints — shown below salary/hours fields
    perDayHint    : String?      = null,
    perHourHint   : String?      = null,
    // legacy alias — existing call-sites using onChange= still compile
    onChange      : ((String) -> Unit)? = null
) {
    val handler = onChange ?: onValueChange

    // Build combined hint if any formula hints are provided
    val formulaHint: String? = listOfNotNull(perDayHint, perHourHint)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("  |  ")

    val displayHint = supportingText ?: formulaHint

    OutlinedTextField(
        value         = value,
        onValueChange = handler,
        enabled       = enabled,               // ← passed through to OutlinedTextField

        label = { Text(text = label) },

        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null)
        },

        supportingText = if (displayHint != null) {
            {
                Text(
                    text  = displayHint,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else null,

        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = imeAction
        ),

        singleLine = true,
        isError    = isError,
        modifier   = Modifier.fillMaxWidth(),

        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor    = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor          = MaterialTheme.colorScheme.primary,
            focusedTextColor     = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor   = MaterialTheme.colorScheme.onSurface
        )
    )
}