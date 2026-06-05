package com.stax.core.presentation

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class UiTextTest {

    // DynamicString

    @Test
    fun `DynamicString is a UiText`() {
        assertThat(UiText.DynamicString("x")).isInstanceOf(UiText::class)
    }

    @Test
    fun `DynamicString holds value`() {
        assertThat(UiText.DynamicString("hello").value).isEqualTo("hello")
    }

    // StringResource

    @Test
    fun `StringResource is a UiText`() {
        assertThat(UiText.StringResource(R.string.error_unknown)).isInstanceOf(UiText::class)
    }

    @Test
    fun `StringResource holds id`() {
        val id = R.string.error_disk_full
        assertThat(UiText.StringResource(id).id).isEqualTo(id)
    }

    @Test
    fun `StringResource default args is empty`() {
        assertThat(UiText.StringResource(R.string.error_unknown).args).isEmpty()
    }

    @Test
    fun `StringResource accepts custom args`() {
        val args = arrayOf<Any>("arg1", 42)
        val sr = UiText.StringResource(R.string.error_unknown, args)
        assertThat(sr.args).isEqualTo(args)
    }
}
