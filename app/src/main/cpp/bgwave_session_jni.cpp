// JNI session layer for BG Wave P2P (issue #799).
//
// Wraps BG-Media's WaveAudioPipeline with the real WebRtcWaveTransport and OpusWaveCodec.
// BG-Media owns the state machine, signaling codec and transport. This file only maps
// Kotlin calls onto it: create/destroy, OFFER/ANSWER/CANCEL/END, stop/reset/reconnect,
// outbound signal queue, stats, and host/listener PCM. It never builds signaling bytes itself.
#include <jni.h>

#include "bgwave_session.h"

#include <atomic>
#include <chrono>
#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA && defined(BGWAVE_P2P) && BGWAVE_P2P
#include "bgmedia/wave/OpusWaveCodec.hpp"
#include "bgmedia/wave/WaveAudioPipeline.hpp"
#include "bgmedia/wave/WaveSignaling.hpp"
#include "bgmedia/wave/WebRtcWaveTransport.hpp"
#define BGWAVE_SESSION_ENABLED 1
#else
#define BGWAVE_SESSION_ENABLED 0
#endif

namespace {

#if BGWAVE_SESSION_ENABLED
using namespace bgmedia::wave;

struct Session {
    std::shared_ptr<WaveAudioPipeline> pipeline;
    std::mutex mutex;
    std::deque<std::string> outbound;  // bg://wave/v1/... URIs waiting for TDLib to send
    std::string lastError;
    // The pipeline does its work only in process(): encoding and sending on the host, jitter and decode on the
    // listener. This thread calls it about every 10 ms for the whole life of the session.
    std::atomic<bool> pumpRunning{false};
    std::thread pump;
};

Session* sessionOf(jlong handle) {
    return reinterpret_cast<Session*>(static_cast<intptr_t>(handle));
}

}  // namespace

std::shared_ptr<WaveAudioPipeline> bgWaveSessionPipeline(std::int64_t sessionHandle) {
    Session* session = sessionOf(sessionHandle);
    return session ? session->pipeline : nullptr;
}

namespace {

std::string toStd(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}
#endif

}  // namespace

extern "C" {

// role: 0 = Host, 1 = Listener. Returns 0 when the P2P build is not compiled in.
JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeCreate(JNIEnv*, jclass, jint role,
                                                                      jint bitrateBps) {
#if BGWAVE_SESSION_ENABLED
    auto* session = new Session();
    WebRtcTransportConfig transportConfig;
    transportConfig.role = role == 0 ? WaveRole::Host : WaveRole::Listener;
    auto transport = std::make_shared<WebRtcWaveTransport>(transportConfig);
    const auto bitrate = static_cast<std::uint32_t>(bitrateBps > 0 ? bitrateBps : kWaveDefaultBitrateBps);
    auto codec = std::make_shared<OpusWaveCodec>(bitrate);
    WavePipelineConfig config;
    config.role = transportConfig.role;
    config.targetBitrateBps = bitrate;
    session->pipeline = std::make_shared<WaveAudioPipeline>(config, codec, transport);

    WavePipelineCallbacks callbacks;
    callbacks.onOutboundSignal = [session](const WaveSignalMessage& signal) {
        std::string error;
        const std::string uri = encodeWaveUri(signal, &error);
        std::lock_guard<std::mutex> lock(session->mutex);
        if (uri.empty()) {
            session->lastError = "encode:" + error;
            return;
        }
        session->outbound.push_back(uri);
    };
    callbacks.onError = [session](const WaveError& error) {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->lastError = error.message;
    };
    session->pipeline->setCallbacks(std::move(callbacks));
    session->pumpRunning.store(true, std::memory_order_release);
    session->pump = std::thread([session]() {
        const auto origin = std::chrono::steady_clock::now();
        while (session->pumpRunning.load(std::memory_order_acquire)) {
            const double now = std::chrono::duration<double>(std::chrono::steady_clock::now() - origin).count();
            session->pipeline->process(now);
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    });
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
#else
    (void)role;
    (void)bitrateBps;
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeDestroy(JNIEnv*, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session) return;
    session->pipeline->stop(false, 0);
    session->pumpRunning.store(false, std::memory_order_release);
    if (session->pump.joinable()) session->pump.join();
    // The mix engine may still hold the pipeline as its PCM tap. Drop the callbacks that point at
    // this Session before deleting it, so a late render or transport event cannot reach freed memory.
    session->pipeline->setCallbacks(WavePipelineCallbacks{});
    delete session;
#else
    (void)handle;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStartOffer(JNIEnv*, jclass, jlong handle,
                                                                          jlong nowSeconds,
                                                                          jlong ttlSeconds) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session) return JNI_FALSE;
    return session->pipeline->startOffer(nowSeconds, ttlSeconds) ? JNI_TRUE : JNI_FALSE;
#else
    (void)handle; (void)nowSeconds; (void)ttlSeconds;
    return JNI_FALSE;
#endif
}

// Routes one remote BG Wave URI by its type: OFFER to the listener, ANSWER to the host,
// CANCEL and END to the pipeline's teardown path. Returns false for a bad or wrong-state signal.
JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeHandleRemote(JNIEnv* env, jclass,
                                                                            jlong handle, jstring jUri,
                                                                            jlong nowSeconds) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session) return JNI_FALSE;
    const std::string uri = toStd(env, jUri);
    WaveSignalMessage msg;
    std::string error;
    if (!decodeWaveUri(uri, msg, &error)) {
        std::lock_guard<std::mutex> lock(session->mutex);
        session->lastError = "decode:" + error;
        return JNI_FALSE;
    }
    bool ok = false;
    switch (msg.type) {
    case WaveSignalType::Offer:
        ok = session->pipeline->acceptOffer(msg, nowSeconds);
        break;
    case WaveSignalType::Answer:
        ok = session->pipeline->acceptAnswer(msg, nowSeconds);
        break;
    case WaveSignalType::Cancel:
    case WaveSignalType::End:
        ok = session->pipeline->handleRemoteSignal(msg, nowSeconds);
        break;
    }
    return ok ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)handle; (void)jUri; (void)nowSeconds;
    return JNI_FALSE;
