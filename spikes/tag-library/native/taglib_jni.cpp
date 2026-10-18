// Minimal JNI bridge used only by the 0.3.0 spike. Not production code.
// Properties cross the boundary as a flat String[] of "KEY\u0001value" entries (one per value, order kept).
#include <jni.h>
#include <unistd.h>

#include <memory>
#include <string>

#include <fileref.h>
#include <id3v2header.h>
#include <id3v2tag.h>
#include <mpegfile.h>
#include <tfilestream.h>
#include <tpropertymap.h>
#include <tvariant.h>

namespace {

constexpr char kSep = '\x01';

TagLib::String toTag(JNIEnv *env, jstring s) {
  const char *chars = env->GetStringUTFChars(s, nullptr);
  TagLib::String out(chars, TagLib::String::UTF8);
  env->ReleaseStringUTFChars(s, chars);
  return out;
}

jstring toJava(JNIEnv *env, const TagLib::String &s) {
  // NewStringUTF expects modified UTF-8; fine for the BMP test data used by the spike.
  return env->NewStringUTF(s.to8Bit(true).c_str());
}

// Opens either by path or, when fd >= 0, by an already opened file descriptor (Android: ParcelFileDescriptor).
struct Opened {
  std::unique_ptr<TagLib::FileStream> stream;
  std::unique_ptr<TagLib::FileRef> ref;
};

Opened open(JNIEnv *env, jstring path, jint fd, bool readOnly, bool readAudioProperties) {
  Opened o;
  auto style = TagLib::AudioProperties::Average;
  if (fd >= 0) {
    // FileStream takes ownership and closes the descriptor in its destructor, so hand it a duplicate.
    o.stream = std::make_unique<TagLib::FileStream>(dup(fd), readOnly);
    o.ref = std::make_unique<TagLib::FileRef>(o.stream.get(), readAudioProperties, style);
  } else {
    const char *p = env->GetStringUTFChars(path, nullptr);
    o.ref = std::make_unique<TagLib::FileRef>(p, readAudioProperties, style);
    env->ReleaseStringUTFChars(path, p);
  }
  return o;
}

}  // namespace

extern "C" {

JNIEXPORT jobjectArray JNICALL Java_spike_taglib_TagLibNative_read(JNIEnv *env, jclass, jstring path, jint fd) {
  Opened o = open(env, path, fd, true, false);
  if (o.ref->isNull()) return nullptr;
  TagLib::PropertyMap props = o.ref->properties();
  TagLib::StringList flat;
  for (const auto &[key, values] : props) {
    for (const auto &v : values) flat.append(key + TagLib::String(std::string(1, kSep)) + v);
  }
  jobjectArray out = env->NewObjectArray(static_cast<jsize>(flat.size()), env->FindClass("java/lang/String"), nullptr);
  jsize i = 0;
  for (const auto &s : flat) env->SetObjectArrayElement(out, i++, toJava(env, s));
  return out;
}

JNIEXPORT jbyteArray JNICALL Java_spike_taglib_TagLibNative_readPicture(JNIEnv *env, jclass, jstring path, jint fd) {
  Opened o = open(env, path, fd, true, false);
  if (o.ref->isNull()) return nullptr;
  const auto pictures = o.ref->complexProperties("PICTURE");
  if (pictures.isEmpty()) return nullptr;
  const TagLib::ByteVector data = pictures.front().value("data").toByteVector();
  jbyteArray out = env->NewByteArray(static_cast<jsize>(data.size()));
  env->SetByteArrayRegion(out, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte *>(data.data()));
  return out;
}

JNIEXPORT jint JNICALL Java_spike_taglib_TagLibNative_id3v2Version(JNIEnv *env, jclass, jstring path) {
  const char *p = env->GetStringUTFChars(path, nullptr);
  TagLib::MPEG::File f(p, false);
  env->ReleaseStringUTFChars(path, p);
  if (!f.isValid() || !f.hasID3v2Tag()) return 0;
  return static_cast<jint>(f.ID3v2Tag()->header()->majorVersion());
}

// replaceKeys: properties to replace (missing in entries = delete). pictureMode: 0 keep, 1 replace, 2 remove.
JNIEXPORT jboolean JNICALL Java_spike_taglib_TagLibNative_write(
    JNIEnv *env, jclass, jstring path, jint fd, jobjectArray replaceKeys, jobjectArray entries,
    jint pictureMode, jbyteArray picture, jstring pictureMime) {
  Opened o = open(env, path, fd, false, false);
  if (o.ref->isNull()) return JNI_FALSE;

  // Start from everything the file already has, so fields we do not touch are written back unchanged.
  TagLib::PropertyMap props = o.ref->properties();
  for (jsize i = 0; i < env->GetArrayLength(replaceKeys); ++i) {
    props.erase(toTag(env, static_cast<jstring>(env->GetObjectArrayElement(replaceKeys, i))));
  }
  for (jsize i = 0; i < env->GetArrayLength(entries); ++i) {
    const TagLib::String e = toTag(env, static_cast<jstring>(env->GetObjectArrayElement(entries, i)));
    const int sep = e.find(TagLib::String(std::string(1, kSep)));
    props[e.substr(0, sep)].append(e.substr(sep + 1));
  }
  o.ref->setProperties(props);

  if (pictureMode == 2) {
    o.ref->setComplexProperties("PICTURE", {});
  } else if (pictureMode == 1) {
    jsize len = env->GetArrayLength(picture);
    TagLib::ByteVector data(static_cast<unsigned int>(len));
    env->GetByteArrayRegion(picture, 0, len, reinterpret_cast<jbyte *>(data.data()));
    TagLib::VariantMap pic;
    pic["data"] = data;
    pic["mimeType"] = toTag(env, pictureMime);
    pic["pictureType"] = TagLib::String("Front Cover");
    pic["description"] = TagLib::String("");
    o.ref->setComplexProperties("PICTURE", {pic});
  }

  // FileRef::save() would upgrade ID3v2.3 to v2.4; keep the version the file already had.
  if (auto *mpeg = dynamic_cast<TagLib::MPEG::File *>(o.ref->file())) {
    const bool v3 = mpeg->hasID3v2Tag() && mpeg->ID3v2Tag()->header()->majorVersion() == 3;
    return mpeg->save(TagLib::MPEG::File::AllTags, TagLib::File::StripNone,
                      v3 ? TagLib::ID3v2::v3 : TagLib::ID3v2::v4) ? JNI_TRUE : JNI_FALSE;
  }
  return o.ref->save() ? JNI_TRUE : JNI_FALSE;
}

// [lengthMs, bitrate, sampleRate, channels]; used by the performance run (tags + audio properties).
JNIEXPORT jintArray JNICALL Java_spike_taglib_TagLibNative_readAudio(JNIEnv *env, jclass, jstring path) {
  Opened o = open(env, path, -1, true, true);
  if (o.ref->isNull() || !o.ref->audioProperties()) return nullptr;
  auto *ap = o.ref->audioProperties();
  (void)o.ref->properties();
  jint values[4] = {ap->lengthInMilliseconds(), ap->bitrate(), ap->sampleRate(), ap->channels()};
  jintArray out = env->NewIntArray(4);
  env->SetIntArrayRegion(out, 0, 4, values);
  return out;
}

}  // extern "C"
