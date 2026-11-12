#include "bg_analysis_registry.h"

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA

#include <condition_variable>
#include <mutex>
#include <unordered_map>
#include <utility>

namespace beygram::bg {

AnalysisRegistry& AnalysisRegistry::instance() {
    static AnalysisRegistry registry;
    return registry;
}

std::int64_t AnalysisRegistry::acquire(const std::string& key,
                                       const std::function<AnalysisSnapshot()>& analyzer) {
    if (key.empty() || !analyzer) {
        return 0;
    }

    std::shared_ptr<Entry> entry;
    bool owner = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = byKey_.find(key);
        if (it != byKey_.end()) {
            entry = it->second.lock();
        }
        if (!entry) {
            entry = std::make_shared<Entry>();
            entry->key = key;
            byKey_[key] = entry;
            owner = true;
        }
    }

    if (owner) {
        AnalysisSnapshot snapshot;
        try {
            snapshot = analyzer();
        } catch (...) {
            // The JNI layer turns an empty JSON snapshot into an error result.
            snapshot = AnalysisSnapshot{};
        }
        {
            std::lock_guard<std::mutex> lock(entry->mutex);
            entry->snapshot = std::move(snapshot);
            entry->finished = true;
        }
        entry->finishedCv.notify_all();
    } else {
        std::unique_lock<std::mutex> lock(entry->mutex);
        entry->finishedCv.wait(lock, [&entry] { return entry->finished; });
    }

    std::lock_guard<std::mutex> lock(mutex_);
    const std::int64_t handle = ++nextHandle_;
    handles_[handle] = entry;
    return handle;
}

AnalysisSnapshot AnalysisRegistry::snapshot(std::int64_t handle) const {
    std::shared_ptr<Entry> entry;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = handles_.find(handle);
        if (it == handles_.end()) {
            return {};
        }
        entry = it->second;
    }
    std::lock_guard<std::mutex> lock(entry->mutex);
    return entry->snapshot;
}

std::shared_ptr<const bgmedia::AnalysisResult> AnalysisRegistry::result(std::int64_t handle) const {
    std::shared_ptr<Entry> entry;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = handles_.find(handle);
        if (it == handles_.end()) {
            return nullptr;
        }
        entry = it->second;
    }
    std::lock_guard<std::mutex> lock(entry->mutex);
    return entry->snapshot.result;
}

void AnalysisRegistry::release(std::int64_t handle) {
    if (handle == 0) {
        return;
    }

    std::shared_ptr<Entry> entry;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = handles_.find(handle);
        if (it == handles_.end()) {
            return;
        }
        entry = it->second;
        handles_.erase(it);

        // No handle points at the entry any more. The local `entry` is the last
        // owner; the weak index must not keep a dead analysis reachable.
        if (entry.use_count() == 1) {
            auto byKey = byKey_.find(entry->key);
            if (byKey != byKey_.end() && byKey->second.lock() == entry) {
                byKey_.erase(byKey);
            }
        }
    }
}

std::size_t AnalysisRegistry::liveHandles() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return handles_.size();
}

} // namespace beygram::bg

#endif // HAVE_BGMEDIA
