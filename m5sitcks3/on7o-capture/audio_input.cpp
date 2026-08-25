#include "audio_input.h"

#include <M5Unified.h>

#include "config.h"

namespace audio {
namespace {

// M5Unified keeps up to two recordings queued. With three rotating buffers, the
// buffer queued two calls ago is guaranteed to be filled by the time the next
// record() returns, so capture never has to pause while a block is uploaded.
constexpr uint8_t kBuffers = 3;
constexpr uint8_t kQueueDepth = 2;

int16_t g_buffers[kBuffers][ON7O_BLOCK_SAMPLES];
uint8_t g_next = 0;
uint8_t g_primed = 0;
uint8_t g_drained = 0;
bool g_dropped_first = false;

}  // namespace

bool begin() {
  // On the StickS3 the ES8311 codec cannot record and play at the same time,
  // so the speaker has to be released before the microphone is claimed. on7o
  // never speaks back, so the speaker is simply never used.
  M5.Speaker.end();

  auto cfg = M5.Mic.config();
  cfg.magnification = ON7O_MIC_MAGNIFICATION;
  cfg.sample_rate = ON7O_SAMPLE_RATE;
  M5.Mic.config(cfg);

  startCapture();
  return M5.Mic.begin();
}

bool ready() {
  return M5.Mic.isEnabled();
}

void startCapture() {
  g_next = 0;
  g_primed = 0;
  g_drained = 0;
  g_dropped_first = false;
}

const int16_t* nextBlock() {
  if (!M5.Mic.record(g_buffers[g_next], ON7O_BLOCK_SAMPLES, ON7O_SAMPLE_RATE)) {
    return nullptr;
  }

  const int16_t* ready = nullptr;
  if (g_primed >= kQueueDepth) {
    // Two behind g_next, which for three buffers is the one immediately ahead.
    ready = g_buffers[(g_next + 1) % kBuffers];
  } else {
    g_primed++;
  }

  // The first block after an idle gap carries whatever DC was sitting in the
  // I2S DMA buffer, which lands as a ~3 ms click at full scale. Dropping 32 ms
  // of audio is a better trade than starting every thought with a pop.
  if (ready != nullptr && !g_dropped_first) {
    g_dropped_first = true;
    return nullptr;
  }

  g_next = (g_next + 1) % kBuffers;
  return ready;
}

const int16_t* drainBlock() {
  if (g_drained >= g_primed) {
    return nullptr;
  }
  while (M5.Mic.isRecording()) {
    delay(1);
  }
  const int16_t* ready = g_buffers[(g_next + 1 + g_drained) % kBuffers];
  g_drained++;
  return ready;
}

}  // namespace audio
