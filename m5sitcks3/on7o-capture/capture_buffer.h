// Holds one capture's whole PCM buffer in PSRAM, for the record-then-send
// BLE transport: unlike the old Wi-Fi design, nothing leaves the device
// until the button is released, so the whole thing has to fit in memory.
#pragma once

#include <stddef.h>
#include <stdint.h>

namespace capture_buffer {

/** Allocates the PSRAM buffer, sized for ON7O_MAX_CAPTURE_MS. Call once at boot. */
bool begin();

/** Starts a new capture: the next append() writes from the beginning. */
void reset();

/** Appends one block of samples. Returns false if the buffer is full. */
bool append(const int16_t* samples, size_t count);

/** The buffer's contents so far, valid until the next reset(). */
const int16_t* data();

/** Samples appended since the last reset(). */
size_t sampleCount();

}  // namespace capture_buffer
