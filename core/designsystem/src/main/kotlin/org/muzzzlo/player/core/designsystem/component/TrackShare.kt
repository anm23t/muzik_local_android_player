package org.muzzzlo.player.core.designsystem.component

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.muzzzlo.player.core.designsystem.R

/** Rules for what the system share sheet is given. */
object TrackShare {
    private const val CONTENT_PREFIX = "content://"

    /**
     * The addresses to share, in the given order: only `content://` ones (a `file://` address would crash the
     * receiving app's access and is a leak of paths), without blanks and without repeats.
     */
    fun shareable(uris: List<String>): List<String> =
        uris.map(String::trim).filter { it.startsWith(CONTENT_PREFIX) && it.length > CONTENT_PREFIX.length }.distinct()
}

/**
 * Opens the system share sheet with the audio files at [uris] (`content://` addresses of MediaStore): one file goes
 * with `ACTION_SEND`, several with `ACTION_SEND_MULTIPLE`, as separate files, not as an archive. The receiving app gets
 * read access to the files. Does nothing when no address can be shared.
 */
fun Context.shareTracks(uris: List<String>) {
    val parsed = TrackShare.shareable(uris).map(Uri::parse)
    if (parsed.isEmpty()) return
    val send =
        if (parsed.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, parsed.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsed))
        }
    send.type = AUDIO_MIME_TYPE
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The grant of a share covers the clip data, not the extras, on the older systems.
    send.clipData =
        ClipData.newRawUri(null, parsed.first()).also {
                clip ->
            parsed.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
    val chooser = Intent.createChooser(send, getString(R.string.track_action_share))
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}

private const val AUDIO_MIME_TYPE = "audio/*"
