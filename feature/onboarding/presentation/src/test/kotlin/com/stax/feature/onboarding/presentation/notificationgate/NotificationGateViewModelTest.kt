package com.stax.feature.onboarding.presentation.notificationgate

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
class NotificationGateViewModelTest {

    private lateinit var viewModel: NotificationGateViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = NotificationGateViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts without the settings path`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem()).isEqualTo(NotificationGateState(showOpenSettings = false))
        }
    }

    @Test
    fun `Allow asks to request the permission`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationGateAction.OnAllowClick)

            assertThat(awaitItem()).isEqualTo(NotificationGateEvent.RequestPermission)
        }
    }

    @Test
    fun `Open settings asks to open app settings`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationGateAction.OnOpenSettingsClick)

            assertThat(awaitItem()).isEqualTo(NotificationGateEvent.OpenAppSettings)
        }
    }

    @Test
    fun `Continue proceeds without the permission`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationGateAction.OnContinueClick)

            assertThat(awaitItem()).isEqualTo(NotificationGateEvent.Proceed)
        }
    }

    @Test
    fun `granting proceeds`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationGateAction.OnPermissionResult(granted = true, permanentlyDenied = false))

            assertThat(awaitItem()).isEqualTo(NotificationGateEvent.Proceed)
        }
    }

    @Test
    fun `permanent denial reveals the settings path without proceeding`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationGateAction.OnPermissionResult(granted = false, permanentlyDenied = true))

            expectNoEvents()
        }
        assertThat(viewModel.state.value.showOpenSettings).isTrue()
    }

    @Test
    fun `a denial that can be retried keeps the settings path hidden`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationGateAction.OnPermissionResult(granted = false, permanentlyDenied = false))

            expectNoEvents()
        }
        assertThat(viewModel.state.value.showOpenSettings).isFalse()
    }
}
