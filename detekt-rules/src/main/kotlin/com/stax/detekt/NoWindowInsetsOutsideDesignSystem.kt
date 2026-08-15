package com.stax.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Flags any `WindowInsets` API outside `:core:design-system` — the inset-padding modifiers, the
 * inset size modifiers, `consumeWindowInsets`, and reading `WindowInsets.<type>` directly.
 *
 * A pane gets **exactly one** inset method, `Modifier.paneInsets()` (spec §2.3.6). Mixing a second
 * one in — a `Scaffold`'s `contentWindowInsets`, a stray `imePadding()` inside a pane that already
 * called `paneInsets()` — is the double-padding bug, and it stays invisible until someone looks at
 * the right device in the right orientation. `:core:design-system` owns inset handling and is
 * exempted via `excludes` in `detekt.yml`.
 */
class NoWindowInsetsOutsideDesignSystem(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoWindowInsetsOutsideDesignSystem",
        severity = Severity.Defect,
        description = "WindowInsets APIs are forbidden outside :core:design-system; a pane gets " +
            "exactly one inset method, Modifier.paneInsets() (spec §2.3.6).",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeName() ?: return
        if (callee !in INSET_FUNCTIONS) return

        report(expression, "$callee(...)")
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        // `WindowInsets.safeDrawing`, `WindowInsets.ime`, … — the receiver must be the bare type
        // name, so a different type that merely starts with it (`WindowInsetsRulers`) is untouched.
        val receiver = expression.receiverExpression
        if (receiver !is KtNameReferenceExpression || receiver.getReferencedName() != "WindowInsets") return

        report(expression, "WindowInsets.${expression.selectorExpression?.text.orEmpty()}")
    }

    private fun report(expression: KtElement, what: String) {
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "$what found outside :core:design-system. A pane applies Modifier.paneInsets() once " +
                    "and uses no other inset API (spec §2.3.6).",
            ),
        )
    }

    private companion object {
        /** Inset-padding + inset-size modifiers, and explicit consumption. */
        private val INSET_FUNCTIONS = setOf(
            "safeDrawingPadding",
            "safeContentPadding",
            "safeGesturesPadding",
            "imePadding",
            "systemBarsPadding",
            "navigationBarsPadding",
            "statusBarsPadding",
            "displayCutoutPadding",
            "captionBarPadding",
            "windowInsetsPadding",
            "windowInsetsTopHeight",
            "windowInsetsBottomHeight",
            "windowInsetsStartWidth",
            "windowInsetsEndWidth",
            "consumeWindowInsets",
        )
    }
}
