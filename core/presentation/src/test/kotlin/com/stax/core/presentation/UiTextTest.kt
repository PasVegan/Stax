package com.stax.core.presentation

import android.content.Context
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiTextTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    // DynamicString

    @Test
    fun `DynamicString is a UiText`() {
        assertThat(UiText.DynamicString("x")).isInstanceOf(UiText::class)
    }

    @Test
    fun `DynamicString holds value`() {
        assertThat(UiText.DynamicString("hello").value).isEqualTo("hello")
    }

    @Test
    fun `asString DynamicString returns value`() {
        assertThat(context.asString(UiText.DynamicString("hello"))).isEqualTo("hello")
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

    @Test
    fun `asString StringResource resolves localized string`() {
        val result = context.asString(UiText.StringResource(R.string.error_unknown))
        assertThat(result).isEqualTo("An unknown error occurred.")
    }

    @Test
    fun `asString StringResource resolves disk full string`() {
        val result = context.asString(UiText.StringResource(R.string.error_disk_full))
        assertThat(result).isEqualTo("Storage is full. Free up space and try again.")
    }
}
