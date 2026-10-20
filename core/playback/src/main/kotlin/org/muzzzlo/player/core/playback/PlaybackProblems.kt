package org.muzzzlo.player.core.playback

import androidx.media3.common.PlaybackException
import org.muzzzlo.player.core.model.PlaybackProblem

/** Maps Media3 error codes to what the user can be told. */
internal object PlaybackProblems {
    fun of(errorCode: Int): PlaybackProblem =
        when (errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> PlaybackProblem.FILE_MISSING

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> PlaybackProblem.UNSUPPORTED

            else -> PlaybackProblem.OTHER
        }
}
