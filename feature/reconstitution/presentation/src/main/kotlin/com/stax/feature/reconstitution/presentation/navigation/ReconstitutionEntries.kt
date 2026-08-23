package com.stax.feature.reconstitution.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.reconstitution.presentation.ReconstitutionArgs
import com.stax.feature.reconstitution.presentation.ReconstitutionRoot

/**
 * Contributes the Reconstitution `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * The optional `compoundId` argument reaches the ViewModel through the typed [NavKey] passed to the
 * entry (never `toRoute<T>()`); back navigation is the [onBack] lambda supplied by `:app`
 * (spec §10.3).
 */
fun EntryProviderScope<NavKey>.reconstitutionEntries(onBack: () -> Unit) {
    entry<ReconstitutionRoute> { key ->
        ReconstitutionRoot(args = ReconstitutionArgs(compoundId = key.compoundId), onBack = onBack)
    }
}
