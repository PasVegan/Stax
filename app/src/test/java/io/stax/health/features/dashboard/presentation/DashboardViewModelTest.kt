package io.stax.health.features.dashboard.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onAction OnRefreshClick updates isLoading state`() =
        runTest {
            val viewModel = DashboardViewModel()

            viewModel.state.test {
                // Initial state
                val initialState = awaitItem()
                assertThat(initialState.isLoading).isFalse()

                viewModel.onAction(DashboardAction.OnRefreshClick)

                // Loading state
                val loadingState = awaitItem()
                assertThat(loadingState.isLoading).isTrue()

                // After delay (simulated in ViewModel)
                testDispatcher.scheduler.advanceTimeBy(1001)
                val finalState = awaitItem()
                assertThat(finalState.isLoading).isFalse()
            }
        }
}
