// on7o thought capture for the M5Stack StickS3.
//
// Hold the button, speak, release. The audio is buffered in PSRAM while you
// talk, then sent to the paired phone over BLE once you let go; nothing is
// interpreted, answered, or acted upon here. The device is an input, and
// that is all it is.
//
// Requires M5Unified >= 0.2.12 (first version with StickS3 support).

#include <M5Unified.h>

#include "audio_input.h"
#include "ble_transport.h"
#include "capture_buffer.h"
#include "config.h"

namespace {

enum class State { Idle, Capturing, Sending, Result };

State g_state = State::Idle;
uint32_t g_capture_start = 0;
uint32_t g_result_until = 0;
uint32_t g_last_elapsed = 0;
bool g_idle_shown_connected = false;

constexpr uint32_t kResultDisplayMs = 2500;

void showIdle() {
  const bool online = ble::connected();
  g_idle_shown_connected = online;
  M5.Display.fillScreen(TFT_BLACK);
  M5.Display.setTextColor(online ? TFT_WHITE : TFT_ORANGE);
  M5.Display.drawString("on7o", M5.Display.width() / 2, M5.Display.height() / 2 - 12);
  M5.Display.setTextColor(TFT_DARKGREY);
  M5.Display.drawString(online ? "hold to speak" : "no phone",
                        M5.Display.width() / 2, M5.Display.height() / 2 + 14);
}

void showCapturing(uint32_t elapsed_s) {
  M5.Display.fillScreen(TFT_BLACK);
  M5.Display.setTextColor(TFT_RED);
  M5.Display.drawString("REC", M5.Display.width() / 2, M5.Display.height() / 2 - 12);
  M5.Display.setTextColor(TFT_WHITE);
  M5.Display.drawString(String(elapsed_s) + "s",
                        M5.Display.width() / 2, M5.Display.height() / 2 + 14);
}

void showSending() {
  M5.Display.fillScreen(TFT_BLACK);
  M5.Display.setTextColor(TFT_YELLOW);
  M5.Display.drawString("sending", M5.Display.width() / 2, M5.Display.height() / 2 - 12);
}

void showResult(bool ok, const String& detail) {
  M5.Display.fillScreen(TFT_BLACK);
  M5.Display.setTextColor(ok ? TFT_GREEN : TFT_RED);
  M5.Display.drawString(ok ? "sent" : "failed",
                        M5.Display.width() / 2, M5.Display.height() / 2 - 12);
  M5.Display.setTextColor(TFT_DARKGREY);
  M5.Display.drawString(detail, M5.Display.width() / 2, M5.Display.height() / 2 + 14);
}

void toResult(bool ok, const String& detail) {
  showResult(ok, detail);
  g_state = State::Result;
  g_result_until = millis() + kResultDisplayMs;
}

void startCapture() {
  if (!ble::connected()) {
    Serial.println("no phone connected");
    toResult(false, "no phone");
    return;
  }
  if (!audio::ready()) {
    Serial.println("microphone unavailable");
    toResult(false, "no mic");
    return;
  }

  capture_buffer::reset();
  audio::startCapture();
  g_capture_start = millis();
  g_last_elapsed = 0;
  g_state = State::Capturing;
  showCapturing(0);
}

/** Appends whatever the microphone has produced to the capture buffer. */
bool pumpCapture() {
  const int16_t* block = audio::nextBlock();
  const bool ok = block == nullptr || capture_buffer::append(block, ON7O_BLOCK_SAMPLES);

  const uint32_t elapsed_s = (millis() - g_capture_start) / 1000;
  if (elapsed_s != g_last_elapsed) {
    g_last_elapsed = elapsed_s;
    showCapturing(elapsed_s);
  }
  return ok;
}

void finishCapture(bool buffer_ok) {
  const uint32_t duration_ms = millis() - g_capture_start;

  if (duration_ms < ON7O_MIN_CAPTURE_MS) {
    Serial.printf("discarded %u ms capture\n", (unsigned)duration_ms);
    toResult(false, "too short");
    return;
  }

  if (!buffer_ok) {
    Serial.println("capture buffer filled before the button was released");
  }

  // Blocks still queued in the mic hold the tail of the sentence.
  const int16_t* block;
  while ((block = audio::drainBlock()) != nullptr) {
    capture_buffer::append(block, ON7O_BLOCK_SAMPLES);
  }

  const uint32_t seconds = duration_ms / 1000;

  g_state = State::Sending;
  showSending();
  const bool sent = ble::sendCapture(capture_buffer::data(), capture_buffer::sampleCount(),
                                     ON7O_SAMPLE_RATE, ON7O_CHANNELS, ON7O_BITS);
  Serial.printf("ble send finished: ok=%d samples=%u\n", sent, (unsigned)capture_buffer::sampleCount());

  if (!ble::connected()) {
    toResult(false, "lost link");
  } else if (sent) {
    toResult(true, String(seconds) + "s captured");
  } else {
    toResult(false, "send failed");
  }
}

}  // namespace

void setup() {
  auto cfg = M5.config();
  M5.begin(cfg);
  M5.Display.setRotation(1);
  M5.Display.setTextDatum(middle_center);
  M5.Display.setFont(&fonts::FreeMonoBold12pt7b);

  Serial.begin(115200);
  Serial.println("on7o capture starting");

  // Started once, here, and left running: the ES8311 takes close to a second to
  // produce anything but zeros, and paying that per capture ate the first word.
  if (!audio::begin()) {
    Serial.println("microphone failed to start");
  }
  if (!capture_buffer::begin()) {
    Serial.println("capture buffer allocation failed");
  }

  ble::begin();
  showIdle();
}

void loop() {
  M5.update();

  switch (g_state) {
    case State::Idle:
      // The screen only redraws on a state transition, but the BLE link can
      // connect or drop while sitting idle, so it is checked every loop
      // rather than left to go stale until the next button press.
      if (ble::connected() != g_idle_shown_connected) {
        showIdle();
      }
      if (ON7O_BUTTON.wasPressed()) {
        startCapture();
      }
      break;

    case State::Capturing: {
      const bool ok = pumpCapture();
      const bool too_long = millis() - g_capture_start > ON7O_MAX_CAPTURE_MS;
      if (!ok || too_long || ON7O_BUTTON.wasReleased() || !ON7O_BUTTON.isPressed()) {
        finishCapture(ok);
      }
      break;
    }

    case State::Sending:
      // finishCapture() drives this state synchronously; loop() never sees
      // it settle here, it is only ever observed mid-draw.
      break;

    case State::Result:
      if (millis() > g_result_until) {
        showIdle();
        g_state = State::Idle;
      }
      break;
  }

  delay(1);
}
