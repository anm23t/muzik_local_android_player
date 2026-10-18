package org.muzzzlo.player.feature.tageditor

import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.tags.TagWritePermission
import org.muzzzlo.player.core.tags.WritePermissionRequest
import javax.inject.Inject

/** Builds the request that lets the app change a file; replaced by a fake in tests. */
fun interface WritePermissions {
    /** The request for the MediaStore file at [uri] (`content://`). */
    suspend fun requestFor(uri: String): WritePermissionRequest
}

/** [WritePermissions] on [TagWritePermission], which may open the file and so runs on the I/O dispatcher. */
internal class SystemWritePermissions
    @Inject
    constructor(
        private val permission: TagWritePermission,
        private val dispatchers: DispatcherProvider,
    ) : WritePermissions {
        override suspend fun requestFor(uri: String): WritePermissionRequest =
            withContext(dispatchers.io) { permission.requestFor(listOf(uri)) }
    }
