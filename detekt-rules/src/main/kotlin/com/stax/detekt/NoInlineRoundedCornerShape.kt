package com.stax.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags an inline `RoundedCornerShape(...)`. Shapes come from the M3 Expressive shape scale —
 * `MaterialTheme.shapes.<slot>` or `StaxShapes.Pill` (spec §9). `:core:design-system` owns shape
 * construction and is exempted via `excludes` in `detekt.yml`.
 */
class NoInlineRoundedCornerShape(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoInlineRoundedCornerShape",
        severity = Severity.Defect,
        description = "Inline RoundedCornerShape(...) is forbidden outside :core:design-system; " +
            "use a shape token (spec §9).",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeName() != "RoundedCornerShape") return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Inline RoundedCornerShape(...) found. Use MaterialTheme.shapes.<slot> or " +
                    "StaxShapes.Pill (spec §9).",
            ),
        )
    }
}
