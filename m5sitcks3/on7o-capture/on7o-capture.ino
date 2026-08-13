// on7o — thought capture for the M5Stack StickS3.
//
// Hold the button, speak, release. The audio streams to the on7o server while
// you are still talking; nothing is interpreted, answered, or acted upon here.
// The device is an input, and that is all it is.
//
// Requires M5Unified >= 0.2.12 (first version with StickS3 support).

#include <M5Unified.h>
#include <WiFi.h>

#include "audio_input.h"
#include "config.h"
#include "uploader.h"

namespace {

enum class State { Idle, Capturing, Result };

State g_state = State::Idle;
uint32_t g_capture_start = 0;
uint32_t g_result_until = 0;
uint32_t g_last_elapsed = 0;

constexpr uint32_t kResultDisplayMs = 2500;

void showIdle() {
  const bool online = WiFi.status() == WL_CONNECTED;
  M5.Display.fillScreen(TFT_BLACK);
  M5.Display.setTextColor(online ? TFT_WHITE : TFT_ORANGE);
  M5.Display.drawString("on7o", M5.Display.width() / 2, M5.Display.height() / 2 - 12);
  M5.Display.setTextColor(TFT_DARKGREY);
  M5.Display.drawString(online ? "hold to speak" : "no wi-fi",
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

void showResult(bool ok, const String& detail) {
  M5.Display.fillScreen(TFT_BLACK);
  M5.Display.setTextColor(ok ? TFT_GREEN : TFT_RED);
  M5.Display.drawString(ok ? "sent" : "failed",
                        M5.Display.width() / 2, M5.Display.height() / 2 - 12);
  M5.Display.setTextColor(TFT_DARKGREY);
  M5.Display.drawString(detail, M5.Display.width() / 2, M5.Display.height() / 2 + 14);
}

void connectWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    return;
  }
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);  // sleep adds latency to every chunk we push
  WiFi.begin(ON7O_WIFI_SSID, ON7O_WIFI_PASSWORD);

  const uint32_t deadline = millis() + ON7O_WIFI_TIMEOUT_MS;
  while (WiFi.status() != WL_CONNECTED && millis() < deadline) {
    delay(200);
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("wi-fi connected, ip=%s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("wi-fi connection failed");
  }
}

void startCapture() {
  if (!uploader::begin()) {
    Serial.println("could not open upload connection");
    showResult(false, "no server");
    g_state = State::Result;
    g_result_until = millis() + kResultDisplayMs;
    return;
  }
  if (!audio::ready()) {
    Serial.println("microphone unavailable");
    uploader::abort();
    showResult(false, "no mic");
    g_state = State::Result;
    g_result_until = millis() + kResultDisplayMs;
    return;
  }

  audio::startCapture();
  g_capture_start = millis();
  g_last_elapsed = 0;
  g_state = State::Capturing;
  showCapturing(0);
}

/** Moves whatever the microphone has produced onto the wire. */
bool pumpCapture() {
  const int16_t* block = audio::nextBlock();
  if (block != nullptr && !uploader::write(block, ON7O_BLOCK_SAMPLES)) {
    Serial.println("upload failed mid-capture");
    return false;
  }

  const uint32_t elapsed_s = (millis() - g_capture_start) / 1000;
  if (elapsed_s != g_last_elapsed) {
    g_last_elapsed = elapsed_s;
    showCapturing(elapsed_s);
  }
  return true;
}

void finishCapture(bool connection_ok) {
  const uint32_t duration_ms = millis() - g_capture_start;

  if (connection_ok && duration_ms < ON7O_MIN_CAPTURE_MS) {
    uploader::abort();
    Serial.printf("discarded %u ms capture\n", (unsigned)duration_ms);
    showResult(false, "too short");
    g_state = State::Result;
    g_result_until = millis() + kResultDisplayMs;
    return;
  }

  // Blocks still queued in the mic hold the tail of the sentence.
  if (connection_ok) {
    const int16_t* block;
    while ((block = audio::drainBlock()) != nullptr) {
      if (!uploader::write(block, ON7O_BLOCK_SAMPLES)) {
        connection_ok = false;
        break;
      }
    }
  }
  const size_t bytes = uploader::bytesSent();
  const uint32_t seconds = duration_ms / 1000;

  if (!connection_ok) {
    uploader::abort();
    showResult(false, "lost link");
  } else {
    const int status = uploader::finish();
    Serial.printf("upload finished: status=%d bytes=%u\n", status, (unsigned)bytes);
    if (status == 201 || status == 200) {
      showResult(true, String(seconds) + "s captured");
    } else {
      showResult(false, "http " + String(status));
    }
  }

  g_state = State::Result;
  g_result_until = millis() + kResultDisplayMs;
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

  connectWifi();
  showIdle();
}

void loop() {
  M5.update();

  switch (g_state) {
    case State::Idle:
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

    case State::Result:
      if (millis() > g_result_until) {
        // A dropped Wi-Fi link is the most likely reason a capture failed.
        connectWifi();
        showIdle();
        g_state = State::Idle;
      }
      break;
  }

  delay(1);
}
