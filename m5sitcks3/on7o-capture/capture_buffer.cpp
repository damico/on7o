#include "capture_buffer.h"

#include <Arduino.h>
#include <esp_heap_caps.h>
#include <string.h>

#include "config.h"

namespace capture_buffer {
namespace {

constexpr size_t kCapacitySamples =
    (size_t)ON7O_MAX_CAPTURE_MS * ON7O_SAMPLE_RATE / 1000;

int16_t* g_buffer = nullptr;
size_t g_count = 0;

}  // namespace

bool begin() {
  g_buffer = static_cast<int16_t*>(
      heap_caps_malloc(kCapacitySamples * sizeof(int16_t), MALLOC_CAP_SPIRAM));
  if (g_buffer == nullptr) {
    Serial.println("capture buffer: PSRAM allocation failed");
    return false;
  }
  Serial.printf("capture buffer: %u samples (%u bytes) in PSRAM\n",
                (unsigned)kCapacitySamples, (unsigned)(kCapacitySamples * sizeof(int16_t)));
  return true;
}

void reset() {
  g_count = 0;
}

bool append(const int16_t* samples, size_t count) {
  if (g_buffer == nullptr || g_count + count > kCapacitySamples) {
    return false;
  }
  memcpy(g_buffer + g_count, samples, count * sizeof(int16_t));
  g_count += count;
  return true;
}

const int16_t* data() {
  return g_buffer;
}

size_t sampleCount() {
  return g_count;
}

}  // namespace capture_buffer
