// JNI bridge between core:playback (Kotlin, org.muzzzlo.player.core.playback.AlacNative) and the Apple ALAC decoder.
//
// Rules for every entry point:
// - a decoder is a pointer handed to Kotlin as a jlong; 0 means "could not create", and nativeRelease frees it;
// - packets and decoded samples travel in direct ByteBuffers, so no audio data is copied by the JVM;
// - no C++ exception crosses into the JVM: failures come back as 0 or a negative count.
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <new>
#include <vector>

#include "ALACBitUtilities.h"
#include "ALACDecoder.h"

namespace {

// Size of ALACSpecificConfig, the magic cookie without the 'frma' and 'alac' atom headers.
constexpr jsize kCookieSize = 24;

// Limits checked before the cookie reaches the decoder, same values as AlacConfig.kt. Encoders write 4096 samples
// per frame; a huge frame length would overflow the buffer sizes the decoder computes on 32-bit ABIs.
constexpr uint32_t kMaxFrameLength = 65536;
constexpr uint32_t kMaxChannels = 8;

// Error codes of nativeDecode, shared with AlacNative.kt (ERROR_*).
constexpr jint kErrorBadArguments = -1;
constexpr jint kErrorCorruptPacket = -2;

/**
 * The decoder reads the bit stream a few bytes at a time without checking the end of the packet, and a broken
 * element can make it read up to a whole frame further: uncompressed samples (frameLength x 2 channels x 4 bytes)
 * plus the shifted low bytes (frameLength x 2 channels x 2 bytes) plus headers and coefficients. Only one element
 * can run past the end, the next one is refused. Each packet is copied into a buffer with this many zero bytes
 * after it, so such reads stay inside memory the bridge owns.
 */
uint32_t paddingFor(uint32_t frameLength) { return frameLength * 12 + 1024; }

/** One decoder per stream: the Apple decoder, its configuration and the padded copy of the current packet. */
struct AlacStream {
  ALACDecoder decoder;
  uint32_t frameLength = 0;
  uint32_t channels = 0;
  uint32_t bytesPerSample = 0;
  std::vector<uint8_t> packet;
};

uint32_t readBigEndian32(const uint8_t *bytes) {
  return (static_cast<uint32_t>(bytes[0]) << 24) | (static_cast<uint32_t>(bytes[1]) << 16) |
         (static_cast<uint32_t>(bytes[2]) << 8) | static_cast<uint32_t>(bytes[3]);
}

/** Bytes per sample of the decoder output: 20-bit samples come out packed into 24 bits. */
uint32_t bytesPerSampleFor(uint32_t bitDepth) {
  switch (bitDepth) {
    case 16:
      return 2;
    case 20:
    case 24:
      return 3;
    case 32:
      return 4;
    default:
      return 0;
  }
}

AlacStream *streamOf(jlong handle) { return reinterpret_cast<AlacStream *>(handle); }

}  // namespace

extern "C" {

/** Creates a decoder for the 24-byte magic cookie [cookie]; 0 when the cookie is not usable. */
JNIEXPORT jlong JNICALL Java_org_muzzzlo_player_core_playback_AlacNative_nativeCreate(
    JNIEnv *env, jobject /* this */, jbyteArray cookie) {
  if (cookie == nullptr || env->GetArrayLength(cookie) != kCookieSize) return 0;
  uint8_t bytes[kCookieSize];
  env->GetByteArrayRegion(cookie, 0, kCookieSize, reinterpret_cast<jbyte *>(bytes));
  const uint32_t frameLength = readBigEndian32(bytes);
  const uint32_t channels = bytes[9];
  if (frameLength == 0 || frameLength > kMaxFrameLength || channels == 0 || channels > kMaxChannels) return 0;

  auto *stream = new (std::nothrow) AlacStream();
  if (stream == nullptr) return 0;
  if (stream->decoder.Init(bytes, kCookieSize) != ALAC_noErr) {
    delete stream;
    return 0;
  }
  const ALACSpecificConfig &config = stream->decoder.mConfig;
  stream->frameLength = config.frameLength;
  stream->channels = config.numChannels;
  stream->bytesPerSample = bytesPerSampleFor(config.bitDepth);
  if (stream->frameLength == 0 || stream->channels == 0 || stream->bytesPerSample == 0) {
    delete stream;
    return 0;
  }
  return reinterpret_cast<jlong>(stream);
}

/**
 * Decodes one packet: [inputSize] bytes of [input] from [inputOffset] into [output] from its start, as interleaved
 * little-endian samples. Returns the number of frames (samples per channel) written, or a negative ERROR_* code.
 */
JNIEXPORT jint JNICALL Java_org_muzzzlo_player_core_playback_AlacNative_nativeDecode(
    JNIEnv *env, jobject /* this */, jlong handle, jobject input, jint inputOffset, jint inputSize,
    jobject output) {
  AlacStream *stream = streamOf(handle);
  if (stream == nullptr || input == nullptr || output == nullptr || inputOffset < 0 || inputSize <= 0) {
    return kErrorBadArguments;
  }
  auto *in = static_cast<uint8_t *>(env->GetDirectBufferAddress(input));
  auto *out = static_cast<uint8_t *>(env->GetDirectBufferAddress(output));
  const jlong inCapacity = env->GetDirectBufferCapacity(input);
  const jlong outCapacity = env->GetDirectBufferCapacity(output);
  const jlong frameBytes =
      static_cast<jlong>(stream->frameLength) * stream->channels * stream->bytesPerSample;
  if (in == nullptr || out == nullptr || static_cast<jlong>(inputOffset) + inputSize > inCapacity ||
      outCapacity < frameBytes) {
    return kErrorBadArguments;
  }

  const auto size = static_cast<uint32_t>(inputSize);
  stream->packet.assign(size + paddingFor(stream->frameLength), 0);
  std::memcpy(stream->packet.data(), in + inputOffset, size);

  BitBuffer bits;
  BitBufferInit(&bits, stream->packet.data(), size);
  uint32_t frames = 0;
  const int32_t status = stream->decoder.Decode(&bits, out, stream->frameLength, stream->channels, &frames);
  if (status != ALAC_noErr || frames > stream->frameLength) return kErrorCorruptPacket;
  return static_cast<jint>(frames);
}

/** Frees the decoder created by nativeCreate; 0 is ignored. */
JNIEXPORT void JNICALL Java_org_muzzzlo_player_core_playback_AlacNative_nativeRelease(
    JNIEnv * /* env */, jobject /* this */, jlong handle) {
  delete streamOf(handle);
}

}  // extern "C"
