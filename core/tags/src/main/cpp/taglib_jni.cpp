// JNI bridge between core:tags (Kotlin, org.muzzzlo.player.core.tags.TagLibNative) and TagLib.
//
// Rules for every entry point:
// - strings cross the boundary as UTF-8 byte arrays in both directions; NewStringUTF and GetStringUTFChars
//   use modified UTF-8 and break characters outside the BMP (emoji);
// - a file is opened either by path (UTF-8 bytes) or, when fd >= 0, by a file descriptor the caller keeps owning;
// - no C++ exception crosses into the JVM: failures come back as null.
#include <jni.h>
#include <unistd.h>

#include <memory>
#include <string>

#include <fileref.h>
#include <taglib.h>
#include <tfilestream.h>
#include <tstring.h>

namespace {

std::string toStdString(JNIEnv *env, jbyteArray bytes) {
  const jsize length = env->GetArrayLength(bytes);
  std::string out(static_cast<size_t>(length), '\0');
  env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
  return out;
}

jbyteArray toJavaBytes(JNIEnv *env, const TagLib::String &value) {
  const std::string utf8 = value.to8Bit(true);
  const auto length = static_cast<jsize>(utf8.size());
  jbyteArray out = env->NewByteArray(length);
  if (out != nullptr) {
    env->SetByteArrayRegion(out, 0, length, reinterpret_cast<const jbyte *>(utf8.data()));
  }
  return out;
}

// Keeps the stream alive as long as the FileRef that reads from it.
struct OpenedFile {
  std::unique_ptr<TagLib::FileStream> stream;
  std::unique_ptr<TagLib::FileRef> ref;

  bool isValid() const { return ref && !ref->isNull(); }
};

OpenedFile openForReading(JNIEnv *env, jbyteArray pathUtf8, jint fd, bool readAudioProperties) {
  OpenedFile opened;
  const auto style = TagLib::AudioProperties::Average;
  if (fd >= 0) {
    // FileStream closes its descriptor in the destructor; the caller keeps its own, so pass a duplicate.
    // The duplicate shares the file offset with the caller's descriptor.
    const int ownFd = dup(fd);
    if (ownFd < 0) return opened;
    opened.stream = std::make_unique<TagLib::FileStream>(ownFd, true);
    opened.ref = std::make_unique<TagLib::FileRef>(opened.stream.get(), readAudioProperties, style);
  } else if (pathUtf8 != nullptr) {
    const std::string path = toStdString(env, pathUtf8);
    opened.ref = std::make_unique<TagLib::FileRef>(path.c_str(), readAudioProperties, style);
  }
  return opened;
}

}  // namespace

extern "C" {

// [major, minor, patch] of the TagLib sources this library was built from.
JNIEXPORT jintArray JNICALL
Java_org_muzzzlo_player_core_tags_TagLibNative_nativeVersion(JNIEnv *env, jobject) {
  const jint values[3] = {TAGLIB_MAJOR_VERSION, TAGLIB_MINOR_VERSION, TAGLIB_PATCH_VERSION};
  jintArray out = env->NewIntArray(3);
  if (out != nullptr) env->SetIntArrayRegion(out, 0, 3, values);
  return out;
}

// [lengthMs, bitrateKbps, sampleRateHz, channels], or null when TagLib cannot open the file.
JNIEXPORT jintArray JNICALL
Java_org_muzzzlo_player_core_tags_TagLibNative_nativeReadAudioProperties(
    JNIEnv *env, jobject, jbyteArray pathUtf8, jint fd) {
  try {
    const OpenedFile opened = openForReading(env, pathUtf8, fd, true);
    if (!opened.isValid()) return nullptr;
    const TagLib::AudioProperties *audio = opened.ref->audioProperties();
    if (audio == nullptr) return nullptr;
    const jint values[4] = {audio->lengthInMilliseconds(), audio->bitrate(), audio->sampleRate(), audio->channels()};
    jintArray out = env->NewIntArray(4);
    if (out != nullptr) env->SetIntArrayRegion(out, 0, 4, values);
    return out;
  } catch (...) {
    return nullptr;
  }
}

// The title tag as UTF-8 bytes; null when the file cannot be opened or has no title.
JNIEXPORT jbyteArray JNICALL
Java_org_muzzzlo_player_core_tags_TagLibNative_nativeReadTitle(JNIEnv *env, jobject, jbyteArray pathUtf8, jint fd) {
  try {
    const OpenedFile opened = openForReading(env, pathUtf8, fd, false);
    if (!opened.isValid() || opened.ref->tag() == nullptr) return nullptr;
    const TagLib::String title = opened.ref->tag()->title();
    if (title.isEmpty()) return nullptr;
    return toJavaBytes(env, title);
  } catch (...) {
    return nullptr;
  }
}

}  // extern "C"
