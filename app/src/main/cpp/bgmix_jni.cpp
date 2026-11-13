// BG Mix bridge for the experimental music playback path.
//
// Wraps bgmedia::mix::MixEngine + MixSession (BG Mix 0.3) for Kotlin. The host
// owns the queue and transport from the control thread; a separate Java thread
// pulls interleaved stereo float frames through nativeRender() and writes them
// to AudioTrack. Nothing here touches voice messages, video notes or the
// ExoPlayer path.
//
// Strings cross the boundary as UTF-8 byte arrays, not jstring: NewStringUTF
// expects "modified" UTF-8 and breaks on 4-byte sequences such as emoji in
// track titles or file names.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <functional>
#include <iomanip>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

#define BGMIX_LOG_TAG "BgMixJNI"
#define BGMIX_LOGW(...) __android_log_print(ANDROID_LOG_WARN, BGMIX_LOG_TAG, __VA_ARGS__)

#include "wave/wave_pcm_tap.h"

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
#include "bgmedia/audio/MiniaudioMixLoader.hpp"
#include "bgmedia/mix/MixEngine.hpp"
#include "bgmedia/mix/MixSession.hpp"
#endif

namespace {

constexpr std::uint32_t kMixSampleRate = 48000;
// About 1.3 s at 48 kHz: enough slack for a network sender to fall behind briefly.
constexpr std::size_t kWaveTapCapacityFrames = 65536;

jbyteArray stringToBytes(JNIEnv* env, std::string_view text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out && !text.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte*>(text.data()));
    }
    return out;
}

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA

std::string bytesToString(JNIEnv* env, jbyteArray bytes) {
    if (!bytes) return {};
    const jsize size = env->GetArrayLength(bytes);
    std::string out(static_cast<std::size_t>(size), '\0');
    if (size > 0) {
        env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(out.data()));
    }
    return out;
}

// Escapes control characters and quotes. Valid UTF-8 above 0x7f is copied as is.
std::string escapeJson(std::string_view s) {
    std::ostringstream o;
    for (char c : s) {
        switch (c) {
            case '"': o << "\\\""; break;
            case '\\': o << "\\\\"; break;
            case '\b': o << "\\b"; break;
            case '\f': o << "\\f"; break;
            case '\n': o << "\\n"; break;
            case '\r': o << "\\r"; break;
            case '\t': o << "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    o << "\\u" << std::hex << std::setw(4) << std::setfill('0')
                      << static_cast<int>(static_cast<unsigned char>(c)) << std::dec << std::setfill(' ');
                } else {
                    o << c;
                }
        }
    }
    return o.str();
}

using bgmedia::mix::MixSessionSnapshot;
using bgmedia::mix::QueueItemStatus;
using bgmedia::mix::QueueState;

const char* queueStateName(QueueState state) {
    switch (state) {
        case QueueState::Pending: return "pending";
        case QueueState::Preparing: return "preparing";
        case QueueState::Ready: return "ready";
        case QueueState::Failed: return "failed";
        case QueueState::Playing: return "playing";
        case QueueState::Played: return "played";
    }
    return "pending";
}

// Host state behind the Java handle. The control mutex serialises every call
// that touches the session (queue, transport, update, status). nativeRender()
// takes no lock: MixEngine::render() is designed to be pumped from one audio
// thread while the control thread runs.
struct MixHost {
    std::mutex control;
    bgmedia::mix::MixEngine engine{kMixSampleRate};
    std::unique_ptr<bgmedia::mix::MixSession> session;
    // BG Wave host tap: disabled until a wave session starts; render never blocks on it.
    bgwave::PcmTap waveTap{kWaveTapCapacityFrames};

    explicit MixHost(std::filesystem::path cacheDirectory) {
        session = std::make_unique<bgmedia::mix::MixSession>(
            engine,
            [cacheDirectory](const std::filesystem::path& path,
                             bgmedia::mix::PreparedMixTrack& out, std::string& error,
                             std::function<void(float, std::string_view)> progress) {
                bgmedia::MixTrackLoadOptions options;
                options.cacheDirectory = cacheDirectory;
                options.targetSampleRate = kMixSampleRate;
                return bgmedia::prepareMixTrack(path, options, out, error, progress);
            });
    }
};

MixHost* hostFrom(jlong handle) {
    return reinterpret_cast<MixHost*>(static_cast<std::intptr_t>(handle));
}

