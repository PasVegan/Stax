package com.stax.core.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(val id: Int, val args: Array<Any> = emptyArray()) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> stringResource(id, *args)
}

fun Context.asString(uiText: UiText): String = when (uiText) {
    is UiText.DynamicString -> uiText.value
    is UiText.StringResource -> getString(uiText.id, *uiText.args)
}
