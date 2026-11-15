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
#include <exception>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <utility>

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

    ~Session() {
        pumpRunning.store(false, std::memory_order_release);
        if (pump.joinable()) {
            if (pump.get_id() != std::this_thread::get_id()) {
                pump.join();
            } else {
                pump.detach();
            }
        }
    }
};

std::mutex sessionsMutex;
std::unordered_map<std::int64_t, std::shared_ptr<Session>> sessions;
std::int64_t nextSessionId = 1;

std::shared_ptr<Session> lookupSession(jlong handle) {
    if (handle <= 0) return nullptr;
    std::lock_guard<std::mutex> lock(sessionsMutex);
    auto it = sessions.find(static_cast<std::int64_t>(handle));
    return it != sessions.end() ? it->second : nullptr;
}

}  // namespace

std::shared_ptr<WaveAudioPipeline> bgWaveSessionPipeline(std::int64_t sessionHandle) {
    try {
        auto session = lookupSession(static_cast<jlong>(sessionHandle));
        return session ? session->pipeline : nullptr;
    } catch (...) {
        return nullptr;
    }
}

namespace {

// A host PCM tap keeps its own copy of the session pipeline, so pushes from the ExoPlayer render thread
// stay valid even if the session is destroyed in the meantime.
struct HostTap {
    std::shared_ptr<WaveAudioPipeline> pipeline;
};
std::mutex tapMutex;
std::map<std::int64_t, std::shared_ptr<HostTap>> hostTaps;
std::int64_t nextTapId = 1;

struct FloatArrayGuard {
    JNIEnv* env;
    jfloatArray array;
    jfloat* data;
    jint mode;

    ~FloatArrayGuard() {
        if (data) env->ReleaseFloatArrayElements(array, data, mode);
    }
};

// Sanitizes arbitrary C++ error/status strings to printable ASCII/whitespace so ART CheckJNI's
// Modified-UTF-8 check never aborts on 4-byte UTF-8 sequences or invalid bytes.
jstring newSafeString(JNIEnv* env, const std::string& text) {
    std::string safe;
    safe.reserve(text.size());
    for (unsigned char c : text) {
        if ((c >= 0x20 && c <= 0x7E) || c == '\t' || c == '\n' || c == '\r') {
            safe.push_back(static_cast<char>(c));
        } else {
            safe.push_back('?');
        }
    }
    jstring out = env->NewStringUTF(safe.c_str());
    if (!out) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        out = env->NewStringUTF("");
    }
    return out;
}

bool toStd(JNIEnv* env, jstring value, std::string& out) {
    out.clear();
    if (!value) return false;
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return false;
    }
    try {
        out.assign(chars);
    } catch (...) {
        env->ReleaseStringUTFChars(value, chars);
        throw;
    }
    env->ReleaseStringUTFChars(value, chars);
    return true;
}
#endif

}  // namespace

