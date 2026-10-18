package org.muzzzlo.player.core.tags

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.muzzzlo.player.core.model.DispatcherProvider
import java.io.File

/** Runs everything in place: the tests call native code synchronously anyway. */
internal object TestDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
}

/** A test resource as a file, for example `tags/flac.flac`. */
internal fun resourceFile(name: String): File {
    val url = requireNotNull(TestDispatchers::class.java.classLoader?.getResource(name)) { "$name is missing" }
    return File(url.toURI())
}

/** The raw descriptor behind a JVM FileDescriptor; on Android this comes from ParcelFileDescriptor.getFd(). */
internal fun java.io.FileDescriptor.toIntFd(): Int =
    java.io.FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(this)
