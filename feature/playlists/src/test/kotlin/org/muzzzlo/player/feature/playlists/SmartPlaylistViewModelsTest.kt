package org.muzzzlo.player.feature.playlists

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.RuleCondition
import org.muzzzlo.player.core.model.RuleField
import org.muzzzlo.player.core.model.RuleGroup
import org.muzzzlo.player.core.model.RuleOperator
import org.muzzzlo.player.core.model.SmartPlaylist
import org.muzzzlo.player.core.model.SmartRules
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackFacts

@OptIn(ExperimentalCoroutinesApi::class)
class SmartPlaylistViewModelsTest {
    private val a = track(1, "Song A", "Bjork", album = "Post", genres = listOf("Electronic"))
    private val b = track(2, "Song B", "Bjork; Thom", album = "Post", genres = listOf("electronic", "Art pop"))
    private val c = track(3, "Song C", "Thom", album = null, genres = emptyList())
    private val smart = FakeSmartPlaylists().apply { facts.value = listOf(a, b, c) }
    private val player = FakePlayerController()

    private val bjorkRules =
        SmartRules(
            groups =
                listOf(
                    RuleGroup(conditions = listOf(RuleCondition(RuleField.ARTIST, RuleOperator.IS, text = "Bjork"))),
                ),
        )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun track(
        id: Long,
        title: String,
        artists: String,
        album: String?,
        genres: List<String>,
    ) = TrackFacts(
        track = Track(id, "content://$id", title, artists.split("; "), album?.let { id }, album, 1000, null),
        titleKey = title.lowercase(),
        genres = genres,
    )

    private fun TestScope.editor(id: Long = 0): SmartPlaylistEditorViewModel {
        val viewModel = SmartPlaylistEditorViewModel(smart, smart, SavedStateHandle(mapOf(SMART_PLAYLIST_ID_ARG to id)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.card(id: Long): SmartPlaylistViewModel {
        val savedState = SavedStateHandle(mapOf(SMART_PLAYLIST_ID_ARG to id))
        val viewModel =
            SmartPlaylistViewModel(
                smart,
                FakePlaylists(emptyMap()),
                FakeLibrary(),
                player,
                FakeTrackMarks(),
                savedState,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun SmartPlaylistEditorViewModel.firstCondition(): Pair<Long, Long> {
        val group = state.value.draft.groups.first()
        return group.id to group.conditions.first().id
    }

    @Test
    fun `a new draft has one empty condition and offers the names of the library`() =
        runTest {
            val viewModel = editor()
            val state = viewModel.state.value

            assertTrue(state.isNew)
            assertTrue(state.isLoaded)
            assertEquals(1, state.draft.groups.single().conditions.size)
            assertEquals(listOf("Bjork", "Thom"), state.choices.artists)
            assertEquals(listOf("Post"), state.choices.albums)
            assertEquals(listOf("Art pop", "Electronic"), state.choices.genres)
        }

    @Test
    fun `the count follows the draft after a short pause`() =
        runTest {
            val viewModel = editor()
            advanceTimeBy(COUNT_DELAY_MS + 1)
            assertEquals(3, viewModel.state.value.matchingCount)

            val (group, condition) = viewModel.firstCondition()
            viewModel.onEdit(DraftEdit.Value(group, condition, "Bjork"))
            advanceTimeBy(COUNT_DELAY_MS / 2)
            assertEquals(3, viewModel.state.value.matchingCount)
            advanceTimeBy(COUNT_DELAY_MS)
            assertEquals(2, viewModel.state.value.matchingCount)
        }

    @Test
    fun `save asks for a name and a complete condition`() =
        runTest {
            val viewModel = editor()
            viewModel.onSave()

            val state = viewModel.state.value
            assertTrue(state.showErrors)
            assertTrue(state.nameMissing)
            assertTrue(state.conditionsMissing)
            assertNull(state.savedId)
            assertEquals(emptyList<SmartPlaylist>(), smart.playlists.value)

            viewModel.onEdit(DraftEdit.Name("Björk"))
            viewModel.onSave()
            assertNull(viewModel.state.value.savedId)
        }

    @Test
    fun `a new smart playlist is created with the rules of the draft`() =
        runTest {
            val viewModel = editor()
            val (group, condition) = viewModel.firstCondition()
            viewModel.onEdit(DraftEdit.Name("  Björk  "))
            viewModel.onEdit(DraftEdit.Value(group, condition, "Bjork"))
            viewModel.onSave()

            val saved = smart.playlists.value.single()
            assertEquals(saved.id, viewModel.state.value.savedId)
            assertEquals("Björk", saved.name)
            assertEquals(bjorkRules, saved.rules)
        }

    @Test
    fun `an existing playlist opens in the editor and is updated in place`() =
        runTest {
            val id = smart.create("Björk", bjorkRules)
            val viewModel = editor(id)
            val state = viewModel.state.value

            assertFalse(state.isNew)
            assertEquals("Björk", state.draft.name)
            assertEquals("Bjork", state.draft.groups.single().conditions.single().value)

            viewModel.onEdit(DraftEdit.Name("Mostly Björk"))
            viewModel.onEdit(DraftEdit.Limit("1"))
            viewModel.onSave()

            val saved = smart.playlists.value.single()
            assertEquals(id, viewModel.state.value.savedId)
            assertEquals("Mostly Björk", saved.name)
            assertEquals(bjorkRules.copy(limit = 1), saved.rules)
        }

    @Test
    fun `the card shows the tracks of the rules, plays them and is deleted`() =
        runTest {
            val id = smart.create("Björk", bjorkRules)
            val viewModel = card(id)

            assertEquals(listOf(a.track, b.track), viewModel.state.value.tracks)
            assertEquals(CoverGradients.forSmart(id), viewModel.state.value.gradient)
            viewModel.onTrackClicked(1)
            assertEquals(listOf(listOf(a.track, b.track) to 1), player.played)

            viewModel.onDelete()
            assertTrue(viewModel.state.value.isDeleted)
            assertNull(viewModel.state.value.playlist)
            assertEquals(emptyList<SmartPlaylist>(), smart.playlists.value)
        }

    @Test
    fun `the list shows smart playlists with their counts`() =
        runTest {
            val id = smart.create("Björk", bjorkRules)
            val viewModel =
                PlaylistListViewModel(FakePlaylists(emptyMap()), FakeAutoPlaylists(), FakePlaylistTransfer(), smart)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(listOf(SmartPlaylistSummary(id, "Björk", 2)), viewModel.state.value.smart)
        }
}
