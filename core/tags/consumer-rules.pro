# JNI looks up native methods by class and method name (see src/main/cpp/taglib_jni.cpp).
-keep class org.muzzzlo.player.core.tags.TagLibNative {
    native <methods>;
}