extern "C" {

// role: 0 = Host, 1 = Listener. Returns 0 when the P2P build is not compiled in.
JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeCreate(JNIEnv*, jclass, jint role,
                                                                      jint bitrateBps) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = std::make_shared<Session>();
        WebRtcTransportConfig transportConfig;
        transportConfig.role = role == 0 ? WaveRole::Host : WaveRole::Listener;
        auto transport = std::make_shared<WebRtcWaveTransport>(transportConfig);
        const auto bitrate = static_cast<std::uint32_t>(bitrateBps > 0 ? bitrateBps : kWaveDefaultBitrateBps);
        auto codec = std::make_shared<OpusWaveCodec>(bitrate);
        WavePipelineConfig config;
        config.role = transportConfig.role;
        config.targetBitrateBps = bitrate;
        session->pipeline = std::make_shared<WaveAudioPipeline>(config, codec, transport);

        std::weak_ptr<Session> weakSession(session);
        WavePipelineCallbacks callbacks;
        callbacks.onOutboundSignal = [weakSession](const WaveSignalMessage& signal) {
            auto s = weakSession.lock();
            if (!s) return;
            try {
                std::string error;
                const std::string uri = encodeWaveUri(signal, &error);
                std::lock_guard<std::mutex> lock(s->mutex);
                if (uri.empty()) {
                    s->lastError = "encode:" + error;
                    return;
                }
                s->outbound.push_back(uri);
            } catch (const std::exception& e) {
                std::lock_guard<std::mutex> lock(s->mutex);
                s->lastError = std::string("outbound:") + e.what();
            } catch (...) {
                std::lock_guard<std::mutex> lock(s->mutex);
                s->lastError = "outbound:unknown";
            }
        };
        callbacks.onError = [weakSession](const WaveError& error) {
            auto s = weakSession.lock();
            if (!s) return;
            try {
                std::lock_guard<std::mutex> lock(s->mutex);
                s->lastError = error.message;
            } catch (...) {
            }
        };
        session->pipeline->setCallbacks(std::move(callbacks));
        session->pumpRunning.store(true, std::memory_order_release);
        session->pump = std::thread([weakSession]() {
            const auto origin = std::chrono::steady_clock::now();
            while (true) {
                auto s = weakSession.lock();
                if (!s || !s->pumpRunning.load(std::memory_order_acquire)) {
                    break;
                }
                try {
                    const double now =
                        std::chrono::duration<double>(std::chrono::steady_clock::now() - origin).count();
                    if (s->pipeline) {
                        s->pipeline->process(now);
                    }
                } catch (const std::exception& e) {
                    std::lock_guard<std::mutex> lock(s->mutex);
                    s->lastError = std::string("pump:") + e.what();
                } catch (...) {
                    std::lock_guard<std::mutex> lock(s->mutex);
                    s->lastError = "pump:unknown";
                }
                s.reset();
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
        });

        std::lock_guard<std::mutex> lock(sessionsMutex);
        if (nextSessionId <= 0) nextSessionId = 1;
        const std::int64_t id = nextSessionId++;
        sessions.emplace(id, std::move(session));
        return static_cast<jlong>(id);
    } catch (...) {
        return 0;
    }
#else
    (void)role;
    (void)bitrateBps;
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeDestroy(JNIEnv*, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    try {
        std::shared_ptr<Session> session;
        {
            if (handle <= 0) return;
            std::lock_guard<std::mutex> lock(sessionsMutex);
            auto it = sessions.find(static_cast<std::int64_t>(handle));
            if (it == sessions.end()) return;
            session = std::move(it->second);
            sessions.erase(it);
        }
        try {
            if (session->pipeline) {
                session->pipeline->stop(false, 0);
            }
        } catch (...) {
        }
        session->pumpRunning.store(false, std::memory_order_release);
        if (session->pump.joinable() && session->pump.get_id() != std::this_thread::get_id()) {
            session->pump.join();
        }
        // The mix engine may still hold the pipeline as its PCM tap. Drop the callbacks that point at
        // this Session before releasing it, so a late render or transport event cannot reach freed memory.
        if (session->pipeline) {
            session->pipeline->setCallbacks(WavePipelineCallbacks{});
        }
    } catch (...) {
    }
#else
    (void)handle;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStartOffer(JNIEnv*, jclass, jlong handle,
                                                                          jlong nowSeconds,
                                                                          jlong ttlSeconds) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return JNI_FALSE;
        return session->pipeline->startOffer(nowSeconds, ttlSeconds) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
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
    try {
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return JNI_FALSE;
        std::string uri;
        if (!toStd(env, jUri, uri)) {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->lastError = "decode:invalid_uri_string";
            return JNI_FALSE;
        }
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
    } catch (const std::exception&) {
        return JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
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
    try {
        auto session = lookupSession(handle);
        std::string next;
        if (session) {
            std::lock_guard<std::mutex> lock(session->mutex);
            if (!session->outbound.empty()) {
                next = std::move(session->outbound.front());
                session->outbound.pop_front();
            }
        }
        return newSafeString(env, next);
    } catch (...) {
        return env->NewStringUTF("");
    }
#else
    (void)handle;
    return env->NewStringUTF("");
#endif
}

// Returns "state|route|lastError" for the Kotlin side to map onto UI states.
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStatus(JNIEnv* env, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return env->NewStringUTF("Failed|None|no session");
        std::string lastError;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            lastError = session->lastError;
        }
        const std::string text = std::string(toString(session->pipeline->state())) + "|" +
                                 toString(session->pipeline->route()) + "|" + lastError;
        return newSafeString(env, text);
    } catch (...) {
        return env->NewStringUTF("Failed|None|internal");
    }
#else
    (void)handle;
    return env->NewStringUTF("Failed|None|p2p-not-built");
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStop(JNIEnv*, jclass, jlong handle,
                                                                    jboolean emitEnd, jlong nowSeconds) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        if (session && session->pipeline) {
            session->pipeline->stop(emitEnd == JNI_TRUE, nowSeconds);
        }
    } catch (...) {
    }
#else
    (void)handle; (void)emitEnd; (void)nowSeconds;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeReset(JNIEnv*, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        if (session && session->pipeline) {
            session->pipeline->reset();
        }
    } catch (...) {
    }
#else
    (void)handle;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeReconnect(JNIEnv*, jclass, jlong handle,
                                                                         jlong nowSeconds) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        return (session && session->pipeline && session->pipeline->reconnect(nowSeconds)) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
