package com.stax.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Flags a raw `Color(0xFF…)` literal. Colors are read from `MaterialTheme.colorScheme.<role>` or a
 * `StaxColors` semantic token (spec §9); `Tokens.kt` is the single legal home for the scheme seeds
 * and is exempted via `excludes` in `detekt.yml`.
 *
 * Only hex-literal arguments are flagged — `Color(red, green, blue)` and `Color(someArgbInt)` are
 * computed values, not hardcoded design decisions.
 */
class NoRawColorLiteral(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoRawColorLiteral",
        severity = Severity.Defect,
        description = "A raw Color(0x…) literal is forbidden outside Tokens.kt; use a colorScheme " +
            "role or a StaxColors token (spec §9).",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.calleeName() != "Color") return
        val firstArgument = expression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
        if (!firstArgument.isHexLiteral()) return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Raw Color(0x…) literal found. Use MaterialTheme.colorScheme.<role> or a StaxColors " +
                    "semantic token (spec §9).",
            ),
        )
    }

    private fun KtExpression.isHexLiteral(): Boolean =
        this is KtConstantExpression && text.startsWith("0x", ignoreCase = true)
}
