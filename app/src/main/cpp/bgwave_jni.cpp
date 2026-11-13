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

// Encodes a control message (Cancel=3, End=4: no ICE/DTLS signal) as the internal bg:// URI
// (asHttps = false) or the HTTPS text-link form. Returns "err|reason" for other types or bad fields.
// Offer and Answer need the ICE/DTLS signal and are not exposed yet.
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveNative_nativeEncodeControl(JNIEnv* env, jclass, jint type,
                                                                      jstring jSidHex, jlong issuedAt,
                                                                      jlong expiresAt, jlong caps,
                                                                      jlong nonce, jboolean asHttps) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    if (type != 3 && type != 4) return newAsciiString(env, "err|unsupported_type");
    if (!jSidHex) return newAsciiString(env, "err|null_sid");
    const char* hex = env->GetStringUTFChars(jSidHex, nullptr);
    if (!hex) return nullptr;
    const std::string sidText(hex);
    env->ReleaseStringUTFChars(jSidHex, hex);
    if (sidText.size() != 32) return newAsciiString(env, "err|bad_sid");

    try {
        bgmedia::wave::WaveSignalMessage msg;
        msg.type = type == 3 ? bgmedia::wave::WaveSignalType::Cancel : bgmedia::wave::WaveSignalType::End;
        for (std::size_t i = 0; i < 16; ++i) {
            const auto nibble = [](char c) -> int {
                if (c >= '0' && c <= '9') return c - '0';
                if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                return -1;
            };
            const int hi = nibble(sidText[2 * i]);
            const int lo = nibble(sidText[2 * i + 1]);
            if (hi < 0 || lo < 0) return newAsciiString(env, "err|bad_sid");
            msg.sid[i] = static_cast<std::uint8_t>(hi * 16 + lo);
        }
        msg.iat = issuedAt;
        msg.exp = expiresAt;
        msg.caps = static_cast<std::uint32_t>(caps);
        msg.nonce = static_cast<std::uint64_t>(nonce);
        std::string error;
        const std::string uri = asHttps
            ? bgmedia::wave::encodeWaveHttpsFragmentUri(msg, "https://beygram.beykus.fun/", &error)
            : bgmedia::wave::encodeWaveUri(msg, &error);
        if (uri.empty()) return newAsciiString(env, "err|" + error);
        return newAsciiString(env, uri);
    } catch (const std::exception&) {
        return newAsciiString(env, "err|internal");
    }
#else
    (void)type;
    (void)jSidHex;
    (void)issuedAt;
    (void)expiresAt;
    (void)caps;
    (void)nonce;
    (void)asHttps;
    return newAsciiString(env, "err|unavailable");
#endif
}

}  // extern "C"
