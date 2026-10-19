package org.muzzzlo.player.feature.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CompoundNames
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CompoundRule

@OptIn(ExperimentalCoroutinesApi::class)
class CompoundNamesViewModelTest {
    private val own = CompoundRule(1, "Tyler, The Creator", null, enabled = true)
    private val builtIn = CompoundRule(2, "Earth, Wind & Fire", "earth", enabled = true)
    private val off = CompoundRule(3, "Peter, Paul and Mary", "peter", enabled = false)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repository: FakeRules): CompoundNamesViewModel {
        val viewModel = CompoundNamesViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `the rules of the repository are listed`() =
        runTest {
            val viewModel = viewModel(FakeRules(listOf(own, builtIn, off)))
            assertEquals(listOf(own, builtIn, off), viewModel.state.value.rules)
            assertEquals(true, viewModel.state.value.isLoaded)
        }

    @Test
    fun `a name without a comma is refused and a good one is added and clears the field`() =
        runTest {
            val repository = FakeRules(emptyList())
            val viewModel = viewModel(repository)

            viewModel.onInputChanged("Tyler The Creator")
            viewModel.onAddClicked()
            assertEquals(CompoundInputError.TooShort, viewModel.state.value.inputError)
            assertEquals(emptyList<String>(), repository.remembered)

            viewModel.onInputChanged(" Tyler, The Creator ")
            assertNull(viewModel.state.value.inputError)
            viewModel.onAddClicked()
            assertEquals(listOf("Tyler, The Creator"), repository.remembered)
            assertEquals("", viewModel.state.value.input)
        }

    @Test
    fun `a name that is already a rule is reported and stays in the field`() =
        runTest {
            val repository = FakeRules(emptyList(), rememberResult = -1)
            val viewModel = viewModel(repository)
            viewModel.onInputChanged("A, B")

            viewModel.onAddClicked()

            assertEquals(CompoundInputError.Duplicate, viewModel.state.value.inputError)
            assertEquals("A, B", viewModel.state.value.input)
        }

    @Test
    fun `removing a rule that splits tracks asks first and removes only after the yes`() =
        runTest {
            val repository = FakeRules(listOf(own), affected = 4)
            val viewModel = viewModel(repository)

            viewModel.onRemoveClicked(own)
            assertEquals(RemoveConfirm(own, 4), viewModel.state.value.confirm)
            assertEquals(emptyList<Long>(), repository.removed)

            viewModel.onRemoveConfirmed()
            assertEquals(listOf(1L), repository.removed)
            assertNull(viewModel.state.value.confirm)
        }

    @Test
    fun `declining the warning removes nothing`() =
        runTest {
            val repository = FakeRules(listOf(own), affected = 2)
            val viewModel = viewModel(repository)
            viewModel.onRemoveClicked(own)

            viewModel.onRemoveDismissed()

            assertNull(viewModel.state.value.confirm)
            assertEquals(emptyList<Long>(), repository.removed)
        }

    @Test
    fun `a rule that touches no track is removed at once and a switched off one is enabled`() =
        runTest {
            val repository = FakeRules(listOf(own, off), affected = 0)
            val viewModel = viewModel(repository)

            viewModel.onRemoveClicked(own)
            assertNull(viewModel.state.value.confirm)
            assertEquals(listOf(1L), repository.removed)

            viewModel.onEnableClicked(off)
            assertEquals(listOf(3L), repository.enabled)
        }

    private class FakeRules(
        rules: List<CompoundRule>,
        private val affected: Int = 0,
        private val rememberResult: Int = 0,
    ) : CompoundRepository {
        private val rules = MutableStateFlow(rules)
        val remembered = mutableListOf<String>()
        val removed = mutableListOf<Long>()
        val enabled = mutableListOf<Long>()

        override fun observeRules(): Flow<List<CompoundRule>> = rules

        override suspend fun compoundNames() = CompoundNames.None

        override suspend fun syncBuiltIn() = Unit

        override suspend fun remember(name: String): Int {
            if (rememberResult >= 0) remembered += name
            return rememberResult
        }

        override suspend fun remove(id: Long): Int {
            removed += id
            return 0
        }

        override suspend fun countAffectedBy(id: Long) = affected

        override suspend fun enable(id: Long): Int {
            enabled += id
            return 0
        }
    }
}
