// JNI bridge between core:tags (Kotlin, org.muzzzlo.player.core.tags.TagLibNative) and TagLib.
//
// Rules for every entry point:
// - strings cross the boundary as UTF-8 byte arrays in both directions; NewStringUTF and GetStringUTFChars
//   use modified UTF-8 and break characters outside the BMP (emoji);
// - a file is opened either by path (UTF-8 bytes) or, when fd >= 0, by a file descriptor the caller keeps owning;
//   with a descriptor the caller may pass the file name, so TagLib picks the format by extension as it does for paths;
// - no C++ exception crosses into the JVM: failures come back as null.
#include <jni.h>
#include <unistd.h>

#include <memory>
#include <string>
#include <utility>

#include <fileref.h>
#include <flacfile.h>
#include <flacpicture.h>
#include <id3v2header.h>
#include <id3v2tag.h>
#include <mp4file.h>
#include <mpegfile.h>
#include <opusfile.h>
#include <taglib.h>
#include <tfilestream.h>
#include <tpropertymap.h>
#include <tstring.h>
#include <tvariant.h>
#include <vorbisfile.h>

namespace {

// Container codes shared with TagLibNative.kt (CONTAINER_*).
constexpr jint kContainerUnknown = 0;
constexpr jint kContainerMp3 = 1;
constexpr jint kContainerFlac = 2;
constexpr jint kContainerMp4 = 3;
constexpr jint kContainerOggVorbis = 4;
constexpr jint kContainerOpus = 5;

// Layout of the int array returned by nativeReadTags, shared with TagLibNative.kt (INFO_*).
enum InfoIndex {
  kInfoContainer,
  kInfoId3v2Version,
  kInfoPictureCount,
  kInfoHasAudio,
  kInfoLengthMs,
  kInfoBitrateKbps,
  kInfoSampleRateHz,
  kInfoChannels,
  kInfoSize,
};

std::string toStdString(JNIEnv *env, jbyteArray bytes) {
  const jsize length = env->GetArrayLength(bytes);
  std::string out(static_cast<size_t>(length), '\0');
  env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
  return out;
}

jbyteArray toJavaBytes(JNIEnv *env, const char *data, size_t size) {
  const auto length = static_cast<jsize>(size);
  jbyteArray out = env->NewByteArray(length);
  if (out != nullptr) {
    env->SetByteArrayRegion(out, 0, length, reinterpret_cast<const jbyte *>(data));
  }
  return out;
}

jbyteArray toJavaBytes(JNIEnv *env, const TagLib::String &value) {
  const std::string utf8 = value.to8Bit(true);
  return toJavaBytes(env, utf8.data(), utf8.size());
}

jbyteArray toJavaBytes(JNIEnv *env, const TagLib::ByteVector &value) {
  return toJavaBytes(env, value.data(), value.size());
}

jintArray toJavaInts(JNIEnv *env, const jint *values, jsize count) {
  jintArray out = env->NewIntArray(count);
  if (out != nullptr) env->SetIntArrayRegion(out, 0, count, values);
  return out;
}

// A descriptor stream that reports a file name. FileRef picks the format by the extension of the stream name and
// only falls back to sniffing the content when that fails; sniffing rejects valid files, for example an MP3 whose
// LAME "Info" frame is followed by a second ID3v2 tag. FileStream(fd) has no name, so it would always be sniffed.
class NamedFileStream : public TagLib::FileStream {
 public:
  NamedFileStream(int fd, std::string name) : TagLib::FileStream(fd, true), name_(std::move(name)) {}

  TagLib::FileName name() const override { return name_.c_str(); }

 private:
  std::string name_;
};

// Keeps the stream alive as long as the FileRef that reads from it.
struct OpenedFile {
  std::unique_ptr<TagLib::FileStream> stream;
  std::unique_ptr<TagLib::FileRef> ref;

