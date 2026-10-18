package org.muzzzlo.player.core.tags

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** How the screen asks the user to let the app change files, see [TagWritePermission.requestFor]. */
sealed interface WritePermissionRequest {
    /** The app may already change the files. */
    data object NotNeeded : WritePermissionRequest

    /**
     * Android 10+: launch [intentSender] (`registerForActivityResult(StartIntentSenderForResult())`); the system asks
     * the user and, on "Allow", the files can be written. On Android 10 it covers one file: ask again for the rest.
     */
    data class SystemDialog(val intentSender: IntentSender) : WritePermissionRequest

    /** Android 8–9: request the runtime [permission] (`WRITE_EXTERNAL_STORAGE`) the usual way. */
    data class RuntimePermission(val permission: String) : WritePermissionRequest
}

/**
 * Builds the request a screen shows before a tag write when [org.muzzzlo.player.core.model.TagWriter] answers
 * [org.muzzzlo.player.core.model.TagWriteResult.NeedsPermission] (ARCHITECTURE.md, section 5, step 1).
 * The dialog itself belongs to the tag editor screen (task 0.3.6).
 *
 * Calls may open files: call them off the main thread.
 */
class TagWritePermission
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** The request for the MediaStore files at [uris] (`content://` strings). */
        fun requestFor(uris: List<String>): WritePermissionRequest {
            if (uris.isEmpty()) return WritePermissionRequest.NotNeeded
            val parsed = uris.map(Uri::parse)
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    WritePermissionRequest.SystemDialog(
                        MediaStore.createWriteRequest(context.contentResolver, parsed).intentSender,
                    )
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> requestOnAndroid10(parsed)
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED -> WritePermissionRequest.NotNeeded
                else -> WritePermissionRequest.RuntimePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        /** Android 10 tells which file needs the user's consent only by throwing when it is opened for writing. */
        @RequiresApi(Build.VERSION_CODES.Q)
        @Suppress("SwallowedException")
        private fun requestOnAndroid10(uris: List<Uri>): WritePermissionRequest {
            for (uri in uris) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "rw")?.close()
                } catch (e: FileNotFoundException) {
                    // A deleted file needs no permission; the write reports it as unreadable.
                } catch (e: RecoverableSecurityException) {
                    return WritePermissionRequest.SystemDialog(e.userAction.actionIntent.intentSender)
                }
            }
            return WritePermissionRequest.NotNeeded
        }
    }
