package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.LibraryFolder
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.QuarantinedFile
import org.muzzzlo.player.core.model.SettingsRepository
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelTest {
    private val stored = MutableStateFlow(AppSettings())
    private val repository = folderSettings(stored)
    private var scans = 0
    private val viewModel by lazy { FoldersViewModel(repository, countingLibrary(), Folders) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun item(path: String) = viewModel.state.value.folders.single { it.folder.relativePath == path }

    @Test
    fun `the default mode excludes a folder and rescans`() =
        runBlocking {
            viewModel.onFolderToggled(LibraryFolder("Music/Rock/", 3), included = false)

            assertEquals(setOf("Music/Rock/"), repository.settings.first().excludedFolders)
            assertFalse(item("Music/Rock/").included)
            assertTrue(item("Music/").included)
            assertEquals(1, scans)
        }

    @Test
    fun `turning the mode on with nothing selected says every folder is still scanned`() =
        runBlocking {
            viewModel.onOnlySelectedChanged(true)

            val state = viewModel.state.value
            assertTrue(state.onlySelected)
            assertTrue(state.selectionEmpty)
            assertTrue(state.folders.none { it.included })
            assertEquals(1, scans)
        }

    @Test
    fun `a selected folder takes its subfolders in, which cannot be left out alone`() =
        runBlocking {
            viewModel.onOnlySelectedChanged(true)
            viewModel.onFolderToggled(LibraryFolder("Music/", 5), included = true)

            assertFalse(viewModel.state.value.selectionEmpty)
            assertTrue(item("Music/").included)
            assertEquals(null, item("Music/").includedWith)
            assertTrue(item("Music/Rock/").included)
            assertEquals("Music/", item("Music/Rock/").includedWith)
            assertFalse(item("Music2/").included)

            viewModel.onFolderToggled(LibraryFolder("Music/Rock/", 3), included = false)
            assertEquals(setOf("Music/"), repository.settings.first().selectedFolders)
            assertEquals(2, scans)
        }

    @Test
    fun `switching modes keeps both lists`() =
        runBlocking {
            viewModel.onFolderToggled(LibraryFolder("Music2/", 1), included = false)
            viewModel.onOnlySelectedChanged(true)
            viewModel.onFolderToggled(LibraryFolder("Music/", 5), included = true)
            viewModel.onOnlySelectedChanged(false)

            val settings = repository.settings.first()
            assertEquals(setOf("Music2/"), settings.excludedFolders)
            assertEquals(setOf("Music/"), settings.selectedFolders)
            assertFalse(item("Music2/").included)
            assertTrue(item("Music/Rock/").included)
        }

    @Test
    fun `a selected folder MediaStore no longer lists stays visible`() =
        runBlocking {
            repository.setFolderSelected("Gone/", selected = true)
            repository.setOnlySelectedFolders(true)

            assertTrue(item("Gone/").included)
            assertEquals(0, item("Gone/").folder.trackCount)
        }

    private object Folders : LibraryMaintenance {
        override suspend fun folders(): List<LibraryFolder> =
            listOf(LibraryFolder("Music/", 5), LibraryFolder("Music/Rock/", 3), LibraryFolder("Music2/", 1))

        override suspend fun quarantined(): List<QuarantinedFile> = emptyList()

        override suspend fun retryQuarantined(uri: String) = Unit
    }

    /** The folder settings over [state]; any other setting fails the test. */
    private fun folderSettings(state: MutableStateFlow<AppSettings>): SettingsRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SettingsRepository::class.java)) { _, method, args ->
            val current = state.value

            fun Set<String>.with(
                path: Any?,
                add: Any?,
            ) = if (add == true) this + (path as String) else this - (path as String)
            when (method.name) {
                "getSettings" -> return@newProxyInstance state
                "setFolderIncluded" ->
                    state.value = current.copy(excludedFolders = current.excludedFolders.with(args[0], args[1] != true))
                "setFolderSelected" ->
                    state.value = current.copy(selectedFolders = current.selectedFolders.with(args[0], args[1]))
                "setOnlySelectedFolders" -> state.value = current.copy(onlySelectedFolders = args[0] as Boolean)
                else -> error("unexpected call to ${method.name}")
            }
            Unit
        } as SettingsRepository

    /** Only [LibraryRepository.scan] is expected; it is counted. */
    private fun countingLibrary(): LibraryRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(LibraryRepository::class.java)) { _, method, _ ->
            check(method.name == "scan") { "unexpected call to ${method.name}" }
            scans++
            Unit
        } as LibraryRepository
}
