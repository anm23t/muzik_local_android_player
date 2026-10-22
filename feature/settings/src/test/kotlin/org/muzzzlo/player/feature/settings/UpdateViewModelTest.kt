package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.ReleaseInfo
import org.muzzzlo.player.core.model.ReleaseLookup
import org.muzzzlo.player.core.model.UpdateCheckState
import org.muzzzlo.player.core.model.UpdateChecker

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val release = ReleaseInfo("v1.1", "https://github.com/a/b/releases/tag/v1.1")
    private val source = FakeReleaseSource(ReleaseLookup.Found(release))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        store: FakeUpdateStore,
        version: String = "1.0-start",
    ) = UpdateViewModel(store, UpdateChecker(store, source, version) { NOW })

    @Test
    fun `the first start asks once and does not touch the network`() {
        val store = FakeUpdateStore()
        val viewModel = viewModel(store)

        assertEquals(UpdatePrompt.AskConsent, viewModel.prompt.value)
        assertEquals(0, source.calls)
    }

    @Test
    fun `yes saves the answer and checks at once`() {
        val store = FakeUpdateStore()
        val viewModel = viewModel(store)

        viewModel.onConsent(true)

        assertEquals(true, store.current.value.enabled)
        assertEquals(UpdatePrompt.Available(release), viewModel.prompt.value)
        assertEquals(NOW, store.current.value.lastCheckMs)
    }

    @Test
    fun `no saves the answer and never asks GitHub`() {
        val store = FakeUpdateStore()
        val viewModel = viewModel(store)

        viewModel.onConsent(false)

        assertEquals(false, store.current.value.enabled)
        assertEquals(UpdatePrompt.None, viewModel.prompt.value)
        assertEquals(0, source.calls)
        assertEquals(UpdatePrompt.None, viewModel(store).prompt.value)
        assertEquals(0, source.calls)
    }

    @Test
    fun `after consent a start checks and a closed announcement is not shown again`() {
        val store = FakeUpdateStore(UpdateCheckState(enabled = true))
        val first = viewModel(store)
        assertEquals(UpdatePrompt.Available(release), first.prompt.value)

        first.onAnnouncementClosed()
        assertEquals(UpdatePrompt.None, first.prompt.value)
        assertEquals("v1.1", store.current.value.announcedTag)

        store.current.value = store.current.value.copy(lastCheckMs = null)
        assertEquals(UpdatePrompt.None, viewModel(store).prompt.value)
    }

    @Test
    fun `a start within a day of the last check shows nothing and asks nothing`() {
        val store = FakeUpdateStore(UpdateCheckState(enabled = true, lastCheckMs = NOW - 1))

        assertEquals(UpdatePrompt.None, viewModel(store).prompt.value)
        assertEquals(0, source.calls)
    }

    @Test
    fun `no network shows nothing`() {
        source.lookup = ReleaseLookup.Failed
        val store = FakeUpdateStore(UpdateCheckState(enabled = true))

        assertEquals(UpdatePrompt.None, viewModel(store).prompt.value)
        assertNull(store.current.value.lastCheckMs)
    }

    @Test
    fun `the running version is not announced`() {
        val store = FakeUpdateStore(UpdateCheckState(enabled = true))

        assertEquals(UpdatePrompt.None, viewModel(store, version = "1.1").prompt.value)
    }

    private companion object {
        const val NOW = 10_000_000_000L
    }
}
