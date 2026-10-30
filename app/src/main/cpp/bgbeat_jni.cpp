#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <filesystem>
#include <iomanip>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#define LOG_TAG "BgBeatJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct JniStringGuard {
    JNIEnv* env;
    jstring jstr;
    const char* utfChars;

    JniStringGuard(JNIEnv* e, jstring s)
        : env(e), jstr(s), utfChars(s ? e->GetStringUTFChars(s, nullptr) : nullptr) {}

    ~JniStringGuard() {
        if (utfChars && jstr) {
            env->ReleaseStringUTFChars(jstr, utfChars);
        }
    }

    [[nodiscard]] const char* c_str() const noexcept { return utfChars ? utfChars : ""; }
    [[nodiscard]] bool empty() const noexcept { return utfChars == nullptr || utfChars[0] == '\0'; }
};

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
                if (static_cast<unsigned char>(c) <= 0x1f) {
                    o << "\\u" << std::hex << std::setw(4) << std::setfill('0') << static_cast<int>(c);
                } else {
                    o << c;
                }
        }
    }
    return o.str();
}

std::string makeErrorJson(std::string_view message, std::string_view status = "error") {
    std::ostringstream ss;
    ss << "{\n";
    ss << "  \"status\": \"" << escapeJson(status) << "\",\n";
    ss << "  \"message\": \"" << escapeJson(message) << "\"\n";
    ss << "}\n";
    return ss.str();
}

// Thread-safe registry for cancellation tokens.
// Eliminates Use-After-Free and race conditions across concurrent analyze/cancel/destroy calls.
class CancelRegistry {
public:
    static CancelRegistry& instance() {
        static CancelRegistry reg;
        return reg;
    }

    int64_t create() {
        std::lock_guard<std::mutex> lock(mutex_);
        int64_t id = ++nextId_;
        tokens_[id] = std::make_shared<std::atomic<bool>>(false);
        return id;
    }

    std::shared_ptr<std::atomic<bool>> get(int64_t id) {
        if (id <= 0) return nullptr;
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = tokens_.find(id);
        return (it != tokens_.end()) ? it->second : nullptr;
    }

    void cancel(int64_t id) {
        if (id <= 0) return;
        std::shared_ptr<std::atomic<bool>> token;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            auto it = tokens_.find(id);
            if (it != tokens_.end()) {
                token = it->second;
            }
        }
        if (token) {
            token->store(true, std::memory_order_relaxed);
        }
    }

    void destroy(int64_t id) {
        if (id <= 0) return;
        std::lock_guard<std::mutex> lock(mutex_);
        tokens_.erase(id);
    }

private:
    std::mutex mutex_;
    int64_t nextId_ = 0;
    std::unordered_map<int64_t, std::shared_ptr<std::atomic<bool>>> tokens_;
};

} // namespace

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA

#include "bgmedia/analysis/Analyzer.hpp"
#include "bgmedia/analysis/AnalysisConfig.hpp"
#include "bgmedia/analysis/AnalysisResult.hpp"
#include "bgmedia/cache/AnalysisCache.hpp"
#include "bgmedia/audio/AudioData.hpp"
#include "bgmedia/version.hpp"
#include "miniaudio.h"

