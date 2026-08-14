package com.stax.feature.compounds.presentation.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.feature.compounds.presentation.R
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compounds list screen across the §6.4.8 breakpoint profiles (§10.5): Compact (Pixel 10 portrait),
 * Medium (Fold inner portrait) and Expanded (Pixel 10 landscape).
 *
 * The screen is the list pane of the Compounds list-detail Scene (§6.4.2), so it keeps the same
 * one-per-line rows at every width — what the breakpoint moves is the FAB (§6.4.6), which floats
 * bottom-end at Compact and sits in the rail slot, collapsed to its icon, at Medium and Expanded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundsListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<CompoundsListAction>()

    @Test
    @Config(qualifiers = COMPACT)
    fun `the list renders its app bar, chips and rows at Compact`() {
        setScreen(state())

        assertListIsRendered()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the list renders its app bar, chips and rows at Medium`() {
        setScreen(state())

        assertListIsRendered()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the list renders its app bar, chips and rows at Expanded`() {
        setScreen(state())

        assertListIsRendered()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the Add FAB floats bottom-end and keeps its label at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compounds_add)).assertIsDisplayed()
        val fab = composeRule.onNodeWithContentDescription(string(R.string.compounds_add))
            .getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertThat(fab.left.value).isGreaterThan(root.width.value / 2)
        assertThat(fab.top.value).isGreaterThan(root.height.value / 2)
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the Add FAB moves into the rail slot and collapses at Medium`() {
        setScreen(state())

        assertFabIsCollapsedInTheRailSlot()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the Add FAB moves into the rail slot and collapses at Expanded`() {
        setScreen(state())

        assertFabIsCollapsedInTheRailSlot()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a status chip files its filter action`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compounds_filter_low_stock)).performClick()

        assertThat(actions).containsExactly(
            CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.LOW_STOCK),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping the Category chip opens its menu`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compounds_filter_category)).performClick()

        assertThat(actions).containsExactly(
            CompoundsListAction.OnFilterMenuOpen(CompoundFilterMenu.CATEGORY),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an open Category menu lists every category and toggles the tapped one`() {
        setScreen(state(openFilterMenu = CompoundFilterMenu.CATEGORY))

        composeRule.onNodeWithText(string(R.string.compounds_category_medication)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compounds_category_hormone)).performClick()

        assertThat(actions).containsExactly(
            CompoundsListAction.OnCategoryToggle(CompoundCategory.HORMONE),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the Category chip counts its selection`() {
        setScreen(
            state(selectedCategories = persistentSetOf(CompoundCategory.PEPTIDE, CompoundCategory.HORMONE)),
        )

        val label = string(R.string.compounds_filter_category)
        composeRule.onNodeWithText(string(R.string.compounds_filter_selected_count, label, 2)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a row opens its detail`() {
        setScreen(state())

        composeRule.onNodeWithText("Semaglutide").performClick()

        assertThat(actions).containsExactly(CompoundsListAction.OnCompoundClick(compoundId = 1))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the app bar search icon opens the overlay`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription(string(R.string.compounds_search)).performClick()

        assertThat(actions).containsExactly(CompoundsListAction.OnSearchClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the search overlay replaces the list with its results`() {
        setScreen(state(isSearchOpen = true, searchQuery = "sema", items = listOf(SEMAGLUTIDE)))

        composeRule.onNodeWithText("Semaglutide").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compounds_filter_all)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.compounds_add)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the search overlay replaces the list at Expanded too`() {
        setScreen(state(isSearchOpen = true, searchQuery = "sema", items = listOf(SEMAGLUTIDE)))

        composeRule.onNodeWithText("Semaglutide").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compounds_filter_all)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a search with no result shows the empty state`() {
        setScreen(state(isSearchOpen = true, searchQuery = "zzz", items = emptyList()))

        composeRule.onNodeWithText(string(R.string.compounds_search_empty_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the overlay back arrow closes it and its trailing icon clears the query`() {
        setScreen(state(isSearchOpen = true, searchQuery = "sema", items = listOf(SEMAGLUTIDE)))

        composeRule.onNodeWithContentDescription(string(R.string.compounds_search_clear)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.compounds_search_close)).performClick()

        assertThat(actions).containsExactly(
            CompoundsListAction.OnSearchQueryChange(""),
            CompoundsListAction.OnSearchDismiss,
        )
    }

    private fun assertListIsRendered() {
        composeRule.onNodeWithText(string(R.string.compounds_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.compounds_search)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compounds_filter_all)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compounds_filter_low_stock)).assertIsDisplayed()
        composeRule.onNodeWithText("Semaglutide").assertIsDisplayed()
        composeRule.onNodeWithText("Vitamin D3").assertIsDisplayed()
        assertThat(actions).isEmpty()
    }

    /** §6.4.6: the rail FAB slot is the pane's top-start corner, and it has no room for a label. */
    private fun assertFabIsCollapsedInTheRailSlot() {
        composeRule.onNodeWithText(string(R.string.compounds_add)).assertDoesNotExist()
        val fab = composeRule.onNodeWithContentDescription(string(R.string.compounds_add))
            .getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertThat(fab.right.value).isLessThan(root.width.value / 2)
        assertThat(fab.bottom.value).isLessThan(root.height.value / 2)
    }

    private fun setScreen(state: CompoundsListState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                CompoundsListScreen(state = state, onAction = { actions += it })
            }
        }
    }

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private fun state(
        items: List<CompoundListItemUi> = listOf(SEMAGLUTIDE, VITAMIN_D3),
        selectedCategories: ImmutableSet<CompoundCategory> = persistentSetOf(),
        openFilterMenu: CompoundFilterMenu? = null,
        isSearchOpen: Boolean = false,
        searchQuery: String = "",
    ) = CompoundsListState(
        items = items.toPersistentList(),
        selectedCategories = selectedCategories,
        openFilterMenu = openFilterMenu,
        isSearchOpen = isSearchOpen,
        searchQuery = searchQuery,
        isLoading = false,
    )

    private companion object {
        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape — Expanded (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"

        val SEMAGLUTIDE = CompoundListItemUi(
            id = 1,
            name = "Semaglutide",
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            isLowStock = false,
            isExpiringSoon = false,
            dosesLeft = 12,
            remaining = "4.5 mg",
            sealedContainers = 1,
            effectiveExpiry = LocalDate.parse("2026-07-14"),
        )

        val VITAMIN_D3 = CompoundListItemUi(
            id = 2,
            name = "Vitamin D3",
            category = CompoundCategory.SUPPLEMENT,
            form = CompoundForm.CAPSULE,
            isLowStock = false,
            isExpiringSoon = false,
            dosesLeft = null,
            remaining = null,
            sealedContainers = 2,
            effectiveExpiry = LocalDate.parse("2027-03-27"),
        )
    }
}
