package com.stax.feature.compounds.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Compounds list — top-level destination (Home › Compounds). */
@Serializable
data object CompoundsRoute : NavKey

/** Compound detail for the compound identified by [compoundId]. */
@Serializable
data class CompoundDetailRoute(val compoundId: Long) : NavKey

/** Create-compound form. */
@Serializable
data object CreateCompoundRoute : NavKey

/** Edit the compound identified by [compoundId]. */
@Serializable
data class EditCompoundRoute(val compoundId: Long) : NavKey
