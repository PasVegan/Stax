package com.stax.feature.onboarding.presentation.welcome

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    private lateinit var viewModel: WelcomeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = WelcomeViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts on step 1 of 3`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem()).isEqualTo(WelcomeState(currentStep = 1, stepCount = 3))
        }
    }

    @Test
    fun `Continue asks to advance the flow`() = runTest {
        viewModel.events.test {
            viewModel.onAction(WelcomeAction.OnContinueClick)

            assertThat(awaitItem()).isEqualTo(WelcomeEvent.NavigateToNextStep)
        }
    }

    @Test
    fun `Skip asks to leave onboarding`() = runTest {
        viewModel.events.test {
            viewModel.onAction(WelcomeAction.OnSkipClick)

            assertThat(awaitItem()).isEqualTo(WelcomeEvent.SkipOnboarding)
        }
    }

    @Test
    fun `neither action changes the state`() = runTest {
        viewModel.state.test {
            val initial = awaitItem()

            viewModel.onAction(WelcomeAction.OnContinueClick)
            viewModel.onAction(WelcomeAction.OnSkipClick)

            expectNoEvents()
            assertThat(viewModel.state.value).isEqualTo(initial)
        }
    }
}
