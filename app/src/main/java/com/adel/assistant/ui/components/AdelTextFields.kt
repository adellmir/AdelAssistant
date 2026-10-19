package com.adel.assistant.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.data.toEnglishDigits

/**
 * OutlinedTextField با ضربدر پاک‌کردن.
 * اگر صفحهٔ کلید عددی باشد (یا numeric=true):
 *   ارقام فارسی پذیرفته می‌شوند و هنگام خروج از فیلد به انگلیسی تبدیل می‌شوند.
 */
@Composable
fun AdelOutlinedTextField(
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    /** اجبار حالت عددی حتی بدون KeyboardType عددی */
    numeric: Boolean = false
) {
    val kbNumeric = when (keyboardOptions.keyboardType) {
        KeyboardType.Number,
        KeyboardType.Decimal,
        KeyboardType.Phone,
        KeyboardType.NumberPassword -> true
        else -> false
    }
    val treatNumeric = numeric || kbNumeric

    var hadFocus by remember { mutableStateOf(false) }

    val clearableTrailing: @Composable (() -> Unit)? =
        if (enabled && !readOnly && value.isNotEmpty()) {
            {
                Row {
                    trailingIcon?.invoke()
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "پاک کردن",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else trailingIcon

    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            if (treatNumeric) {
                // اجازه ارقام فارسی؛ فقط نویسهٔ غیرعددی حذف
                onValueChange(filterNumericInput(raw))
            } else {
                onValueChange(raw)
            }
        },
        modifier = modifier.onFocusChanged { st ->
            if (hadFocus && !st.isFocused && treatNumeric && value.isNotEmpty()) {
                val eng = value.toEnglishDigits()
                if (eng != value) onValueChange(eng)
            }
            hadFocus = st.isFocused
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = clearableTrailing,
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
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}
