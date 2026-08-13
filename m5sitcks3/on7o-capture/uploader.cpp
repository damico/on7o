#include "uploader.h"

#include <Arduino.h>
#include <WiFi.h>

#include "config.h"

namespace uploader {
namespace {

constexpr uint32_t kConnectTimeoutMs = 5000;
constexpr uint32_t kResponseTimeoutMs = 8000;

WiFiClient g_client;
bool g_active = false;
size_t g_bytes = 0;

}  // namespace

bool begin() {
  abort();

  if (WiFi.status() != WL_CONNECTED) {
    return false;
  }
  g_client.setTimeout(kConnectTimeoutMs / 1000);
  if (!g_client.connect(ON7O_HOST, ON7O_PORT, kConnectTimeoutMs)) {
    return false;
  }
  // Nagle would sit on our 1 kB chunks waiting for more data to coalesce.
  g_client.setNoDelay(true);

  char request[512];
  int len = snprintf(
      request, sizeof(request),
      "POST %s?device=%s&sampleRate=%d&channels=%d&bits=%d HTTP/1.1\r\n"
      "Host: %s:%d\r\n"
      "Content-Type: application/octet-stream\r\n"
      "Transfer-Encoding: chunked\r\n"
      "Connection: close\r\n"
      "\r\n",
      ON7O_PATH, ON7O_DEVICE_ID, ON7O_SAMPLE_RATE, ON7O_CHANNELS, ON7O_BITS,
      ON7O_HOST, ON7O_PORT);

  if (len <= 0 || len >= (int)sizeof(request)) {
    abort();
    return false;
  }
  if (g_client.write((const uint8_t*)request, len) != (size_t)len) {
    abort();
    return false;
  }

  g_active = true;
  g_bytes = 0;
  return true;
}

bool write(const int16_t* samples, size_t count) {
  if (!g_active || samples == nullptr || count == 0) {
    return g_active;
  }

  const size_t bytes = count * sizeof(int16_t);
  char header[16];
  int header_len = snprintf(header, sizeof(header), "%X\r\n", (unsigned)bytes);

  if (g_client.write((const uint8_t*)header, header_len) != (size_t)header_len ||
      g_client.write((const uint8_t*)samples, bytes) != bytes ||
      g_client.write((const uint8_t*)"\r\n", 2) != 2) {
    abort();
    return false;
  }

  g_bytes += bytes;
  return true;
}

int finish() {
  if (!g_active) {
    return -1;
  }

  // Terminating zero-length chunk.
  if (g_client.write((const uint8_t*)"0\r\n\r\n", 5) != 5) {
    abort();
    return -2;
  }

  const uint32_t deadline = millis() + kResponseTimeoutMs;
  while (!g_client.available() && g_client.connected() && millis() < deadline) {
    delay(5);
  }

  int status = -3;
  if (g_client.available()) {
    // "HTTP/1.1 201 Created"
    String line = g_client.readStringUntil('\n');
    int first_space = line.indexOf(' ');
    if (first_space > 0) {
      status = line.substring(first_space + 1, first_space + 4).toInt();
    }
  }

  abort();
  return status;
}

void abort() {
  if (g_client.connected()) {
    g_client.stop();
  }
  g_active = false;
}

size_t bytesSent() {
  return g_bytes;
}

}  // namespace uploader
