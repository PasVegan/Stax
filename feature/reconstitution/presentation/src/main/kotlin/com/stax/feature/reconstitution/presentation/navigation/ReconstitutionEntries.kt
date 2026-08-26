package com.stax.feature.reconstitution.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.core.domain.Concentration
import com.stax.feature.reconstitution.presentation.ReconstitutionArgs
import com.stax.feature.reconstitution.presentation.ReconstitutionRoot

/**
 * Contributes the Reconstitution `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * The optional `compoundId` argument reaches the ViewModel through the typed [NavKey] passed to the
 * entry (never `toRoute<T>()`); back navigation is the [onBack] lambda supplied by `:app`
 * (spec §10.3).
 *
 * [onSaved] is §4.6.7's "return to caller": the helper has written the mix onto the compound and
 * hands it up, and `:app` is what knows which screen was waiting for it — the Create / Edit Compound
 * form, whose concentration field it fills (§4.4.3). It replaces the back-pop rather than following
 * one, so a caller that only pops still gets a screen that leaves.
 */
fun EntryProviderScope<NavKey>.reconstitutionEntries(onBack: () -> Unit, onSaved: (Concentration) -> Unit) {
    entry<ReconstitutionRoute> { key ->
        ReconstitutionRoot(
            args = ReconstitutionArgs(compoundId = key.compoundId),
            onBack = onBack,
            onSaved = onSaved,
        )
    }
}