  bool isValid() const { return ref && !ref->isNull(); }
};

OpenedFile openForReading(JNIEnv *env, jbyteArray pathUtf8, jint fd, jbyteArray nameUtf8, bool readAudioProperties) {
  OpenedFile opened;
  const auto style = TagLib::AudioProperties::Average;
  if (fd >= 0) {
    // FileStream closes its descriptor in the destructor; the caller keeps its own, so pass a duplicate.
    // The duplicate shares the file offset with the caller's descriptor.
    const int ownFd = dup(fd);
    if (ownFd < 0) return opened;
    std::string name = nameUtf8 != nullptr ? toStdString(env, nameUtf8) : std::string();
    opened.stream = std::make_unique<NamedFileStream>(ownFd, std::move(name));
    opened.ref = std::make_unique<TagLib::FileRef>(opened.stream.get(), readAudioProperties, style);
  } else if (pathUtf8 != nullptr) {
    const std::string path = toStdString(env, pathUtf8);
    opened.ref = std::make_unique<TagLib::FileRef>(path.c_str(), readAudioProperties, style);
  }
  return opened;
}

jint containerOf(TagLib::File *file) {
  if (dynamic_cast<TagLib::MPEG::File *>(file) != nullptr) return kContainerMp3;
  if (dynamic_cast<TagLib::FLAC::File *>(file) != nullptr) return kContainerFlac;
  if (dynamic_cast<TagLib::MP4::File *>(file) != nullptr) return kContainerMp4;
  if (dynamic_cast<TagLib::Ogg::Vorbis::File *>(file) != nullptr) return kContainerOggVorbis;
  if (dynamic_cast<TagLib::Ogg::Opus::File *>(file) != nullptr) return kContainerOpus;
  return kContainerUnknown;
}

// Major version (3 or 4) of the ID3v2 tag of an MPEG file, 0 when there is none.
jint id3v2VersionOf(TagLib::File *file) {
  auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file);
  if (mpeg == nullptr || !mpeg->hasID3v2Tag()) return 0;
  return static_cast<jint>(mpeg->ID3v2Tag()->header()->majorVersion());
}

// TagLib picks the MPEG reader by file extension and accepts a file without a single MPEG frame
// (a text file named .mp3). Such a file is not audio.
bool isMpegWithoutFrames(TagLib::File *file) {
  auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(file);
  return mpeg != nullptr && mpeg->firstFrameOffset() < 0;
}

// Flattens the property map into [key, value, key, value, ...]; a key repeats once per value.
jobjectArray propertiesToJava(JNIEnv *env, const TagLib::PropertyMap &properties) {
  jsize count = 0;
  for (const auto &[key, values] : properties) count += static_cast<jsize>(values.size()) * 2;

  jclass byteArrayClass = env->FindClass("[B");
  if (byteArrayClass == nullptr) return nullptr;
  jobjectArray out = env->NewObjectArray(count, byteArrayClass, nullptr);
  env->DeleteLocalRef(byteArrayClass);
  if (out == nullptr) return nullptr;

  jsize index = 0;
  for (const auto &[key, values] : properties) {
    for (const auto &value : values) {
      jbyteArray keyBytes = toJavaBytes(env, key);
      jbyteArray valueBytes = toJavaBytes(env, value);
      if (keyBytes == nullptr || valueBytes == nullptr) return nullptr;
      env->SetObjectArrayElement(out, index++, keyBytes);
      env->SetObjectArrayElement(out, index++, valueBytes);
      // A long tag list would otherwise overflow the local reference table.
      env->DeleteLocalRef(keyBytes);
      env->DeleteLocalRef(valueBytes);
    }
  }
  return out;
}

// Picture type of a PICTURE complex property; MP4 covers have none and count as front covers.
jint pictureTypeOf(const TagLib::VariantMap &picture) {
  if (!picture.contains("pictureType")) return TagLib::FLAC::Picture::FrontCover;
  return static_cast<jint>(TagLib::FLAC::Picture::typeFromString(picture.value("pictureType").toString()));
}

}  // namespace

