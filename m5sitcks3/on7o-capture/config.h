// on7o capture device for the M5Stack StickS3.
//
// This is the only file you should need to edit.
#pragma once

// ---------------------------------------------------------------------------
// Bluetooth Low Energy
//
// The StickS3 is an ESP32-S3, which has no Bluetooth Classic radio, only
// BLE. It advertises as a GATT peripheral under this name; the Android
// bridge app scans for it and connects. There is no Wi-Fi and no server
// address here anymore: the phone owns the internet leg. See
// android/PROTOCOL.md for the framing the capture characteristic carries.
// ---------------------------------------------------------------------------
#define ON7O_BLE_DEVICE_NAME "on7o-sticks3-01"
#define ON7O_DEVICE_ID       "sticks3-01"

// Bytes per AUDIO_CHUNK frame payload, well under the protocol's 64 KiB
// ceiling. Actual BLE packets are much smaller than this (one per MTU);
// this only bounds how much of the capture is grouped into one frame.
#define ON7O_BLE_CHUNK_BYTES 4096

// ---------------------------------------------------------------------------
// Audio
//
// The StickS3 captures through an ES8311 codec, which M5Unified configures on
// our behalf. 16 kHz / 16-bit mono is what speech-to-text engines expect.
// ---------------------------------------------------------------------------
#define ON7O_SAMPLE_RATE 16000
#define ON7O_CHANNELS    1
#define ON7O_BITS        16

// Samples per capture block. 512 samples = 32 ms of audio = 1 kB.
#define ON7O_BLOCK_SAMPLES 512

// Software gain applied by M5Unified to each sample. The library defaults to 16,
// which clips speech at conversational distance: measured peaks hit 32752 of a
// 32768 full scale. Clipped audio costs speech-to-text accuracy, so we trade
// loudness for headroom.
#define ON7O_MIC_MAGNIFICATION 4

// Safety stop, in case the button gets held down in a pocket. Capped much
// lower than the old Wi-Fi design's 300000 ms: that design streamed each
// block out as it arrived and used a fixed, tiny amount of RAM regardless of
// duration. This one holds the whole capture in PSRAM before sending it over
// BLE, so the cap has to fit the board's 8 MB. 60 s of 16 kHz/16-bit mono is
// about 1.9 MB, comfortably within budget with room for the BLE stack.
#define ON7O_MAX_CAPTURE_MS 60000

// Anything shorter than this was a bump, not a thought. Discarded before it
// is sent, so accidental taps never become empty thoughts.
#define ON7O_MIN_CAPTURE_MS 400

// ---------------------------------------------------------------------------
// Button
//
// The StickS3 exposes two programmable buttons through M5Unified: BtnA (G11)
// and BtnB (G12). Swap this if the push-to-talk button should be the other one.
// ---------------------------------------------------------------------------
#define ON7O_BUTTON M5.BtnA
