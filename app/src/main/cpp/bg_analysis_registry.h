#pragma once

// Shared, ref-counted, in-flight-deduplicated analysis results.
//
// BG Beat and the mascot both need the same track analysis. Decoding a track to
// PCM and running the analyser is the expensive part, so both consumers acquire
// the same native snapshot instead of doing the work twice.
//
// The registry is deliberately independent from JNI. Callers pass a factory
// that produces one immutable snapshot; only the first caller for a key runs it.
// Concurrent callers with the same key wait for that snapshot and then receive
// their own handle. A handle keeps the snapshot alive; release it when the UI,
// session, or manager no longer needs it.

#include <condition_variable>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA
#include "bgmedia/analysis/AnalysisResult.hpp"

namespace beygram::bg {

struct AnalysisSnapshot {
    bool ok = false;
    bool cacheHit = false;
    std::string json;
    std::shared_ptr<const bgmedia::AnalysisResult> result;
};

class AnalysisRegistry {
public:
    static AnalysisRegistry& instance();

    // `analyzer` is invoked exactly once for `key` while at least one caller is
    // interested. Returns a non-zero handle, or 0 for an invalid request or a
    // waiter cancelled through `isCancelled`. The call is synchronous: it
    // returns after the analysis is ready.
    std::int64_t acquire(const std::string& key,
                         const std::function<AnalysisSnapshot()>& analyzer,
                         const std::function<bool()>& isCancelled = {});

    // Safe from any thread. Returns an empty snapshot for an unknown handle.
    [[nodiscard]] AnalysisSnapshot snapshot(std::int64_t handle) const;
    [[nodiscard]] std::shared_ptr<const bgmedia::AnalysisResult> result(std::int64_t handle) const;

    // Safe from any thread and idempotent. The last release of a key drops the
    // cached snapshot.
    void release(std::int64_t handle);

    // Diagnostics only.
    [[nodiscard]] std::size_t liveHandles() const;

private:
    struct Entry {
        std::string key;
        mutable std::mutex mutex;
        std::condition_variable finishedCv;
        bool finished = false;
        AnalysisSnapshot snapshot;
    };

    mutable std::mutex mutex_;
    std::unordered_map<std::string, std::weak_ptr<Entry>> byKey_;
    std::unordered_map<std::int64_t, std::shared_ptr<Entry>> handles_;
    std::int64_t nextHandle_ = 0;
};

} // namespace beygram::bg
#endif