namespace {

std::string formatHex64(std::uint64_t val) {
    std::ostringstream ss;
    ss << std::hex << std::setw(16) << std::setfill('0') << val;
    return ss.str();
}

std::string serializeResultToJson(
    const bgmedia::AnalysisResult& r,
    bool cacheHit,
    std::size_t maxWaveformBins = 400
) {
    std::ostringstream json;
    json << std::setprecision(6) << std::fixed;
    json << "{\n";
    json << "  \"status\": \"ok\",\n";
    json << "  \"cacheHit\": " << (cacheHit ? "true" : "false") << ",\n";
    json << "  \"schemaVersion\": " << r.info.schemaVersion << ",\n";
    json << "  \"analyzerVersion\": " << r.info.analyzerVersion << ",\n";
    json << "  \"contentHash\": \"" << formatHex64(r.info.contentHash) << "\",\n";
    json << "  \"configHash\": \"" << formatHex64(r.info.configHash) << "\",\n";
    json << "  \"duration\": " << r.info.duration << ",\n";
    json << "  \"sampleRate\": " << r.info.sampleRate << ",\n";
    json << "  \"sourceChannels\": " << r.info.sourceChannels << ",\n";
    json << "  \"bpm\": " << r.bpm << ",\n";
    json << "  \"bpmConfidence\": " << r.bpmConfidence << ",\n";
    json << "  \"bpmLevelConfidence\": " << r.bpmLevelConfidence << ",\n";
    json << "  \"beatPhaseConfidence\": " << r.beatPhaseConfidence << ",\n";
    json << "  \"downbeatConfidence\": " << r.downbeatConfidence << ",\n";
    json << "  \"tempoStable\": " << (r.tempoStable ? "true" : "false") << ",\n";
    json << "  \"octaveAmbiguous\": " << (r.octaveAmbiguous ? "true" : "false") << ",\n";
    json << "  \"beatsPerBar\": " << r.beatsPerBar << ",\n";
    json << "  \"meterConfidence\": " << r.meterConfidence << ",\n";
    json << "  \"analysisSeconds\": " << r.analysisSeconds << ",\n";
    json << "  \"activeRatio\": " << r.activeRatio << ",\n";

    // BPM candidates
    json << "  \"bpmCandidates\": [";
    for (std::size_t i = 0; i < r.bpmCandidates.size(); ++i) {
        if (i > 0) json << ", ";
        float score = (i < r.bpmCandidateScores.size()) ? r.bpmCandidateScores[i] : 0.0f;
        json << "{\"bpm\": " << r.bpmCandidates[i] << ", \"score\": " << score << "}";
    }
    json << "],\n";

    // Beats
    json << "  \"beats\": [";
    for (std::size_t i = 0; i < r.beats.size(); ++i) {
        if (i > 0) json << ", ";
        const auto& b = r.beats[i];
        json << "{\"time\": " << b.time
             << ", \"strength\": " << b.strength
             << ", \"confidence\": " << b.confidence
             << ", \"beatInBar\": " << b.beatInBar
             << ", \"barIndex\": " << b.barIndex
             << ", \"downbeat\": " << (b.downbeat ? "true" : "false")
             << ", \"extrapolated\": " << (b.extrapolated ? "true" : "false") << "}";
    }
    json << "],\n";

    // Waveform: resampled to maxWaveformBins for snappy, efficient rendering
    json << "  \"waveform\": [";
    if (!r.waveform.empty()) {
        const std::size_t bins = std::min(maxWaveformBins, r.waveform.size());
        const double factor = static_cast<double>(r.waveform.size()) / static_cast<double>(bins);
        for (std::size_t i = 0; i < bins; ++i) {
            if (i > 0) json << ", ";
            const std::size_t startIdx = static_cast<std::size_t>(i * factor);
            const std::size_t endIdx = std::min(r.waveform.size(), static_cast<std::size_t>((i + 1) * factor));
            float minVal = 0.0f;
            float maxVal = 0.0f;
            for (std::size_t j = startIdx; j < endIdx; ++j) {
                minVal = std::min(minVal, r.waveform[j].minValue);
                maxVal = std::max(maxVal, r.waveform[j].maxValue);
            }
            json << "{\"min\": " << minVal << ", \"max\": " << maxVal << "}";
        }
    }
    json << "],\n";

    json << "  \"waveformPeaksPerSecond\": " << r.waveformPeaksPerSecond << "\n";
    json << "}\n";
    return json.str();
}

struct DecodeChunkResult {
    bool ok = false;
    bool cancelled = false;
    std::string error = "";
    bgmedia::AudioData audio = {};
};

// Cooperative chunk-based PCM decoding using miniaudio.
// Distinguishes regular EOF from decoder read errors.
// Never treats truncated/corrupted streams as successful decoding.
DecodeChunkResult decodeAudioWithCancellation(
    const std::filesystem::path& path,
    const std::function<bool()>& isCancelled,
    const std::function<void(float, const std::string&)>& reportProgress
) {
    constexpr std::size_t kMaxDecodedBytes = 1024ull * 1024ull * 1024ull; // 1 GiB limit
    constexpr ma_uint64 kBlockFrames = 8192;

    if (isCancelled && isCancelled()) {
        return {.ok = false, .cancelled = true};
    }

    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    ma_decoder decoder{};
    const std::string utf8Path = path.string();
    const ma_result initResult = ma_decoder_init_file(utf8Path.c_str(), &config, &decoder);
    if (initResult != MA_SUCCESS) {
        return {
            .ok = false,
            .cancelled = false,
            .error = std::string("miniaudio failed to open file: ") + ma_result_description(initResult)
        };
    }

    const ma_uint32 channels = decoder.outputChannels;
    const ma_uint32 sampleRate = decoder.outputSampleRate;
    if (channels == 0 || sampleRate == 0) {
        ma_decoder_uninit(&decoder);
        return {.ok = false, .cancelled = false, .error = "invalid channels or sample rate in audio file"};
    }

    ma_uint64 lengthFrames = 0;
    const bool haveLength =
        (ma_decoder_get_length_in_pcm_frames(&decoder, &lengthFrames) == MA_SUCCESS) && (lengthFrames > 0);

    std::vector<float> samples;
    if (haveLength) {
        const std::uint64_t estimated = static_cast<std::uint64_t>(lengthFrames) * channels;
        const std::uint64_t maxFloats = kMaxDecodedBytes / sizeof(float);
        if (estimated > maxFloats) {
            ma_decoder_uninit(&decoder);
            return {.ok = false, .cancelled = false, .error = "audio file too large for in-memory decoding"};
        }
        samples.reserve(static_cast<std::size_t>(estimated));
    }

    std::vector<float> block(static_cast<std::size_t>(kBlockFrames) * channels);
    ma_uint64 framesReadTotal = 0;

    while (true) {
        if (isCancelled && isCancelled()) {
            ma_decoder_uninit(&decoder);
            return {.ok = false, .cancelled = true};
        }

        ma_uint64 framesRead = 0;
        const ma_result readRes = ma_decoder_read_pcm_frames(&decoder, block.data(), kBlockFrames, &framesRead);

        if (framesRead > 0) {
            const std::size_t oldSize = samples.size();
            if (oldSize + framesRead * channels > kMaxDecodedBytes / sizeof(float)) {
                ma_decoder_uninit(&decoder);
                return {.ok = false, .cancelled = false, .error = "decoded audio exceeds 1 GiB memory limit"};
            }
            samples.resize(oldSize + static_cast<std::size_t>(framesRead) * channels);
            std::copy_n(block.data(), static_cast<std::size_t>(framesRead) * channels, samples.data() + oldSize);

            framesReadTotal += framesRead;
            if (haveLength && lengthFrames > 0 && reportProgress) {
                float frac = std::clamp(static_cast<float>(framesReadTotal) / static_cast<float>(lengthFrames), 0.0f, 1.0f);
                reportProgress(0.0f + 0.15f * frac, "Decoding audio");
            }
        }

        // Distinct handling of EOF vs actual decoder errors
        if (readRes != MA_SUCCESS && readRes != MA_AT_END) {
            ma_decoder_uninit(&decoder);
            return {
                .ok = false,
                .cancelled = false,
                .error = std::string("miniaudio decode error: ") + ma_result_description(readRes)
            };
        }

        if (readRes == MA_AT_END || framesRead == 0) {
            // Normal end-of-file reached
            break;
        }
    }

    ma_decoder_uninit(&decoder);

    if (samples.empty() || framesReadTotal == 0) {
        return {.ok = false, .cancelled = false, .error = "decoded audio stream contains 0 frames"};
    }

    // Guard against prematurely truncated audio streams
    if (haveLength && lengthFrames > 1000 && framesReadTotal < (lengthFrames / 2)) {
        return {
            .ok = false,
            .cancelled = false,
            .error = "audio file is corrupted or prematurely truncated"
        };
    }

    bgmedia::AudioData audioData{
        .samples = std::move(samples),
        .channels = channels,
        .sampleRate = sampleRate
    };

    return {.ok = true, .cancelled = false, .error = "", .audio = std::move(audioData)};
}

} // namespace

