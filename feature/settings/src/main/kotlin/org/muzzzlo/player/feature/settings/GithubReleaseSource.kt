package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.GithubRelease
import org.muzzzlo.player.core.model.ReleaseLookup
import org.muzzzlo.player.core.model.ReleaseSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Asks GitHub for the latest release of the project: one HTTPS request for `releases/latest`, which leaves out
 * drafts and pre-releases. It sends nothing about the user, only a fixed `User-Agent`. This is the only network
 * request of the app and it runs only after the user agreed (task 1.1.9).
 */
internal class GithubReleaseSource
    @Inject
    constructor(
        private val dispatchers: DispatcherProvider,
    ) : ReleaseSource {
        override suspend fun latest(): ReleaseLookup =
            withContext(dispatchers.io) {
                try {
                    request()
                } catch (_: IOException) {
                    ReleaseLookup.Failed
                }
            }

        private fun request(): ReleaseLookup {
            val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "MUZZZLO-update-check")
                return when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK ->
                        connection.inputStream.use(::readLimited)
                            ?.let(GithubRelease::parse)
                            ?.let(ReleaseLookup::Found)
                            ?: ReleaseLookup.Failed
                    HttpURLConnection.HTTP_NOT_FOUND -> ReleaseLookup.NoRelease
                    else -> ReleaseLookup.Failed
                }
            } finally {
                connection.disconnect()
            }
        }

        /** The text of the answer, or null when it is longer than [MAX_BYTES]: a release answer is a few KB. */
        private fun readLimited(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > MAX_BYTES) return null
            }
            return out.toString(Charsets.UTF_8.name())
        }

        private companion object {
            const val LATEST_URL = "https://api.github.com/repos/anm23t/muzik_local_android_player/releases/latest"
            const val TIMEOUT_MS = 10_000
            const val MAX_BYTES = 512 * 1024
            const val BUFFER_BYTES = 8 * 1024
        }
    }
