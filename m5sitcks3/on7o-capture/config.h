// on7o capture device — M5Stack StickS3.
//
// This is the only file you should need to edit.
#pragma once

// ---------------------------------------------------------------------------
// Wi-Fi
//
// Credentials live in secrets.h, which is git-ignored. Copy secrets.h.example
// to secrets.h and fill it in.
// ---------------------------------------------------------------------------
#if __has_include("secrets.h")
  #include "secrets.h"
#else
  #error "Missing secrets.h — copy secrets.h.example to secrets.h and set your Wi-Fi credentials."
#endif

#define ON7O_WIFI_TIMEOUT_MS 20000

// ---------------------------------------------------------------------------
// Server
//
// The LAN address of the machine running on7o-server. Its startup log prints
// the exact URL ("ingest endpoint: http://...").
// ---------------------------------------------------------------------------
#define ON7O_HOST      "10.109.118.30"
#define ON7O_PORT      8080
#define ON7O_PATH      "/api/thoughts/audio"
#define ON7O_DEVICE_ID "sticks3-01"

// ---------------------------------------------------------------------------
// Audio
//
// The StickS3 captures through an ES8311 codec, which M5Unified configures on
// our behalf. 16 kHz / 16-bit mono is what speech-to-text engines expect and it
// keeps the stream at 32 kB/s, comfortably within the Wi-Fi link.
// ---------------------------------------------------------------------------
#define ON7O_SAMPLE_RATE 16000
#define ON7O_CHANNELS    1
#define ON7O_BITS        16

// Samples per network chunk. 512 samples = 32 ms of audio = 1 kB per chunk.
#define ON7O_BLOCK_SAMPLES 512

// Software gain applied by M5Unified to each sample. The library defaults to 16,
// which clips speech at conversational distance — measured peaks hit 32752 of a
// 32768 full scale. Clipped audio costs speech-to-text accuracy, so we trade
// loudness for headroom.
#define ON7O_MIC_MAGNIFICATION 4

// Safety stop, in case the button gets held down in a pocket.
#define ON7O_MAX_CAPTURE_MS 300000

// Anything shorter than this was a bump, not a thought. Discarded before it
// reaches the server, so accidental taps never become empty thoughts.
#define ON7O_MIN_CAPTURE_MS 400

// ---------------------------------------------------------------------------
// Button
//
// The StickS3 exposes two programmable buttons through M5Unified: BtnA (G11)
// and BtnB (G12). Swap this if the push-to-talk button should be the other one.
// ---------------------------------------------------------------------------
#define ON7O_BUTTON M5.BtnA
