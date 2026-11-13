// Host-side check for bgwave::PcmTap. Not wired into Gradle; build with:
//   g++ -std=c++17 -Wall -Wextra -I app/src/main/cpp app/src/test/cpp/wave_pcm_tap_test.cpp -o tap_test
#include "wave/wave_pcm_tap.h"

#include <cstdio>
#include <vector>

namespace {

int failures = 0;

void check(bool condition, const char* what) {
    if (!condition) {
        std::printf("FAIL: %s\n", what);
        ++failures;
    }
}

}  // namespace

int main() {
    // Disabled tap stores nothing.
    {
        bgwave::PcmTap tap(8);
        float in[4] = {0.1f, 0.2f, 0.3f, 0.4f};
        check(tap.push(in, 2) == 0, "disabled tap must not store");
    }

    // Capacity rounds up to a power of two.
    {
        bgwave::PcmTap tap(5);
        check(tap.capacityFrames() == 8, "capacity rounds up to 8");
    }

    // FIFO order across the wrap-around point.
    {
        bgwave::PcmTap tap(4);
        tap.setEnabled(true);
        std::vector<float> a = {1, 1, 2, 2, 3, 3};   // 3 frames
        std::vector<float> out(12, 0.0f);
        check(tap.push(a.data(), 3) == 3, "push 3 frames");
        check(tap.pop(out.data(), 2) == 2, "pop 2 frames");
        std::vector<float> b = {4, 4, 5, 5, 6, 6};   // 3 frames, wraps the ring
        check(tap.push(b.data(), 3) == 3, "push wrapping frames");
        check(tap.pop(out.data(), 4) == 4, "pop remaining 4 frames");
        const float expect[8] = {3, 3, 4, 4, 5, 5, 6, 6};
        bool ok = true;
        for (int i = 0; i < 8; ++i) ok = ok && out[i] == expect[i];
        check(ok, "wrap-around keeps order");
    }

    // Overflow drops the newest frames, never blocks, and counts them.
    {
        bgwave::PcmTap tap(4);
        tap.setEnabled(true);
        std::vector<float> big(2 * 10, 0.5f);      // 10 frames into capacity 4
        check(tap.push(big.data(), 10) == 4, "overflow stores only free frames");
        check(tap.droppedFrames() == 6, "overflow counts dropped frames");
    }

    // Clipping is counted per sample.
    {
        bgwave::PcmTap tap(4);
        tap.setEnabled(true);
        float in[4] = {1.5f, -1.2f, 0.9f, -0.9f};   // two clipped samples
        tap.push(in, 2);
        check(tap.clippedSamples() == 2, "clipped samples counted");
    }

    std::printf(failures == 0 ? "ok\n" : "%d failure(s)\n", failures);
    return failures == 0 ? 0 : 1;
}