#endif // HAVE_BGMEDIA

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgbeat_BgBeatNative_nativeGetVersion(
    JNIEnv* env,
    jclass /* clazz */) {
#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
    std::ostringstream ss;
    ss << "BG Beat " << BGMEDIA_VERSION_STRING
       << " (schema " << bgmedia::kAnalysisSchemaVersion
       << ", analyzer " << bgmedia::kAnalyzerVersion << ")";
    return env->NewStringUTF(ss.str().c_str());
#else
    return env->NewStringUTF("BG Beat 0.1 (stub - private core omitted)");
#endif
}

JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_media_bgbeat_BgBeatNative_nativeCreateCancelToken(
    JNIEnv* /* env */,
    jclass /* clazz */) {
    return static_cast<jlong>(CancelRegistry::instance().create());
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgbeat_BgBeatNative_nativeCancel(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong tokenHandle) {
    CancelRegistry::instance().cancel(static_cast<int64_t>(tokenHandle));
}

JNIEXPORT void JNICALL
Java_com_beykus_beygram_media_bgbeat_BgBeatNative_nativeDestroyCancelToken(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong tokenHandle) {
    CancelRegistry::instance().destroy(static_cast<int64_t>(tokenHandle));
}

JNIEXPORT jstring JNICALL
Java_com_beykus_beygram_media_bgbeat_BgBeatNative_nativeAnalyzeFile(
    JNIEnv* env,
    jclass /* clazz */,
    jstring jfilePath,
    jstring jcacheDir,
    jdouble minBpm,
    jdouble maxBpm,
    jlong cancelTokenHandle,
    jobject jprogressCallback) {

#if !defined(HAVE_BGMEDIA) || !HAVE_BGMEDIA
    (void)jfilePath; (void)jcacheDir; (void)minBpm; (void)maxBpm;
    (void)cancelTokenHandle; (void)jprogressCallback;
    std::string err = makeErrorJson("BG Media C++ core is omitted in this build (requires submodule/token)", "unsupported");
    return env->NewStringUTF(err.c_str());
#else

    JniStringGuard filePathGuard(env, jfilePath);
    JniStringGuard cacheDirGuard(env, jcacheDir);

    if (filePathGuard.empty()) {
        std::string err = makeErrorJson("File path is empty or null");
        return env->NewStringUTF(err.c_str());
    }

    std::shared_ptr<std::atomic<bool>> cancelToken =
        CancelRegistry::instance().get(static_cast<int64_t>(cancelTokenHandle));

    auto isCancelled = [cancelToken]() -> bool {
        return cancelToken != nullptr && cancelToken->load(std::memory_order_relaxed);
    };

    if (isCancelled()) {
        std::string cancelled = makeErrorJson("Analysis cancelled", "cancelled");
        return env->NewStringUTF(cancelled.c_str());
    }

    jclass callbackClass = nullptr;
    jmethodID onProgressMid = nullptr;
    if (jprogressCallback != nullptr) {
        callbackClass = env->GetObjectClass(jprogressCallback);
        if (callbackClass != nullptr) {
            onProgressMid = env->GetMethodID(callbackClass, "onProgress", "(FLjava/lang/String;)V");
        }
    }

    float lastReportedFraction = -1.0f;
    std::string lastReportedStage;
    auto reportProgress = [&](float frac, const std::string& stage) {
        if (isCancelled()) return;
        if (std::abs(frac - lastReportedFraction) >= 0.02f || stage != lastReportedStage) {
            lastReportedFraction = frac;
            lastReportedStage = stage;
            if (jprogressCallback != nullptr && onProgressMid != nullptr) {
                jstring jstage = env->NewStringUTF(stage.c_str());
                env->CallVoidMethod(jprogressCallback, onProgressMid, static_cast<jfloat>(frac), jstage);
                env->DeleteLocalRef(jstage);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                }
            }
        }
    };

    try {
        std::filesystem::path filePath(filePathGuard.c_str());
        std::error_code ec;
        if (!std::filesystem::exists(filePath, ec)) {
            std::string err = makeErrorJson("File does not exist: " + filePath.string());
            return env->NewStringUTF(err.c_str());
        }

        reportProgress(0.0f, "Decoding audio");

        // Cooperative decoding: checks cancellation every chunk and verifies EOF
        DecodeChunkResult decodeRes = decodeAudioWithCancellation(filePath, isCancelled, reportProgress);

        if (decodeRes.cancelled || isCancelled()) {
            std::string cancelled = makeErrorJson("Analysis cancelled", "cancelled");
            return env->NewStringUTF(cancelled.c_str());
        }

        if (!decodeRes.ok) {
            std::string err = makeErrorJson("Audio decoder failed: " + decodeRes.error);
            return env->NewStringUTF(err.c_str());
        }

        reportProgress(0.15f, "Preparing PCM");

        std::string pcmError;
        bgmedia::PcmBuffer pcm = bgmedia::PcmBuffer::fromAudio(decodeRes.audio, &pcmError);
        if (!pcm.valid()) {
            std::string err = makeErrorJson("Invalid PCM audio: " + pcmError);
            return env->NewStringUTF(err.c_str());
        }

        bgmedia::AnalysisConfig config;
        config.minBpm = (minBpm >= 20.0 && minBpm <= 300.0) ? minBpm : 55.0;
        config.maxBpm = (maxBpm > config.minBpm && maxBpm <= 400.0) ? maxBpm : 200.0;

        bool cacheHit = false;
        bgmedia::AnalysisResult result;

        std::filesystem::path cachePath(cacheDirGuard.c_str());
        if (!cacheDirGuard.empty()) {
            std::filesystem::create_directories(cachePath, ec);
            bgmedia::AnalysisCache cache(cachePath);
            auto cacheLoad = cache.load(pcm, config);
            if (cacheLoad.hit()) {
                result = std::move(cacheLoad.result);
                cacheHit = true;
                LOGI("BG Beat cache hit for content hash %llx", static_cast<unsigned long long>(pcm.contentHash));
            }
        }

        if (!cacheHit) {
            if (isCancelled()) {
                std::string cancelled = makeErrorJson("Analysis cancelled", "cancelled");
                return env->NewStringUTF(cancelled.c_str());
            }

            bgmedia::Analyzer analyzer(config);
            auto report = analyzer.analyze(
                pcm,
                [&](float frac, const std::string& stage) {
                    reportProgress(0.15f + 0.85f * frac, stage);
                },
                isCancelled
            );

            if (report.status == bgmedia::AnalysisStatus::Cancelled || isCancelled()) {
                std::string cancelled = makeErrorJson("Analysis cancelled", "cancelled");
                return env->NewStringUTF(cancelled.c_str());
            }

            if (report.status != bgmedia::AnalysisStatus::Ok) {
                std::string err = makeErrorJson("Analysis pipeline failed: " + report.message);
                return env->NewStringUTF(err.c_str());
            }

            result = std::move(report.result);

            if (!cacheDirGuard.empty()) {
                bgmedia::AnalysisCache cache(cachePath);
                std::string storeErr;
                if (!cache.store(result, pcm, config, &storeErr)) {
                    LOGW("Failed to store analysis in cache: %s", storeErr.c_str());
                }
            }
        }

        reportProgress(1.0f, "Completed");

        std::string jsonStr = serializeResultToJson(result, cacheHit);
        return env->NewStringUTF(jsonStr.c_str());

    } catch (const std::exception& e) {
        LOGE("Native exception in nativeAnalyzeFile: %s", e.what());
        std::string err = makeErrorJson(std::string("Native exception: ") + e.what());
        return env->NewStringUTF(err.c_str());
    } catch (...) {
        LOGE("Unknown native exception in nativeAnalyzeFile");
        std::string err = makeErrorJson("Unknown native exception in BG Beat");
        return env->NewStringUTF(err.c_str());
    }
#endif
}

} // extern "C"
