// JNI bridge to the BG-Media BG Wave signaling codec (issue #799).
//
// The wire format is owned by BG-Media (WaveSignaling). This file only calls its
// decoder and returns a flat, ASCII summary to Kotlin, so no second serializer exists in BeyGram.
#include <jni.h>

#include <cstdint>
#include <exception>
#include <string>

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
#include "bgmedia/wave/WaveSignaling.hpp"
#endif

namespace {

jstring newAsciiString(JNIEnv* env, const std::string& text) {
    return env->NewStringUTF(text.c_str());
}

}  // namespace

extern "C" {

// Returns "ok|type|sidHex|iat|exp|caps|nonce|candidates" on success or "err|reason" otherwise.
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveNative_nativeDecodeUri(JNIEnv* env, jclass, jstring jUri,
                                                                  jlong nowSeconds) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    if (!jUri) return newAsciiString(env, "err|null");
    const char* chars = env->GetStringUTFChars(jUri, nullptr);
    if (!chars) return nullptr;
    const std::string uri(chars);
    env->ReleaseStringUTFChars(jUri, chars);

    try {
        bgmedia::wave::WaveSignalMessage msg;
        std::string error;
        if (!bgmedia::wave::decodeWaveUri(uri, msg, &error)) {
            return newAsciiString(env, "err|" + error);
        }
        if (!bgmedia::wave::validateWaveSignal(msg, static_cast<std::int64_t>(nowSeconds), &error)) {
            return newAsciiString(env, "err|" + error);
        }
        const std::string out = "ok|" + std::to_string(static_cast<int>(msg.type)) + "|" +
                                bgmedia::wave::formatSessionIdHex(msg.sid) + "|" +
                                std::to_string(msg.iat) + "|" + std::to_string(msg.exp) + "|" +
                                std::to_string(msg.caps) + "|" + std::to_string(msg.nonce) + "|" +
                                std::to_string(msg.signal.candidates.size());
        return newAsciiString(env, out);
    } catch (const std::exception&) {
        return newAsciiString(env, "err|internal");
    }
#else
    (void)jUri;
    (void)nowSeconds;
    return newAsciiString(env, "err|unavailable");
#endif
}

}  // extern "C"
