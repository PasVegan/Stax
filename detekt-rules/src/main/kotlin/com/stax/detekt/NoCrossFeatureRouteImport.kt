package com.stax.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Fails when a `:feature:<x>:presentation` source file imports a symbol from a different feature's
 * package (most importantly another feature's `Routes.kt`). Features never depend on each other;
 * cross-feature navigation is expressed as lambda callbacks wired in `:app` (spec §10.3).
 *
 * The Gradle module graph already forbids feature → feature dependencies
 * (`checkForbiddenModuleDependencies`); this rule is the static-analysis backstop the M5-01
 * acceptance criteria require, and it pinpoints the offending import line.
 */
class NoCrossFeatureRouteImport(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoCrossFeatureRouteImport",
        severity = Severity.Defect,
        description = "A feature presentation module must not import another feature's routes; " +
            "cross-feature navigation is wired as lambda callbacks in :app (spec §10.3).",
        debt = Debt.TEN_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val imported = importDirective.importedFqName?.asString() ?: return
        val filePackage = importDirective.containingKtFile.packageFqName.asString()

        val fileFeature = featureOf(filePackage) ?: return
        val importedFeature = featureOf(imported) ?: return

        if (fileFeature != importedFeature) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(importDirective),
                    "Feature '$fileFeature' imports '$imported' from feature '$importedFeature'. " +
                        "Route this through a lambda callback passed from :app instead (spec §10.3).",
                ),
            )
        }
    }

    private fun featureOf(fqName: String): String? =
        FEATURE_PACKAGE.find(fqName)?.groupValues?.get(1)

    private companion object {
        /** Captures `<x>` in `com.stax.feature.<x>.…`. */
        private val FEATURE_PACKAGE = Regex("""^com\.stax\.feature\.([^.]+)\.""")
    }
}
