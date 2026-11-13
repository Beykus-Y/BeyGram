// BG Wave host PCM tap (issue #799, subtask 2).
//
// Copies the final interleaved stereo float frames produced by the BG Mix render
// into a fixed single-producer / single-consumer ring. The producer is the
// real-time audio thread, so push() never allocates, never locks and never
// blocks: when the consumer falls behind, the newest frames are dropped and
// counted instead of stalling the audio path.
#ifndef BEYGRAM_WAVE_PCM_TAP_H
#define BEYGRAM_WAVE_PCM_TAP_H

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

namespace bgwave {

class PcmTap {
public:
    static constexpr std::size_t kChannels = 2;

    // capacityFrames is rounded up to a power of two. All storage is allocated here,
    // never on the audio thread.
    explicit PcmTap(std::size_t capacityFrames) {
        std::size_t cap = 1;
        while (cap < capacityFrames) cap <<= 1;
        capacityFrames_ = cap;
        samples_.assign(cap * kChannels, 0.0f);
    }

    PcmTap(const PcmTap&) = delete;
    PcmTap& operator=(const PcmTap&) = delete;

    void setEnabled(bool enabled) noexcept { enabled_.store(enabled, std::memory_order_release); }
    bool enabled() const noexcept { return enabled_.load(std::memory_order_acquire); }

    // Producer side, real-time safe. Returns the number of frames stored.
    std::size_t push(const float* interleaved, std::size_t frames) noexcept {
        if (!interleaved || frames == 0 || !enabled()) return 0;

        const std::size_t write = write_.load(std::memory_order_relaxed);
        const std::size_t read = read_.load(std::memory_order_acquire);
        const std::size_t used = write - read;
        const std::size_t freeFrames = capacityFrames_ - used;
        const std::size_t stored = frames < freeFrames ? frames : freeFrames;
        if (stored < frames) {
            droppedFrames_.fetch_add(frames - stored, std::memory_order_relaxed);
        }

        std::uint64_t clipped = 0;
        for (std::size_t i = 0; i < stored * kChannels; ++i) {
            if (std::fabs(interleaved[i]) > 1.0f) ++clipped;
        }
        if (clipped) clippedSamples_.fetch_add(clipped, std::memory_order_relaxed);

        const std::size_t start = write & (capacityFrames_ - 1);
        const std::size_t firstFrames = std::min(stored, capacityFrames_ - start);
        std::memcpy(&samples_[start * kChannels], interleaved, firstFrames * kChannels * sizeof(float));
        if (stored > firstFrames) {
            std::memcpy(&samples_[0], interleaved + firstFrames * kChannels,
                        (stored - firstFrames) * kChannels * sizeof(float));
        }

        write_.store(write + stored, std::memory_order_release);
        return stored;
    }

    // Consumer side. Returns the number of frames copied into out.
    std::size_t pop(float* out, std::size_t maxFrames) noexcept {
        if (!out || maxFrames == 0) return 0;
        const std::size_t read = read_.load(std::memory_order_relaxed);
        const std::size_t write = write_.load(std::memory_order_acquire);
        const std::size_t available = write - read;
        const std::size_t frames = maxFrames < available ? maxFrames : available;

        const std::size_t start = read & (capacityFrames_ - 1);
        const std::size_t firstFrames = std::min(frames, capacityFrames_ - start);
        std::memcpy(out, &samples_[start * kChannels], firstFrames * kChannels * sizeof(float));
        if (frames > firstFrames) {
            std::memcpy(out + firstFrames * kChannels, &samples_[0],
                        (frames - firstFrames) * kChannels * sizeof(float));
        }

        read_.store(read + frames, std::memory_order_release);
        return frames;
    }

    std::size_t capacityFrames() const noexcept { return capacityFrames_; }
    std::uint64_t droppedFrames() const noexcept { return droppedFrames_.load(std::memory_order_relaxed); }
    std::uint64_t clippedSamples() const noexcept { return clippedSamples_.load(std::memory_order_relaxed); }

private:
    std::size_t capacityFrames_ = 1;
    std::vector<float> samples_;
    std::atomic<std::size_t> write_{0};
    std::atomic<std::size_t> read_{0};
    std::atomic<bool> enabled_{false};
    std::atomic<std::uint64_t> droppedFrames_{0};
    std::atomic<std::uint64_t> clippedSamples_{0};
};

}  // namespace bgwave

#endif  // BEYGRAM_WAVE_PCM_TAP_H
