package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AutoBackupRepository
import org.muzzzlo.player.core.model.AutoBackupScheduler
import org.muzzzlo.player.core.model.AutoBackupSettings
import org.muzzzlo.player.core.model.BackupFormatException
import org.muzzzlo.player.core.model.BackupImportResult
import org.muzzzlo.player.core.model.BackupRepository
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val backup = FakeBackup()
    private val files = FakeFiles()
    private val auto = FakeAuto()
    private val scheduler = FakeScheduler()
    private val viewModel by lazy { BackupViewModel(backup, files, auto, scheduler) }
    private val uri = "content://file"

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `export writes the json and reports it`() {
        viewModel.onExportBackup(uri)

        assertEquals("json", files.written)
        assertEquals(BackupMessage.BackupSaved, viewModel.state.value.message)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun `history export writes the csv`() {
        viewModel.onExportHistory(uri)

        assertEquals("csv", files.written)
        assertEquals(BackupMessage.HistorySaved, viewModel.state.value.message)
    }

    @Test
    fun `import hands the file text to the repository and shows the counts`() {
        files.content = "backup text"
        viewModel.onImport(uri)

        assertEquals("backup text", backup.imported)
        assertEquals(BackupMessage.Imported(backup.result), viewModel.state.value.message)
    }

    @Test
    fun `a file that is not a backup is reported`() {
        backup.failWith = BackupFormatException("no")
        viewModel.onImport(uri)

        assertEquals(BackupMessage.NotABackup, viewModel.state.value.message)
    }

    @Test
    fun `an unreadable file is reported`() {
        files.fail = true
        viewModel.onImport(uri)
        assertEquals(BackupMessage.FileError, viewModel.state.value.message)

        viewModel.onMessageDismissed()
        files.fail = true
        viewModel.onExportBackup(uri)
        assertEquals(BackupMessage.FileError, viewModel.state.value.message)
    }

    @Test
    fun `the message goes away when dismissed`() {
        viewModel.onExportBackup(uri)
        viewModel.onMessageDismissed()

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `the switch needs a folder`() {
        viewModel.onAutoBackupChanged(true)

        assertFalse(auto.state.value.enabled)
        assertEquals(emptyList<Boolean>(), scheduler.scheduled)
    }

    @Test
    fun `choosing a folder from the switch turns the weekly copy on`() {
        viewModel.onFolderChosen("content://tree", enable = true)

        assertEquals(AutoBackupSettings(enabled = true, folderUri = "content://tree"), viewModel.state.value.auto)
        assertEquals(listOf(true), scheduler.scheduled)
    }

    @Test
    fun `changing the folder alone keeps the switch as it is`() {
        viewModel.onFolderChosen("content://tree", enable = false)

        assertFalse(viewModel.state.value.auto.enabled)
        assertEquals("content://tree", viewModel.state.value.auto.folderUri)
        assertEquals(emptyList<Boolean>(), scheduler.scheduled)
    }

    @Test
    fun `switching off cancels the schedule`() {
        viewModel.onFolderChosen("content://tree", enable = true)
        viewModel.onAutoBackupChanged(false)

        assertFalse(viewModel.state.value.auto.enabled)
        assertEquals(listOf(true, false), scheduler.scheduled)
    }

    @Test
    fun `back up now needs a folder`() {
        viewModel.onBackUpNow()
        assertEquals(0, scheduler.runs)

        viewModel.onFolderChosen("content://tree", enable = false)
        viewModel.onBackUpNow()
        assertEquals(1, scheduler.runs)
    }

    private class FakeAuto : AutoBackupRepository {
        val state = MutableStateFlow(AutoBackupSettings())
        override val settings = state

        override suspend fun setEnabled(enabled: Boolean) {
            state.value = state.value.copy(enabled = enabled)
        }

        override suspend fun setFolder(uri: String?) {
            state.value = state.value.copy(folderUri = uri)
        }

        override suspend fun recordResult(
            timeMs: Long,
            failed: Boolean,
        ) = Unit
    }

    private class FakeScheduler : AutoBackupScheduler {
        val scheduled = mutableListOf<Boolean>()
        var runs = 0

        override fun schedule(enabled: Boolean) {
            scheduled += enabled
        }

        override fun runNow() {
            runs++
        }
    }

    private class FakeBackup : BackupRepository {
        val result = BackupImportResult(1, 2, 3, 4, 5)
        var imported: String? = null
        var failWith: BackupFormatException? = null

        override suspend fun exportJson() = "json"

        override suspend fun exportEventsCsv() = "csv"

        override suspend fun importJson(text: String): BackupImportResult {
            failWith?.let { throw it }
            imported = text
            return result
        }
    }

    private class FakeFiles : BackupFiles {
        var content = ""
        var written: String? = null
        var fail = false

        override suspend fun read(uri: String): String = if (fail) throw IOException() else content

        override suspend fun write(
            uri: String,
            text: String,
        ) {
            if (fail) throw IOException()
            written = text
        }
    }
}
