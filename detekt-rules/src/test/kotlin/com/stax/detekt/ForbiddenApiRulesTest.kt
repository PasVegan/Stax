package com.stax.detekt

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test

/**
 * Covers the four forbidden-API guards migrated from the root `checkForbidden*Apis` Gradle tasks.
 *
 * Each rule gets a violating snippet and a clean snippet, plus the cases the old line-regex versions
 * got wrong: an API name inside a string literal or a comment is not a call, and `WindowInsetsRulers`
 * is not `WindowInsets`. Per-file exemptions (`StaxMotion.kt`, `Tokens.kt`, `:core:design-system`)
 * are `excludes` globs in `detekt.yml` and are applied by detekt itself, so they are not re-tested
 * here.
 */
class ForbiddenApiRulesTest {

    @Test
    fun `flags an inline tween`() {
        val findings = NoInlineTween(Config.empty).lint(
            """
            fun spec() = tween<Float>(durationMillis = 300)
            """.trimIndent(),
        )

        assertThat(findings).hasSize(1)
    }

    @Test
    fun `allows a StaxMotion spec`() {
        val findings = NoInlineTween(Config.empty).lint(
            """
            fun spec() = StaxMotion.defaultSpatialSpec()
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    @Test
    fun `ignores tween named in a comment or a string`() {
        val findings = NoInlineTween(Config.empty).lint(
            """
            // Prefer StaxMotion over tween(...) here.
            fun label() = "call tween( to animate"
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    @Test
    fun `flags an inline RoundedCornerShape`() {
        val findings = NoInlineRoundedCornerShape(Config.empty).lint(
            """
            fun shape() = RoundedCornerShape(12)
            """.trimIndent(),
        )

        assertThat(findings).hasSize(1)
    }

    @Test
    fun `allows a shape token`() {
        val findings = NoInlineRoundedCornerShape(Config.empty).lint(
            """
            fun shape() = StaxShapes.Pill
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    @Test
    fun `flags a raw hex Color literal`() {
        val findings = NoRawColorLiteral(Config.empty).lint(
            """
            val seed = Color(0xFF6750A4)
            """.trimIndent(),
        )

        assertThat(findings).hasSize(1)
    }

    @Test
    fun `allows a computed Color and a colorScheme role`() {
        val findings = NoRawColorLiteral(Config.empty).lint(
            """
            val computed = Color(red, green, blue)
            val role = MaterialTheme.colorScheme.primary
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }

    @Test
    fun `flags inset padding modifiers and WindowInsets reads`() {
        val findings = NoWindowInsetsOutsideDesignSystem(Config.empty).lint(
            """
            fun a() = Modifier.imePadding()
            fun b() = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            fun c() = Modifier.consumeWindowInsets(padding)
            """.trimIndent(),
        )

        // b() reports twice: the windowInsetsPadding call and the WindowInsets.safeDrawing read.
        assertThat(findings).hasSize(4)
    }

    @Test
    fun `allows paneInsets and types that merely start with WindowInsets`() {
        val findings = NoWindowInsetsOutsideDesignSystem(Config.empty).lint(
            """
            fun a() = Modifier.paneInsets()
            fun b() = Modifier.fitInside(WindowInsetsRulers.SafeDrawing.current)
            """.trimIndent(),
        )

        assertThat(findings).isEmpty()
    }
}
