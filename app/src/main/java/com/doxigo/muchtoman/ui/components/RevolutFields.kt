package com.doxigo.muchtoman.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.doxigo.muchtoman.Radius

/**
 * The app's text field: Material's outlined field, dressed the way Revolut dresses inputs — a
 * filled well in the card colour with no outline at rest, and a single accent ring only while
 * it has focus. Same signature as material3's, so a screen switches by its import alone.
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(Radius.field),
    colors: TextFieldColors = revFieldColors(),
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        shape = shape,
        colors = colors,
    )
}

@Composable
fun revFieldColors(): TextFieldColors {
    val cs = MaterialTheme.colorScheme
    // On a card the well has to step away from the card itself, so it takes the page tone.
    val well = cs.surfaceContainerLow
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = well,
        unfocusedContainerColor = well,
        disabledContainerColor = well,
        errorContainerColor = well,
        unfocusedBorderColor = cs.outlineVariant,
        focusedBorderColor = cs.primary,
        disabledBorderColor = Color.Transparent,
        unfocusedLabelColor = cs.onSurfaceVariant,
        unfocusedPlaceholderColor = cs.onSurfaceVariant,
        focusedPlaceholderColor = cs.onSurfaceVariant,
    )
}

/**
 * The app's filter chip: a pill in the card colour, and when chosen, inverted — ink on paper by
 * day, paper on ink by night. No outline in either state. Same call shape as material3's.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radius.pill),
    colors: SelectableChipColors? = null,
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors ?: FilterChipDefaults.filterChipColors(
            // One step off the card colour, so a chip still reads as a chip inside a white sheet.
            containerColor = cs.surfaceContainerHighest,
            labelColor = cs.onSurface,
            iconColor = cs.onSurfaceVariant,
            selectedContainerColor = cs.onSurface,
            selectedLabelColor = cs.surface,
            selectedLeadingIconColor = cs.surface,
            selectedTrailingIconColor = cs.surface,
        ),
        border = null,
    )
}
