// Shared accessor between the BG Wave session layer and the BG Mix JNI (issue #799).
#pragma once

#include <cstdint>
#include <memory>

#if defined(HAVE_BGMEDIA) && HAVE_BGMEDIA && defined(BGWAVE_P2P) && BGWAVE_P2P
#include "bgmedia/wave/WaveAudioPipeline.hpp"

// Returns the live session's pipeline (the host PCM tap), or null for an unknown handle.
std::shared_ptr<bgmedia::wave::WaveAudioPipeline> bgWaveSessionPipeline(std::int64_t sessionHandle);
#endif