std::string statusJson(const MixSessionSnapshot& s, bool paused) {
    std::ostringstream json;
    json << std::setprecision(6) << std::fixed;
    json << "{";
    json << "\"paused\":" << (paused ? "true" : "false") << ",";
    json << "\"master\":" << bgmedia::mix::deckIndex(s.engine.master) << ",";
    json << "\"transitionActive\":" << (s.engine.phase == bgmedia::mix::TransitionPhase::Running ? "true" : "false") << ",";
    json << "\"transitionAvailable\":" << (s.transitionAvailable ? "true" : "false") << ",";
    json << "\"transitionType\":\"" << escapeJson(bgmedia::mix::transitionTypeShortName(s.engine.transitionType)) << "\",";
    json << "\"transitionProgress\":" << s.engine.transitionProgress << ",";
    json << "\"preparingNext\":" << (s.preparingNext ? "true" : "false") << ",";
    json << "\"preparingName\":\"" << escapeJson(s.preparingName) << "\",";
    json << "\"preparingProgress\":" << s.preparingProgress << ",";
    json << "\"lastError\":\"" << escapeJson(s.lastError) << "\",";
    json << "\"status\":\"" << escapeJson(s.status) << "\",";
    json << "\"decks\":[";
    for (int i = 0; i < 2; ++i) {
        const auto& deck = s.engine.decks[i];
        if (i > 0) json << ",";
        json << "{\"loaded\":" << (deck.loaded ? "true" : "false")
             << ",\"playing\":" << (deck.playing ? "true" : "false")
             << ",\"ended\":" << (deck.ended ? "true" : "false")
             << ",\"positionSeconds\":" << deck.positionSeconds
             << ",\"durationSeconds\":" << deck.durationSeconds
             << ",\"bpm\":" << deck.bpm << "}";
    }
    json << "],\"queue\":[";
    for (std::size_t i = 0; i < s.queue.size(); ++i) {
        const QueueItemStatus& item = s.queue[i];
        if (i > 0) json << ",";
        json << "{\"id\":" << item.id
             << ",\"name\":\"" << escapeJson(item.displayName) << "\""
             << ",\"state\":\"" << queueStateName(item.state) << "\""
             << ",\"progress\":" << item.progress
             << ",\"bpm\":" << item.bpm
             << ",\"durationSeconds\":" << item.durationSeconds
             << ",\"error\":\"" << escapeJson(item.error) << "\"}";
    }
    json << "]}";
    return json.str();
}

#endif  // HAVE_BGMEDIA

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeIsAvailable(JNIEnv*, jclass) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeCreate(JNIEnv* env, jclass, jbyteArray cacheDir) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    try {
        auto* host = new MixHost(std::filesystem::path(bytesToString(env, cacheDir)));
        return static_cast<jlong>(reinterpret_cast<std::intptr_t>(host));
    } catch (const std::exception& e) {
        BGMIX_LOGW("nativeCreate failed: %s", e.what());
        return 0;
    }
#else
    (void)env;
    (void)cacheDir;
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeDestroy(JNIEnv*, jclass, jlong handle) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    // Kotlin stops the render thread before calling this, so no render() is in flight.
    delete hostFrom(handle);
#else
    (void)handle;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeAddTrack(JNIEnv* env, jclass, jlong handle, jbyteArray path) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return 0;
    std::lock_guard lock(host->control);
    return static_cast<jlong>(host->session->addTrack(std::filesystem::path(bytesToString(env, path))));
#else
    (void)env;
    (void)handle;
    (void)path;
    return 0;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeRemoveTrack(JNIEnv*, jclass, jlong handle, jlong id) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return JNI_FALSE;
    std::lock_guard lock(host->control);
    return host->session->removeTrack(static_cast<std::uint64_t>(id)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)handle;
    (void)id;
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeClearQueue(JNIEnv*, jclass, jlong handle) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return;
    std::lock_guard lock(host->control);
    host->session->clearQueue();
#else
    (void)handle;
#endif
}

// Transport: play, pause, next (planned smooth transition), stop.
JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeTransport(JNIEnv*, jclass, jlong handle, jint action) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return;
    std::lock_guard lock(host->control);
    switch (action) {
        case 0: host->session->play(); break;
        case 1: host->session->pause(); break;
        case 2: host->session->next(); break;
        case 3: host->session->stopAll(); break;
        default: break;
    }
#else
    (void)handle;
    (void)action;
#endif
}

// Seeks the master deck to seconds. Returns false while a transition is running or nothing is loaded.
JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeSeek(JNIEnv*, jclass, jlong handle, jdouble seconds) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return JNI_FALSE;
    std::lock_guard lock(host->control);
    const auto snap = host->session->snapshot();
    return host->session->seekDeck(snap.engine.master, seconds) ? JNI_TRUE : JNI_FALSE;
#else
    (void)handle;
    (void)seconds;
    return JNI_FALSE;
#endif
}

// Control tick. Must run regularly (about every 50 ms) from a non-audio thread.
JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeUpdate(JNIEnv*, jclass, jlong handle) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return;
    std::lock_guard lock(host->control);
    host->session->update();
#else
    (void)handle;
#endif
}

// Fills `out` with interleaved stereo float frames. Returns the number of frames written.
JNIEXPORT jint JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeRender(JNIEnv* env, jclass, jlong handle,
                                                             jfloatArray out, jint frames) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host || !out || frames <= 0) return 0;
    const jsize capacityFrames = env->GetArrayLength(out) / 2;
    const jsize wanted = std::min<jsize>(frames, capacityFrames);
    if (wanted <= 0) return 0;
    jfloat* data = env->GetFloatArrayElements(out, nullptr);
    if (!data) return 0;
    host->engine.render(data, static_cast<std::size_t>(wanted));
    host->waveTap.push(data, static_cast<std::size_t>(wanted));
    env->ReleaseFloatArrayElements(out, data, 0);
    return wanted;
#else
    (void)env;
    (void)handle;
    (void)out;
    (void)frames;
    return 0;
#endif
}

// UTF-8 JSON with queue, deck and transition state (see statusJson()).
JNIEXPORT jbyteArray JNICALL
Java_com_beykus_beygram_media_bgmix_BgMixNative_nativeStatus(JNIEnv* env, jclass, jlong handle) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    MixHost* host = hostFrom(handle);
    if (!host) return nullptr;
    std::lock_guard lock(host->control);
    const std::string json = statusJson(host->session->snapshot(), host->engine.paused());
    return stringToBytes(env, json);
#else
    (void)handle;
    return stringToBytes(env, R"({"unavailable":true})");
#endif
}

}  // extern "C"
