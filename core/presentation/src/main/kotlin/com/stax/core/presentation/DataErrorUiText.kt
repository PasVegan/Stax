package com.stax.core.presentation

import com.stax.core.domain.DataError

fun DataError.toUiText(): UiText = when (this) {
    DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
    DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
    DataError.Local.CONSTRAINT_VIOLATION -> UiText.StringResource(R.string.error_constraint_violation)
    DataError.Local.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
}
