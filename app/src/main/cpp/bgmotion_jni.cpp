#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <iomanip>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>

#include "bg_analysis_registry.h"

#define LOG_TAG "BgMotionJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA

#include "bgmedia/analysis/AnalysisResult.hpp"
#include "bgmedia/choreography/Choreography.hpp"
#include "bgmedia/motion3/MusicalTimeline.hpp"
#include "bgmedia/version.hpp"

namespace {

// Stable native -> Kotlin float contract. Keep in sync with MascotFrameLayout.
constexpr int kMascotFrameFloats = 37;

struct MascotSession {
    mutable std::mutex mutex;
    std::shared_ptr<const bgmedia::AnalysisResult> analysis;
    bgmedia::Choreography choreography;
    bgmedia::motion::MusicalTimeline timeline;
    bool cacheHit = false;
    float intensity = 1.0f;
    double playbackOffsetSeconds = 0.0;
    bool destroyed = false;
};

class SessionRegistry {
public:
    static SessionRegistry& instance() {
        static SessionRegistry registry;
        return registry;
    }

    std::int64_t add(std::shared_ptr<MascotSession> session) {
        std::lock_guard<std::mutex> lock(mutex_);
        const std::int64_t handle = ++nextHandle_;
        sessions_[handle] = std::move(session);
        return handle;
    }

    std::shared_ptr<MascotSession> get(std::int64_t handle) {
        if (handle == 0) return nullptr;
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = sessions_.find(handle);
        return it == sessions_.end() ? nullptr : it->second;
    }

    void destroy(std::int64_t handle) {
        if (handle == 0) return;
        std::shared_ptr<MascotSession> session;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            auto it = sessions_.find(handle);
            if (it == sessions_.end()) return;
            session = it->second;
            sessions_.erase(it);
        }
        // Wait for a sample/render call that already owns this session. No new
        // call can obtain the handle: it is no longer in the registry.
        std::lock_guard<std::mutex> lock(session->mutex);
        session->destroyed = true;
    }

    std::size_t liveSessions() {
        std::lock_guard<std::mutex> lock(mutex_);
        return sessions_.size();
    }

private:
    std::mutex mutex_;
    std::int64_t nextHandle_ = 0;
    std::unordered_map<std::int64_t, std::shared_ptr<MascotSession>> sessions_;
};

const char* moveName(bgmedia::MoveKind move) {
    switch (move) {
    case bgmedia::MoveKind::Idle: return "IDLE";
    case bgmedia::MoveKind::Pulse: return "PULSE";
    case bgmedia::MoveKind::Bounce: return "BOUNCE";
    case bgmedia::MoveKind::Sway: return "SWAY";
    case bgmedia::MoveKind::ArmWave: return "ARM_WAVE";
    case bgmedia::MoveKind::Spin: return "SPIN";
    case bgmedia::MoveKind::Pose: return "POSE";
    case bgmedia::MoveKind::Freeze: return "FREEZE";
    case bgmedia::MoveKind::HeadNod: return "HEAD_NOD";
    case bgmedia::MoveKind::BassDip: return "BASS_DIP";
    case bgmedia::MoveKind::AccentPose: return "ACCENT_POSE";
    }
    return "IDLE";
}

bgmedia::SectionKind sectionKindAt(const bgmedia::AnalysisResult& analysis, double time) {
    for (const auto& section : analysis.sections) {
        if (time >= section.start && time < section.end) {
            return section.kind;
        }
    }
    return bgmedia::SectionKind::Unknown;
}

float sectionEnergyAt(const bgmedia::AnalysisResult& analysis, double time) {
    for (const auto& section : analysis.sections) {
        if (time >= section.start && time < section.end) {
            return section.energy;
        }
    }
    return 0.0f;
}

float clampIntensity(float value) {
    if (!std::isfinite(value)) return 1.0f;
    return std::clamp(value, 0.5f, 1.5f);
}

float clampPoseValue(float value) {
    if (!std::isfinite(value)) return 0.0f;
    return std::clamp(value, -1.0f, 1.0f);
}

} // namespace

