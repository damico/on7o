// Microphone capture, decoupled from the board it runs on.
#pragma once

#include <stdint.h>

namespace audio {

/**
 * Powers up the microphone, once, at boot.
 *
 * The ES8311 needs close to a second before it produces anything but zeros, so
 * the microphone is started here and left running for the lifetime of the
 * device. Starting it per capture would swallow the first word of every thought.
 */
bool begin();

/** True once the microphone is up and producing samples. */
bool ready();

/** Arms a new capture. Cheap — the microphone is already running. */
void startCapture();

/**
 * Advances the capture pipeline by one block.
 *
 * Returns a completed block of ON7O_BLOCK_SAMPLES samples, or nullptr while the
 * pipeline is still filling up. The returned pointer stays valid until the third
 * subsequent call, which is more than enough to hand it to the uploader.
 */
const int16_t* nextBlock();

/**
 * Drains the blocks still in flight after the button is released, one per call,
 * then returns nullptr. Without this the tail of every thought is lost.
 */
const int16_t* drainBlock();

}  // namespace audio
