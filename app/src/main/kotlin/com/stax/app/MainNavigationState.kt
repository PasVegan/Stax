package com.stax.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Holds one saveable [NavBackStack] per top-level destination (Nav3 multiple-back-stacks recipe,
 * §6.2 / §6.4.5). Each stack — and the active [topLevelRoute] — survives configuration changes and
 * process death, so switching destinations preserves each section's history and scroll/UI state.
 *
 * Follows the "exit through home" model: [startRoute] is always present, so the user ultimately
 * leaves through Home. At most one other destination's stack is live at a time; an inactive stack
 * keeps its state for when the user returns to it.
 *
 * This holder is mutated only through its own methods; [rememberMainNavigationState] builds it.
 */
class MainNavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    /** The currently selected top-level destination. */
    var topLevelRoute: NavKey by topLevelRoute
        private set

    private val currentStack: NavBackStack<NavKey>
        get() = backStacks.getValue(topLevelRoute)

    /** The screen on top of the active destination's stack — what the user is currently looking at. */
    val currentRoute: NavKey
        get() = currentStack.last()

    /**
     * Handles a tap on a top-level nav item: switches to [route], or — if it is already the active
     * destination — pops its stack back to its root (§6.4.5). Re-tapping at the root is a no-op.
     */
    fun onTopLevelSelected(route: NavKey) {
        if (topLevelRoute == route) {
            backStacks[route]?.let(::popToRoot)
        } else {
            topLevelRoute = route
        }
    }

    /**
     * Lands on [startRoute] with nothing stacked on it, whichever destination is active and however
     * deep its stack is. Used to end a flow rather than to walk back out of it — onboarding
     * completion drops the whole stepper and shows Dashboard (§4.14).
     */
    fun goToStartRoot() {
        // The flow was pushed onto whichever destination was active, so clear that stack as well:
        // leaving it behind would resume a finished flow the next time the user goes back to it.
        popToRoot(currentStack)
        topLevelRoute = startRoute
        popToRoot(currentStack)
    }

    /** Pushes a stacked screen onto the active destination's stack (§6.2). */
    fun push(route: NavKey) {
        currentStack.add(route)
    }

    /**
     * Selects [route] as the active destination's detail pane (§6.4.2). Any existing detail of the
     * same type is replaced rather than stacked, so at Medium+ the detail pane swaps in place
     * without growing the back stack; at Compact it simply pushes the detail.
     */
    fun showDetail(route: NavKey) {
        val stack = currentStack
        stack.removeAll { it::class == route::class }
        stack.add(route)
    }

    /**
     * Handles system / predictive back: pops the active stack, or — at a non-Home root — returns to
     * the Home stack. At the Home root nothing pops (the back press leaves the app).
     */
    fun goBack() {
        val stack = currentStack
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            topLevelRoute != startRoute -> topLevelRoute = startRoute
        }
    }

    /**
     * Decorates the in-use stacks into the flat [NavEntry] list rendered by `NavDisplay`. Each stack
     * owns its own [rememberSaveableStateHolderNavEntryDecorator] so its UI state is retained while
     * the destination is inactive; the ViewModel-store decorator scopes ViewModels per entry.
     */
    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        // Decorate every stack (stable count/order) so inactive stacks keep their state; only the
        // in-use stacks are rendered.
        val decoratedByRoute = LinkedHashMap<NavKey, List<NavEntry<NavKey>>>(backStacks.size)
        for ((route, stack) in backStacks) {
            // Keyed for the same reason as the stacks themselves: the decorators hold saveable state
            // (each entry's UI state) whose registry keys would otherwise collide across iterations.
            decoratedByRoute[route] = key(route) {
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider,
                )
            }
        }
        return routesInUse().flatMap { decoratedByRoute[it].orEmpty() }
    }

    /** Drops everything stacked on [stack]'s root destination. */
    private fun popToRoot(stack: NavBackStack<NavKey>) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Start route first ("exit through home"), then the active route when it differs. */
    private fun routesInUse(): List<NavKey> =
        if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)
}

/**
 * Builds a [MainNavigationState] whose active destination and per-destination back stacks all
 * survive configuration changes and process death. [startRoute] must be one of [topLevelRoutes].
 *
 * [initialStackedRoute], when given, starts [startRoute]'s stack with that route already on top — a
 * flow the user must answer before reaching the start destination (first-run onboarding, §4.14).
 */
@Composable
fun rememberMainNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
    initialStackedRoute: NavKey? = null,
): MainNavigationState {
    // Every saveable here is wrapped in `key(...)`. An unkeyed rememberSaveable/rememberSerializable
    // derives its registry key from the *enclosing composable's* compound hash, not its call site, so
    // all the siblings below — and all five stacks of the loop — would share one key. The registry
    // tolerates that by saving a list per key and popping it positionally on restore, which means one
    // ordering or type mismatch makes every value fall back to its default: the whole nav state
    // silently resets on every configuration change.
    val topLevelRoute = key(TOP_LEVEL_ROUTE_KEY) {
        rememberSerializable(
            startRoute,
            topLevelRoutes,
            serializer = MutableStateSerializer(NavKeySerializer()),
        ) {
            mutableStateOf(startRoute)
        }
    }

    // One saveable back stack per destination, keyed by that destination's route. `initialStackedRoute`
    // is only an *initial* value: a restored stack always wins, so a configuration change or process
    // death part-way through the seeded flow never pushes it a second time.
    val backStacks = topLevelRoutes.associateWith { route ->
        val initialStack = if (route == startRoute) listOfNotNull(route, initialStackedRoute) else listOf(route)
        key(route) { rememberNavBackStack(*initialStack.toTypedArray()) }
    }

    return remember(startRoute, topLevelRoutes) {
        MainNavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks,
        )
    }
}

/** Registry key for the active-destination state (see [rememberMainNavigationState]). */
private const val TOP_LEVEL_ROUTE_KEY = "MainNavigationState.topLevelRoute"
