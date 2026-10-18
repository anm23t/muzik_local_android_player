package spike.taglib

/** JNI entry points implemented in native/taglib_jni.cpp. `fd` < 0 means "open by path". */
object TagLibNative {
    @JvmStatic external fun read(path: String?, fd: Int): Array<String>?

    @JvmStatic external fun readPicture(path: String?, fd: Int): ByteArray?

    @JvmStatic external fun id3v2Version(path: String): Int

    @JvmStatic external fun write(
        path: String?,
        fd: Int,
        replaceKeys: Array<String>,
        entries: Array<String>,
        pictureMode: Int,
        picture: ByteArray?,
        pictureMime: String?,
    ): Boolean

    @JvmStatic external fun readAudio(path: String): IntArray?
}