#else
    (void)handle; (void)nowSeconds;
    return JNI_FALSE;
#endif
}

// Stats as "sent|received|lost|plc|jitterMs|bitrateBps|depthMs".
JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeStats(JNIEnv* env, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return env->NewStringUTF("0|0|0|0|0|0|0");
        const WaveStats stats = session->pipeline->stats();
        const std::string text = std::to_string(stats.packetsSent) + "|" +
                                 std::to_string(stats.packetsReceived) + "|" +
                                 std::to_string(stats.packetsLost) + "|" + std::to_string(stats.plcFrames) + "|" +
                                 std::to_string(stats.jitterMs) + "|" + std::to_string(stats.targetBitrateBps) + "|" +
                                 std::to_string(stats.jitterBufferDepthMs);
        return newSafeString(env, text);
    } catch (...) {
        return env->NewStringUTF("0|0|0|0|0|0|0");
    }
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
    try {
        if (!pcm || frames <= 0 || frames > std::numeric_limits<jint>::max() / 2 || sampleRate <= 0) return;
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return;
        const jsize length = env->GetArrayLength(pcm);
        if (length < frames * 2) return;
        jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
        if (!data) return;
        FloatArrayGuard guard{env, pcm, data, JNI_ABORT};
        session->pipeline->pushHostPcm(data, static_cast<std::size_t>(frames),
                                       static_cast<std::uint32_t>(sampleRate));
    } catch (...) {
    }
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
    try {
        if (!out || frames <= 0 || frames > std::numeric_limits<jint>::max() / 2 || sampleRate <= 0) return 0;
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return 0;
        const jsize length = env->GetArrayLength(out);
        if (length < frames * 2) return 0;
        jfloat* data = env->GetFloatArrayElements(out, nullptr);
        if (!data) return 0;
        FloatArrayGuard guard{env, out, data, JNI_ABORT};
        const std::size_t written = session->pipeline->pullListenerPcm(
            data, static_cast<std::size_t>(frames), static_cast<std::uint32_t>(sampleRate));
        guard.mode = 0;
        return static_cast<jint>(written);
    } catch (...) {
        return 0;
    }
#else
    (void)env; (void)handle; (void)out; (void)frames; (void)sampleRate;
    return 0;
#endif
}

// Opens a host PCM tap on a live session and returns its id, or 0.
JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeOpenHostTap(JNIEnv*, jclass, jlong handle) {
#if BGWAVE_SESSION_ENABLED
    try {
        auto session = lookupSession(handle);
        if (!session || !session->pipeline) return 0;
        auto tap = std::make_shared<HostTap>();
        tap->pipeline = session->pipeline;
        std::lock_guard<std::mutex> lock(tapMutex);
        if (nextTapId <= 0) nextTapId = 1;
        const std::int64_t id = nextTapId++;
        hostTaps[id] = std::move(tap);
        return static_cast<jlong>(id);
    } catch (...) {
        return 0;
    }
#else
    (void)handle;
    return 0;
#endif
}

// Pushes interleaved stereo float PCM into the tap's session. Returns false for an unknown or closed tap.
JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativePushHostTap(JNIEnv* env, jclass, jlong tapId,
                                                                           jfloatArray pcm, jint frames,
                                                                           jint sampleRate) {
#if BGWAVE_SESSION_ENABLED
    try {
        if (!pcm || frames <= 0 || frames > std::numeric_limits<jint>::max() / 2 || sampleRate <= 0) {
            return JNI_FALSE;
        }
        std::shared_ptr<HostTap> tap;
        {
            std::lock_guard<std::mutex> lock(tapMutex);
            auto it = hostTaps.find(static_cast<std::int64_t>(tapId));
            if (it == hostTaps.end()) return JNI_FALSE;
            tap = it->second;
        }
        if (!tap || !tap->pipeline || env->GetArrayLength(pcm) < frames * 2) return JNI_FALSE;
        jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
        if (!data) return JNI_FALSE;
        FloatArrayGuard guard{env, pcm, data, JNI_ABORT};
        tap->pipeline->pushHostPcm(data, static_cast<std::size_t>(frames),
                                   static_cast<std::uint32_t>(sampleRate));
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
#else
    (void)env; (void)tapId; (void)pcm; (void)frames; (void)sampleRate;
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgwave_BgWaveSessionNative_nativeCloseHostTap(JNIEnv*, jclass, jlong tapId) {
#if BGWAVE_SESSION_ENABLED
    try {
        std::lock_guard<std::mutex> lock(tapMutex);
        hostTaps.erase(static_cast<std::int64_t>(tapId));
    } catch (...) {
    }
#else
    (void)tapId;
#endif
}

}  // extern "C"
