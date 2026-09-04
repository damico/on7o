// Sends a finished capture to the paired phone over BLE, once the button is
// released. See android/PROTOCOL.md for the frame format and android/PROTOCOL.md
// "Transport" section for the GATT shape this implements.
#pragma once

#include <stddef.h>
#include <stdint.h>

namespace ble {

/** Starts advertising the on7o GATT service. Call once at boot. */
void begin();

/** True while a phone is connected. */
bool connected();

/**
 * Sends one complete capture as a CAPTURE_HEADER frame, chunked AUDIO_CHUNK
 * frames, then a CAPTURE_END frame, each as a GATT indication that blocks
 * until the phone confirms it. Returns false if the connection drops or a
 * frame is not confirmed within a few seconds, in which case the capture
 * should be treated as lost, matching how a dropped Wi-Fi upload used to be
 * treated.
 */
bool sendCapture(const int16_t* pcm, size_t sampleCount, int sampleRate, int channels, int bitsPerSample);

}  // namespace ble
