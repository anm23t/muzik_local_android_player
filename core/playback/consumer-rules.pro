# JNI looks up native methods by class and method name (see src/main/cpp/alac_jni.cpp).
-keep class org.muzzzlo.player.core.playback.AlacNative {
    native <methods>;
}
