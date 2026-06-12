package com.stax.app

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.stax.feature.compounds.presentation.navigation.CompoundDetailRoute
import com.stax.feature.compounds.presentation.navigation.CompoundsRoute
import com.stax.feature.dashboard.presentation.navigation.DashboardRoute
import org.junit.jupiter.api.Test

/**
 * Covers the per-destination back-stack behaviour (M5-03, §6.2 / §6.4.5): switching destinations,
 * re-tapping to pop to root, and "exit through home" back handling. Process-death persistence is
 * provided by `rememberNavigationState`'s saveables and is exercised by instrumented tests.
 */
class MainNavigationStateTest {

    private fun newState(): MainNavigationState {
        val backStacks = linkedMapOf<NavKey, NavBackStack<NavKey>>(
            DashboardRoute to NavBackStack(DashboardRoute),
            CompoundsRoute to NavBackStack(CompoundsRoute),
        )
        return MainNavigationState(
            startRoute = DashboardRoute,
            topLevelRoute = mutableStateOf(DashboardRoute),
            backStacks = backStacks,
        )
    }

    @Test
    fun `starts on the home destination`() {
        assertThat(newState().topLevelRoute).isEqualTo(DashboardRoute)
    }

    @Test
    fun `selecting another destination switches to it`() {
        val state = newState()

        state.onTopLevelSelected(CompoundsRoute)

        assertThat(state.topLevelRoute).isEqualTo(CompoundsRoute)
    }

    @Test
    fun `re-tapping the active destination pops its stack to root`() {
        val state = newState()
        state.onTopLevelSelected(CompoundsRoute)
        state.push(CompoundDetailRoute(compoundId = 1L))
        assertThat(state.backStacks.getValue(CompoundsRoute)).hasSize(2)

        state.onTopLevelSelected(CompoundsRoute)

        assertThat(state.backStacks.getValue(CompoundsRoute)).containsExactly(CompoundsRoute)
    }

    @Test
    fun `re-tapping at root is a no-op`() {
        val state = newState()
        state.onTopLevelSelected(CompoundsRoute)

        state.onTopLevelSelected(CompoundsRoute)

        assertThat(state.backStacks.getValue(CompoundsRoute)).containsExactly(CompoundsRoute)
    }

    @Test
    fun `back pops the active stack before changing destination`() {
        val state = newState()
        state.onTopLevelSelected(CompoundsRoute)
        state.push(CompoundDetailRoute(compoundId = 1L))

        state.goBack()

        assertThat(state.topLevelRoute).isEqualTo(CompoundsRoute)
        assertThat(state.backStacks.getValue(CompoundsRoute)).containsExactly(CompoundsRoute)
    }

    @Test
    fun `back from a non-home root returns to home`() {
        val state = newState()
        state.onTopLevelSelected(CompoundsRoute)

        state.goBack()

        assertThat(state.topLevelRoute).isEqualTo(DashboardRoute)
    }

    @Test
    fun `switching destinations retains the inactive stack's state`() {
        val state = newState()
        state.onTopLevelSelected(CompoundsRoute)
        state.push(CompoundDetailRoute(compoundId = 7L))

        state.onTopLevelSelected(DashboardRoute)
        state.onTopLevelSelected(CompoundsRoute)

        assertThat(state.backStacks.getValue(CompoundsRoute)).hasSize(2)
    }
}
