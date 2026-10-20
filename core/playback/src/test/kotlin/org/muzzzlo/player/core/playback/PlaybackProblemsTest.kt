package org.muzzzlo.player.core.playback

import androidx.media3.common.PlaybackException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.PlaybackProblem

class PlaybackProblemsTest {
    private val table =
        mapOf(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND to PlaybackProblem.FILE_MISSING,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION to PlaybackProblem.FILE_MISSING,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED to PlaybackProblem.FILE_MISSING,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED to PlaybackProblem.UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to PlaybackProblem.UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED to PlaybackProblem.UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED to PlaybackProblem.OTHER,
            PlaybackException.ERROR_CODE_UNSPECIFIED to PlaybackProblem.OTHER,
        )

    @Test
    fun `error codes map to what the user is told`() =
        table.forEach {
                (code, expected) ->
            assertEquals(expected, PlaybackProblems.of(code), "code $code")
        }
}
