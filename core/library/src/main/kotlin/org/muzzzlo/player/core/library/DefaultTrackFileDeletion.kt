package org.muzzzlo.player.core.library

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.DeleteMethod
import org.muzzzlo.player.core.model.DeleteRequest
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.SystemDialogHandle
import org.muzzzlo.player.core.model.TrackFileDeletion
import org.muzzzlo.player.core.model.TrashRules
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

/** The system dialog of a delete: the app module launches [intentSender] for a result. */
class IntentSenderHandle(val intentSender: IntentSender) : SystemDialogHandle

/**
 * [TrackFileDeletion] on MediaStore (task 2.0.6). Android 11+ moves the file to the system trash through
 * `createTrashRequest`; Android 10 deletes it after the dialog of `RecoverableSecurityException`; Android 8–9 deletes
 * it with the write permission, together with the `.lrc` file of the same name next to it. On Android 10 and newer a
 * lyrics file is not touched: with the trash it must wait for the audio file to come back, and on Android 10 the app
 * has no access to files that are not media.
 */
internal class DefaultTrackFileDeletion
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MuzzzloDatabase,
        private val dao: LibraryDao,
        private val covers: DefaultCoverRepository,
        private val dispatchers: DispatcherProvider,
    ) : TrackFileDeletion {
        override val method: DeleteMethod = DeleteMethod.forSdk(Build.VERSION.SDK_INT)

        override suspend fun request(trackId: Long): DeleteRequest =
            withContext(dispatchers.io) {
                val uri = uriOf(trackId) ?: return@withContext DeleteRequest.Gone
                when (method) {
                    DeleteMethod.TRASH -> trashRequest(uri)
                    DeleteMethod.CONSENT_DIALOG -> consentRequest(uri)
                    DeleteMethod.WRITE_PERMISSION ->
                        if (canWrite()) {
                            DeleteRequest.NotNeeded
                        } else {
                            DeleteRequest.RuntimePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                }
            }

        override suspend fun delete(trackId: Long): Boolean =
            withContext(dispatchers.io) {
                if (method == DeleteMethod.TRASH) return@withContext true
                val uri = uriOf(trackId) ?: return@withContext true
                val lyrics = if (method == DeleteMethod.WRITE_PERMISSION) lyricsFileOf(uri) else null
                val removed = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
                // On Android 10 a file of the app itself went already when the request was made.
                val gone = removed || !exists(uri)
                if (gone) lyrics?.delete()
                gone
            }

        override suspend fun markDeleted(trackId: Long) =
            withContext(dispatchers.io) {
                covers.forgetTrack(trackId)
                dao.markDeleted(trackId, System.currentTimeMillis())
            }

        override suspend fun purgeExpired(nowMs: Long) =
            withContext(dispatchers.io) {
                database.withTransaction {
                    if (dao.deleteExpiredTracks(TrashRules.cutoff(nowMs)) > 0) {
                        dao.deleteEmptyAlbums()
                        dao.deleteOrphanArtists()
                    }
                }
            }

        private suspend fun uriOf(trackId: Long): Uri? =
            dao.tracksByIds(listOf(trackId)).singleOrNull()?.uri?.let(Uri::parse)

        @RequiresApi(Build.VERSION_CODES.R)
        private fun trashRequest(uri: Uri): DeleteRequest {
            val pending = MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true)
            return DeleteRequest.SystemDialog(IntentSenderHandle(pending.intentSender))
        }

        /**
         * Android 10 tells whether the user must agree only by throwing on the delete; a file of this app goes at
         * once, and the following [delete] finds nothing left.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        @Suppress("SwallowedException")
        private fun consentRequest(uri: Uri): DeleteRequest =
            try {
                context.contentResolver.delete(uri, null, null)
                DeleteRequest.NotNeeded
            } catch (e: RecoverableSecurityException) {
                DeleteRequest.SystemDialog(IntentSenderHandle(e.userAction.actionIntent.intentSender))
            } catch (e: FileNotFoundException) {
                DeleteRequest.Gone
            }

        private fun exists(uri: Uri): Boolean =
            runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                    it.moveToFirst()
                }
            }.getOrNull() ?: false

        private fun canWrite(): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        /** Android 8–9 still gives the path of a media file, so the lyrics file next to it can be found. */
        @Suppress("DEPRECATION")
        private fun lyricsFileOf(uri: Uri): File? {
            val path =
                runCatching {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: return null
            val audio = File(path)
            return File(audio.parentFile, TrashRules.lyricsFileName(audio.name)).takeIf { it.isFile }
        }
    }
