package com.stax.core.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.stax.core.domain.DataError
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class DataErrorUiTextTest {

    @ParameterizedTest
    @EnumSource(DataError.Local::class)
    fun `every DataError Local case maps to a StringResource`(error: DataError.Local) {
        assertThat(error.toUiText()).isInstanceOf(UiText.StringResource::class)
    }

    @Test
    fun `DISK_FULL maps to error_disk_full`() {
        val uiText = DataError.Local.DISK_FULL.toUiText() as UiText.StringResource
        assertThat(uiText.id).isEqualTo(R.string.error_disk_full)
    }

    @Test
    fun `NOT_FOUND maps to error_not_found`() {
        val uiText = DataError.Local.NOT_FOUND.toUiText() as UiText.StringResource
        assertThat(uiText.id).isEqualTo(R.string.error_not_found)
    }

    @Test
    fun `CONSTRAINT_VIOLATION maps to error_constraint_violation`() {
        val uiText = DataError.Local.CONSTRAINT_VIOLATION.toUiText() as UiText.StringResource
        assertThat(uiText.id).isEqualTo(R.string.error_constraint_violation)
    }

    @Test
    fun `UNKNOWN maps to error_unknown`() {
        val uiText = DataError.Local.UNKNOWN.toUiText() as UiText.StringResource
        assertThat(uiText.id).isEqualTo(R.string.error_unknown)
    }
}