#endif // HAVE_BGMEDIA

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeIsAvailable(
    JNIEnv* /* env */,
    jclass /* clazz */) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeGetVersion(
    JNIEnv* env,
    jclass /* clazz */) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    std::ostringstream ss;
    ss << "BG Mascot 0.1 (BG Motion 2.0 test rig, BG Media " << BGMEDIA_VERSION_STRING << ")";
    return env->NewStringUTF(ss.str().c_str());
#else
    return env->NewStringUTF("BG Mascot 0.1 (stub - BG Media core omitted)");
#endif
}

JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeCreateSession(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong analysisHandle,
    jint mode,
    jfloat intensity) {
#if !defined(HAVE_BGMEDIA) || !HAVE_BGMEDIA
    (void)analysisHandle; (void)mode; (void)intensity;
    return 0;
#else
    if (mode != 0) {
        LOGW("BG Motion 3.x session is reserved for the future VRM presenter");
        return 0;
    }

    try {
        const auto snapshot = beygram::bg::AnalysisRegistry::instance().snapshot(
            static_cast<std::int64_t>(analysisHandle));
        if (!snapshot.result) {
            LOGE("Cannot create mascot session: analysis handle is invalid");
            return 0;
        }

        auto session = std::make_shared<MascotSession>();
        session->analysis = snapshot.result;
        session->cacheHit = snapshot.cacheHit;
        session->intensity = clampIntensity(intensity);

        bgmedia::ChoreographyStyle style;
        bgmedia::ChoreographyBuilder builder;
        session->choreography = builder.build(*session->analysis, style);
        session->timeline = bgmedia::motion::MusicalTimeline::fromAnalysis(*session->analysis);

        const auto handle = SessionRegistry::instance().add(std::move(session));
        LOGI("Created mascot session %lld for %zu beats", static_cast<long long>(handle),
             snapshot.result->beats.size());
        return static_cast<jlong>(handle);
    } catch (const std::exception& e) {
        LOGE("Exception in nativeCreateSession: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("Unknown exception in nativeCreateSession");
        return 0;
    }
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeDestroySession(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong handle) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    SessionRegistry::instance().destroy(static_cast<std::int64_t>(handle));
#else
    (void)handle;
#endif
}

JNIEXPORT jint JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeSample2D(
    JNIEnv* env,
    jclass /* clazz */,
    jlong handle,
    jdouble mediaTime,
    jfloatArray out) {
#if !defined(HAVE_BGMEDIA) || !HAVE_BGMEDIA
    (void)env; (void)handle; (void)mediaTime; (void)out;
    return 0;
#else
    if (out == nullptr || env->GetArrayLength(out) < kMascotFrameFloats) {
        return 0;
    }
    auto session = SessionRegistry::instance().get(static_cast<std::int64_t>(handle));
    if (!session) return 0;

    std::array<float, kMascotFrameFloats> values{};
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->destroyed || !session->analysis) return 0;

        const double time = std::max(0.0, mediaTime + session->playbackOffsetSeconds);
        bgmedia::ChoreographySampler sampler;
        bgmedia::CharacterPose pose = sampler.sample(session->choreography, time);

        const float scale = session->intensity;
        pose.bob = clampPoseValue(pose.bob * scale);
        pose.lean = clampPoseValue(pose.lean * scale);
        pose.armLeft = clampPoseValue(pose.armLeft * scale);
        pose.armRight = clampPoseValue(pose.armRight * scale);
        pose.legLeft = clampPoseValue(pose.legLeft * scale);
        pose.legRight = clampPoseValue(pose.legRight * scale);
        pose.rotation = clampPoseValue(pose.rotation * scale);
        pose.energy = std::clamp(pose.energy * scale, 0.0f, 1.0f);
        pose.headNod = clampPoseValue(pose.headNod * scale);
        pose.headTilt = clampPoseValue(pose.headTilt * scale);
        pose.headTurn = clampPoseValue(pose.headTurn * scale);
        pose.chestTwist = clampPoseValue(pose.chestTwist * scale);
        pose.hipShift = clampPoseValue(pose.hipShift * scale);
        pose.kneeLeft = clampPoseValue(pose.kneeLeft * scale);
        pose.kneeRight = clampPoseValue(pose.kneeRight * scale);
        pose.elbowLeft = clampPoseValue(pose.elbowLeft * scale);
        pose.elbowRight = clampPoseValue(pose.elbowRight * scale);
        pose.footLeft = clampPoseValue(pose.footLeft * scale);
        pose.footRight = clampPoseValue(pose.footRight * scale);
        pose.phase = std::clamp(pose.phase, 0.0f, 1.0f);
        pose.blend = std::clamp(pose.blend, 0.0f, 1.0f);
        pose.impact = std::clamp(pose.impact * scale, 0.0f, 1.0f);

        const auto& timeline = session->timeline;
        const bool analyzed = timeline.valid() && timeline.analyzed();
        const float phrase = timeline.valid() ? timeline.phraseLoudnessAt(time) : -1.0f;
        const float accent = timeline.valid() ? timeline.accentAt(time) : pose.impact;
        const float bass = timeline.valid() ? timeline.bassAt(time) : 0.0f;
        const float silence = timeline.valid() ? timeline.silenceWeightAt(time) : 1.0f;
        const double beat = timeline.valid() ? timeline.beatAt(time) : 0.0;
        const int beatInBar = timeline.valid() ? timeline.beatInBarAt(time) : 0;
        const int bar = timeline.valid() ? timeline.barAt(time) : 0;
        const double bpm = timeline.valid() ? timeline.bpmAt(time) : session->analysis->bpm;
        const float sectionEnergy = sectionEnergyAt(*session->analysis, time);
        const auto sectionKind = sectionKindAt(*session->analysis, time);

        values[0] = pose.bob;
        values[1] = pose.lean;
        values[2] = pose.armLeft;
        values[3] = pose.armRight;
        values[4] = pose.legLeft;
        values[5] = pose.legRight;
        values[6] = pose.rotation;
        values[7] = pose.energy;
        values[8] = pose.headNod;
        values[9] = pose.headTilt;
        values[10] = pose.headTurn;
        values[11] = pose.chestTwist;
        values[12] = pose.hipShift;
        values[13] = pose.kneeLeft;
        values[14] = pose.kneeRight;
        values[15] = pose.elbowLeft;
        values[16] = pose.elbowRight;
        values[17] = pose.footLeft;
        values[18] = pose.footRight;
        values[19] = pose.phase;
        values[20] = pose.blend;
        values[21] = pose.impact;
        values[22] = static_cast<float>(static_cast<int>(pose.move));
        values[23] = pose.accented ? 1.0f : 0.0f;
        values[24] = phrase;
        values[25] = accent;
        values[26] = bass;
        values[27] = silence;
        values[28] = static_cast<float>(beat);
        values[29] = static_cast<float>(beatInBar);
        values[30] = static_cast<float>(bar);
        values[31] = static_cast<float>(bpm);
        values[32] = sectionEnergy;
        values[33] = static_cast<float>(static_cast<int>(sectionKind));
        values[34] = analyzed ? 1.0f : 0.0f;
        values[35] = session->intensity;
        values[36] = static_cast<float>(
            timeline.valid() ? timeline.beatsPerBar() : session->analysis->beatsPerBar);
    }

    env->SetFloatArrayRegion(out, 0, kMascotFrameFloats, values.data());
    return kMascotFrameFloats;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeSetIntensity(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong handle,
    jfloat intensity) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    auto session = SessionRegistry::instance().get(static_cast<std::int64_t>(handle));
    if (!session) return;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->intensity = clampIntensity(intensity);