#endif
}

// Returns the next outbound BG Wave URI, or "" when the queue is empty.
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativePollOutbound(JNIEnv* env, jclass,
                                                                            jlong handle) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    std::string next;
    if (session) {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (!session->outbound.empty()) {
            next = session->outbound.front();
            session->outbound.pop_front();
        }
    }
    return env->NewStringUTF(next.c_str());
#else
    (void)handle;
    return env->NewStringUTF("");
#endif
}

// Returns "state|route|lastError" for the Kotlin side to map onto UI states.
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStatus(JNIEnv* env, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session) return env->NewStringUTF("Failed|None|no session");
    std::string lastError;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        lastError = session->lastError;
    }
    const std::string text = std::string(toString(session->pipeline->state())) + "|" +
                             toString(session->pipeline->route()) + "|" + lastError;
    return env->NewStringUTF(text.c_str());
#else
    (void)handle;
    return env->NewStringUTF("Failed|None|p2p-not-built");
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStop(JNIEnv*, jclass, jlong handle,
                                                                    jboolean emitEnd, jlong nowSeconds) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (session) session->pipeline->stop(emitEnd == JNI_TRUE, nowSeconds);
#else
    (void)handle; (void)emitEnd; (void)nowSeconds;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeReset(JNIEnv*, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (session) session->pipeline->reset();
#else
    (void)handle;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeReconnect(JNIEnv*, jclass, jlong handle,
                                                                         jlong nowSeconds) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    return (session && session->pipeline->reconnect(nowSeconds)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)handle; (void)nowSeconds;
    return JNI_FALSE;
#endif
}

// Stats as "sent|received|lost|plc|jitterMs|bitrateBps|depthMs".
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStats(JNIEnv* env, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session) return env->NewStringUTF("0|0|0|0|0|0|0");
    const WaveStats stats = session->pipeline->stats();
    const std::string text = std::to_string(stats.packetsSent) + "|" +
                             std::to_string(stats.packetsReceived) + "|" +
                             std::to_string(stats.packetsLost) + "|" + std::to_string(stats.plcFrames) + "|" +
                             std::to_string(stats.jitterMs) + "|" + std::to_string(stats.targetBitrateBps) + "|" +
                             std::to_string(stats.jitterBufferDepthMs);
    return env->NewStringUTF(text.c_str());
#else
    (void)handle;
    return env->NewStringUTF("0|0|0|0|0|0|0");
#endif
}

// Host: real-time PCM from the audio thread (interleaved stereo float, 48 kHz preferred).
JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativePushHostPcm(JNIEnv* env, jclass,
                                                                           jlong handle, jfloatArray pcm,
                                                                           jint frames, jint sampleRate) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session || !pcm || frames <= 0) return;
    const jsize length = env->GetArrayLength(pcm);
    if (length < frames * 2) return;
    jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
    if (!data) return;
    session->pipeline->pushHostPcm(data, static_cast<std::size_t>(frames),
                                   static_cast<std::uint32_t>(sampleRate));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
#else
    (void)env; (void)handle; (void)pcm; (void)frames; (void)sampleRate;
#endif
}

// Listener: fills `out` with decoded interleaved stereo; returns frames written.
JNIEXPORT jint JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativePullListenerPcm(JNIEnv* env, jclass,
                                                                               jlong handle, jfloatArray out,
                                                                               jint frames, jint sampleRate) {
#if BGWAVE_SESSION_ENABLED
    Session* session = sessionOf(handle);
    if (!session || !out || frames <= 0) return 0;
    const jsize length = env->GetArrayLength(out);
    if (length < frames * 2) return 0;
    jfloat* data = env->GetFloatArrayElements(out, nullptr);
    if (!data) return 0;
    const std::size_t written = session->pipeline->pullListenerPcm(
        data, static_cast<std::size_t>(frames), static_cast<std::uint32_t>(sampleRate));
    env->ReleaseFloatArrayElements(out, data, 0);
    return static_cast<jint>(written);
#else
    (void)env; (void)handle; (void)out; (void)frames; (void)sampleRate;
    return 0;
#endif
}

}  // extern "C"
