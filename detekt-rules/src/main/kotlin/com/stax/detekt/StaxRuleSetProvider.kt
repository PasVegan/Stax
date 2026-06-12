package com.stax.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers Stax's custom detekt rules under the `stax` ruleset id. Discovered at runtime via the
 * `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider` service file.
 */
class StaxRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "stax"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(NoCrossFeatureRouteImport(config)))
}
