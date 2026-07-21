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
 * Flags an inline `tween(...)` animation spec. Motion specs are centralized in `StaxMotion` so the
 * M3 Expressive motion scheme stays consistent app-wide (spec §5.9) — `StaxMotion.kt` itself is
 * exempted via `excludes` in `detekt.yml`.
 */
class NoInlineTween(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoInlineTween",
        severity = Severity.Defect,
        description = "Inline tween(...) is forbidden outside StaxMotion; use a StaxMotion spec (spec §5.9).",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeName() != "tween") return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Inline tween(...) found. Use a StaxMotion spec (e.g. StaxMotion.defaultSpatialSpec()) " +
                    "so motion stays centralized (spec §5.9).",
            ),
        )
    }
}