#else
    (void)handle; (void)intensity;
#endif
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeSetPlaybackOffset(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong handle,
    jdouble offsetSeconds) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    auto session = SessionRegistry::instance().get(static_cast<std::int64_t>(handle));
    if (!session) return;
    std::lock_guard<std::mutex> lock(session->mutex);
    session->playbackOffsetSeconds = std::clamp(
        std::isfinite(offsetSeconds) ? offsetSeconds : 0.0, -0.5, 0.5);
#else
    (void)handle; (void)offsetSeconds;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgmotion_BgMascotNative_nativeStatusJson(
    JNIEnv* env,
    jclass /* clazz */,
    jlong handle,
    jdouble mediaTime) {
#if !defined(HAVE_BGMEDIA) || !HAVE_BGMEDIA
    (void)handle; (void)mediaTime;
    return env->NewStringUTF("{\"available\":false,\"message\":\"BG Media core is omitted\"}");
#else
    auto session = SessionRegistry::instance().get(static_cast<std::int64_t>(handle));
    if (!session) {
        return env->NewStringUTF("{\"available\":false,\"message\":\"Mascot session not found\"}");
    }

    // Registry counters are read before the session lock: keep the lock order
    // registry -> session, never session -> registry.
    const std::size_t analysisHandles = beygram::bg::AnalysisRegistry::instance().liveHandles();
    const std::size_t sessions = SessionRegistry::instance().liveSessions();

    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->destroyed || !session->analysis) {
        return env->NewStringUTF("{\"available\":false,\"message\":\"Mascot session destroyed\"}");
    }

    const double time = std::max(0.0, mediaTime + session->playbackOffsetSeconds);
    const auto& timeline = session->timeline;
    bgmedia::ChoreographySampler sampler;
    const auto pose = sampler.sample(session->choreography, time);

    std::ostringstream json;
    json << std::fixed << std::setprecision(6);
    json << "{";
    json << "\"available\":true,";
    json << "\"mode\":\"2d\",";
    json << "\"cacheHit\":" << (session->cacheHit ? "true" : "false") << ",";
    json << "\"bpm\":" << session->analysis->bpm << ",";
    json << "\"duration\":" << session->analysis->info.duration << ",";
    json << "\"analyzed\":" << ((timeline.valid() && timeline.analyzed()) ? "true" : "false") << ",";
    json << "\"beatCount\":" << session->analysis->beats.size() << ",";
    json << "\"sectionCount\":" << session->analysis->sections.size() << ",";
    json << "\"intensity\":" << session->intensity << ",";
    json << "\"offsetMs\":" << (session->playbackOffsetSeconds * 1000.0) << ",";
    json << "\"mediaTime\":" << time << ",";
    json << "\"beat\":" << (timeline.valid() ? timeline.beatAt(time) : 0.0) << ",";
    json << "\"beatInBar\":" << (timeline.valid() ? timeline.beatInBarAt(time) : 0) << ",";
    json << "\"beatsPerBar\":" << (timeline.valid() ? timeline.beatsPerBar() : session->analysis->beatsPerBar) << ",";
    json << "\"bar\":" << (timeline.valid() ? timeline.barAt(time) : 0) << ",";
    json << "\"phraseLoudness\":" << (timeline.valid() ? timeline.phraseLoudnessAt(time) : -1.0f) << ",";
    json << "\"accent\":" << (timeline.valid() ? timeline.accentAt(time) : 0.0f) << ",";
    json << "\"bass\":" << (timeline.valid() ? timeline.bassAt(time) : 0.0f) << ",";
    json << "\"silenceWeight\":" << (timeline.valid() ? timeline.silenceWeightAt(time) : 1.0f) << ",";
    json << "\"sectionEnergy\":" << sectionEnergyAt(*session->analysis, time) << ",";
    json << "\"sectionKind\":" << static_cast<int>(sectionKindAt(*session->analysis, time)) << ",";
    json << "\"move\":\"" << moveName(pose.move) << "\",";
    json << "\"accented\":" << (pose.accented ? "true" : "false") << ",";
    json << "\"analysisHandles\":" << analysisHandles << ",";
    json << "\"sessions\":" << sessions;
    json << "}";
    return env->NewStringUTF(json.str().c_str());
#endif
}

} // extern "C"
