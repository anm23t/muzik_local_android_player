package spike

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Independent view of a file: mutagen for tags, ffmpeg for the audio stream checksum. */
object Oracle {
    private val python = System.getProperty("spike.python")
    private val script = System.getProperty("spike.oracle")

    fun tags(file: File): JSONObject = JSONObject(run(python, "-I", script, file.path))

    /** MD5 of the compressed audio packets (stream copy), so any change to audio data is visible. */
    fun audioMd5(file: File): String =
        run("ffmpeg", "-loglevel", "error", "-i", file.path, "-map", "0:a", "-c", "copy", "-f", "md5", "-").trim()

    fun sha(bytes: ByteArray?): String? =
        bytes?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) }.take(16) }

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "${cmd.first()} failed: $out" }
        return out
    }
}