extern "C" {

// [major, minor, patch] of the TagLib sources this library was built from.
JNIEXPORT jintArray JNICALL
Java_org_muzzzlo_player_core_tags_TagLibNative_nativeVersion(JNIEnv *env, jobject) {
  const jint values[3] = {TAGLIB_MAJOR_VERSION, TAGLIB_MINOR_VERSION, TAGLIB_PATCH_VERSION};
  return toJavaInts(env, values, 3);
}

// [info (int[], see InfoIndex), properties (byte[][], see propertiesToJava)],
// or null when TagLib cannot open the file.
JNIEXPORT jobjectArray JNICALL
Java_org_muzzzlo_player_core_tags_TagLibNative_nativeReadTags(
    JNIEnv *env, jobject, jbyteArray pathUtf8, jint fd, jbyteArray nameUtf8, jboolean readAudioProperties) {
  try {
    const OpenedFile opened = openForReading(env, pathUtf8, fd, nameUtf8, readAudioProperties == JNI_TRUE);
    if (!opened.isValid()) return nullptr;
    TagLib::File *file = opened.ref->file();
    if (isMpegWithoutFrames(file)) return nullptr;

    jint info[kInfoSize] = {};
    info[kInfoContainer] = containerOf(file);
    info[kInfoId3v2Version] = id3v2VersionOf(file);
    info[kInfoPictureCount] = static_cast<jint>(opened.ref->complexProperties("PICTURE").size());
    if (const TagLib::AudioProperties *audio = opened.ref->audioProperties(); audio != nullptr) {
      info[kInfoHasAudio] = 1;
      info[kInfoLengthMs] = audio->lengthInMilliseconds();
      info[kInfoBitrateKbps] = audio->bitrate();
      info[kInfoSampleRateHz] = audio->sampleRate();
      info[kInfoChannels] = audio->channels();
    }

    jintArray infoArray = toJavaInts(env, info, kInfoSize);
    jobjectArray properties = propertiesToJava(env, opened.ref->properties());
    jclass objectClass = env->FindClass("java/lang/Object");
    if (infoArray == nullptr || properties == nullptr || objectClass == nullptr) return nullptr;
    jobjectArray out = env->NewObjectArray(2, objectClass, nullptr);
    if (out == nullptr) return nullptr;
    env->SetObjectArrayElement(out, 0, infoArray);
    env->SetObjectArrayElement(out, 1, properties);
    return out;
  } catch (...) {
    return nullptr;
  }
}

// [data (byte[]), mimeType (UTF-8 byte[]), pictureType (int[1])] of the front cover, or of the first picture
// when there is no front cover; null when the file cannot be opened or has no pictures.
JNIEXPORT jobjectArray JNICALL
Java_org_muzzzlo_player_core_tags_TagLibNative_nativeReadCover(
    JNIEnv *env, jobject, jbyteArray pathUtf8, jint fd, jbyteArray nameUtf8) {
  try {
    const OpenedFile opened = openForReading(env, pathUtf8, fd, nameUtf8, false);
    if (!opened.isValid() || isMpegWithoutFrames(opened.ref->file())) return nullptr;
    const TagLib::List<TagLib::VariantMap> pictures = opened.ref->complexProperties("PICTURE");
    if (pictures.isEmpty()) return nullptr;

    const TagLib::VariantMap *chosen = &pictures.front();
    for (const auto &picture : pictures) {
      if (pictureTypeOf(picture) == TagLib::FLAC::Picture::FrontCover) {
        chosen = &picture;
        break;
      }
    }

    const jint type = pictureTypeOf(*chosen);
    jbyteArray data = toJavaBytes(env, chosen->value("data").toByteVector());
    jbyteArray mimeType = toJavaBytes(env, chosen->value("mimeType").toString());
    jintArray typeArray = toJavaInts(env, &type, 1);
    jclass objectClass = env->FindClass("java/lang/Object");
    if (data == nullptr || mimeType == nullptr || typeArray == nullptr || objectClass == nullptr) return nullptr;
    jobjectArray out = env->NewObjectArray(3, objectClass, nullptr);
    if (out == nullptr) return nullptr;
    env->SetObjectArrayElement(out, 0, data);
    env->SetObjectArrayElement(out, 1, mimeType);
    env->SetObjectArrayElement(out, 2, typeArray);
    return out;
  } catch (...) {
    return nullptr;
  }
}

}  // extern "C"
