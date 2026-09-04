#include "ble_transport.h"

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include <string.h>

#include "ble_protocol.h"
#include "config.h"

namespace ble {
namespace {

// Private, project-specific UUIDs: this is not an adopted GATT service, just
// one stick talking to one phone.
constexpr char kServiceUuid[] = "8b6c9f10-4b3e-4d2a-9f0a-1f6c7a2e0a01";
constexpr char kCaptureCharUuid[] = "8b6c9f11-4b3e-4d2a-9f0a-1f6c7a2e0a01";

constexpr uint32_t kConfirmTimeoutMs = 5000;
constexpr size_t kMaxDeviceIdBytes = 32;
constexpr size_t kFrameHeaderBytes = 6;  // magic(4) + version(1) + type(1)

// A notify() fails locally (returns false) when NimBLE's own outgoing queue
// for this connection is momentarily full; that is backpressure, not a lost
// packet, so it is worth a few short retries rather than giving up the whole
// transfer over one busy queue.
constexpr int kNotifyRetries = 30;
constexpr uint32_t kNotifyRetryDelayMs = 5;

// How many AUDIO_CHUNK notifications sendFast() will let sit unacknowledged
// (queued at the host but not yet reported done by onStatus) at once. Without
// this cap, notify() happily accepts far more packets than the controller can
// actually get on air, and most of them later fail with BLE_HS_ENOMEM when
// the controller runs out of ACL buffers to hand them off with; the failure
// is silent to sendFast() (notify()'s own return value only reflects local
// queuing, not delivery), so a capture can report success while most of its
// audio never left the radio. Gating on real completions, not just queue
// space, makes the send rate track what the link can actually sustain.
constexpr int kMaxInFlightNotifications = 3;
constexpr uint32_t kCreditTimeoutMs = 5000;

constexpr uint16_t kAttOverheadBytes = 3;    // opcode(1) + attribute handle(2)
constexpr uint16_t kMinAttMtu = 23;          // BLE spec floor, used if the peer MTU looks unset
constexpr uint16_t kMaxSendChunk = 512;      // sanity ceiling, far above any real negotiated MTU

NimBLECharacteristic* g_captureChar = nullptr;
volatile bool g_connected = false;
volatile uint16_t g_connHandle = 0;
SemaphoreHandle_t g_confirm = nullptr;
volatile bool g_confirmOk = false;
// Counting semaphore: one credit per AUDIO_CHUNK notification allowed
// in flight. Taken before each notify(), given back by onStatus() once
// that notification's transmission actually finishes (success or not).
SemaphoreHandle_t g_txCredits = nullptr;

// Reused across sends rather than stack-allocated: the chunk buffer in
// particular is a few kB, more than is comfortable to put on the loop task's
// stack.
uint8_t g_headerFrame[kFrameHeaderBytes + 1 + kMaxDeviceIdBytes + 4 + 1 + 1 + 1];
uint8_t g_chunkFrame[kFrameHeaderBytes + 4 + ON7O_BLE_CHUNK_BYTES];
uint8_t g_endFrame[kFrameHeaderBytes + 4];

void putFrameHeader(uint8_t* buf, uint8_t type) {
  buf[0] = ON7O_PROTO_MAGIC0;
  buf[1] = ON7O_PROTO_MAGIC1;
  buf[2] = ON7O_PROTO_MAGIC2;
  buf[3] = ON7O_PROTO_MAGIC3;
  buf[4] = ON7O_PROTO_VERSION;
  buf[5] = type;
}

void putU32LE(uint8_t* buf, uint32_t value) {
  buf[0] = (uint8_t)(value & 0xFF);
  buf[1] = (uint8_t)((value >> 8) & 0xFF);
  buf[2] = (uint8_t)((value >> 16) & 0xFF);
  buf[3] = (uint8_t)((value >> 24) & 0xFF);
}

/**
 * Bytes usable per indication. A GATT indication is exactly one ATT packet:
 * there is no fragmentation/reassembly across multiple indications the way
 * there is for, say, an HTTP chunk, so anything larger than (ATT MTU - 3)
 * has to be split into several indications, each individually confirmed.
 */
uint16_t sendChunkBytes() {
  uint16_t mtu = kMinAttMtu;
  NimBLEServer* server = NimBLEDevice::getServer();
  if (server != nullptr && g_connHandle != 0) {
    uint16_t peerMtu = server->getPeerMTU(g_connHandle);
    if (peerMtu > 0) {
      mtu = peerMtu;
    }
  }
  uint16_t payload = mtu > kAttOverheadBytes ? mtu - kAttOverheadBytes : kMinAttMtu - kAttOverheadBytes;
  return payload < kMaxSendChunk ? payload : kMaxSendChunk;
}

/**
 * Sends one indication, blocking until the phone confirms it. The initial
 * queue-full rejection gets the same short local retry notifyOnce() already
 * has: with the screen locked, Android renegotiates a much slower BLE
 * connection interval (there is no API to prevent this), so the outgoing
 * queue drains slower and a CAPTURE_HEADER/CAPTURE_END sent right after a
 * long run of AUDIO_CHUNK notifications can otherwise find it still full
 * from a single, unretried check.
 */
bool indicateOnceAndWait(const uint8_t* data, size_t length) {
  if (!g_connected || g_captureChar == nullptr) {
    Serial.printf("ble: indicate skipped, connected=%d char=%p\n", (int)g_connected, (void*)g_captureChar);
    return false;
  }
  xSemaphoreTake(g_confirm, 0);  // drop any stale signal from a previous send
  bool queued = false;
  for (int attempt = 0; attempt < kNotifyRetries; ++attempt) {
    if (g_captureChar->indicate(data, length)) {
      queued = true;
      break;
    }
    delay(kNotifyRetryDelayMs);
  }
  if (!queued) {
    Serial.printf("ble: indicate(len=%u) still rejected locally after %d retries (queue full / not subscribed?)\n",
                  (unsigned)length, kNotifyRetries);
    return false;
  }
  const bool signaled = xSemaphoreTake(g_confirm, pdMS_TO_TICKS(kConfirmTimeoutMs)) == pdTRUE;
  if (!signaled) {
    Serial.printf("ble: indicate(len=%u) queued but confirm timed out after %ums\n",
                  (unsigned)length, (unsigned)kConfirmTimeoutMs);
  } else if (!g_confirmOk) {
    Serial.printf("ble: indicate(len=%u) confirmed with error status\n", (unsigned)length);
  }
  return signaled && g_confirmOk;
}

/**
 * Sends an arbitrarily long buffer as however many MTU-sized indications it
 * takes, each confirmed before the next goes out. Frame boundaries (where
 * one CAPTURE_HEADER/AUDIO_CHUNK/CAPTURE_END ends and the next begins) are
 * purely a property of the byte stream FrameReader parses on the other end;
 * they do not need to, and generally will not, line up with indication
 * boundaries.
 */
bool sendReliable(const uint8_t* data, size_t length) {
  size_t sent = 0;
  while (sent < length) {
    const size_t chunk = sendChunkBytes();
    const size_t n = (length - sent) < chunk ? (length - sent) : chunk;
    if (!indicateOnceAndWait(data + sent, n)) {
      return false;
    }
    sent += n;
  }
  return true;
}

/**
 * Sends one packet as a GATT notification: no per-packet round trip to the
 * phone, but gated by g_txCredits so at most kMaxInFlightNotifications are
 * ever outstanding at once, keeping the send rate at what the link actually
 * drains instead of what NimBLE's host queue will merely accept. Retries
 * briefly on local backpressure (queue still full despite the credit) but
 * never waits for the phone.
 */
bool notifyOnce(const uint8_t* data, size_t length) {
  if (!g_connected || g_captureChar == nullptr) {
    return false;
  }
  if (xSemaphoreTake(g_txCredits, pdMS_TO_TICKS(kCreditTimeoutMs)) != pdTRUE) {
    Serial.printf("ble: notify(len=%u) no tx credit after %ums, link stalled\n",
                  (unsigned)length, (unsigned)kCreditTimeoutMs);
    return false;
  }
  for (int attempt = 0; attempt < kNotifyRetries; ++attempt) {
    if (g_captureChar->notify(data, length)) {
      return true;
    }
    delay(kNotifyRetryDelayMs);
  }
  Serial.printf("ble: notify(len=%u) still failing after %d retries\n", (unsigned)length, kNotifyRetries);
  return false;
}

/**
 * Sends an arbitrarily long buffer as however many MTU-sized notifications
 * it takes. Used for AUDIO_CHUNK payloads, where a stop-and-wait indication
 * per ~250-byte packet made a multi-second recording take over a minute to
 * transfer; a dropped notification is not retransmitted, but CAPTURE_END's
 * totalPcmBytes lets the phone detect an incomplete transfer and discard it,
 * the same way a dropped connection mid-capture already had to be handled.
 */
bool sendFast(const uint8_t* data, size_t length) {
  size_t sent = 0;
  while (sent < length) {
    const size_t chunk = sendChunkBytes();
    const size_t n = (length - sent) < chunk ? (length - sent) : chunk;
    if (!notifyOnce(data + sent, n)) {
      return false;
    }
    sent += n;
  }
  return true;
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* server, NimBLEConnInfo& info) override {
    g_connected = true;
    g_connHandle = info.getConnHandle();
    Serial.println("ble: phone connected");
  }

  void onDisconnect(NimBLEServer* server, NimBLEConnInfo& info, int reason) override {
    g_connected = false;
    g_connHandle = 0;
    Serial.println("ble: phone disconnected, resuming advertising");
    NimBLEDevice::startAdvertising();
  }

  void onMTUChange(uint16_t mtu, NimBLEConnInfo& info) override {
    Serial.printf("ble: MTU negotiated: %u\n", (unsigned)mtu);
  }
};

class CaptureCharCallbacks : public NimBLECharacteristicCallbacks {
  void onStatus(NimBLECharacteristic* characteristic, NimBLEConnInfo& info, int code) override {
    // For an INDICATE characteristic, NimBLE's own gatt server (ble_gatts_indicate_rx_rsp)
    // reports a confirmed indication as BLE_HS_EDONE, not 0: status 0 only means "sent,
    // not yet acknowledged" and the library filters that one out before this callback
    // ever fires (see NimBLEServer.cpp's BLE_GAP_EVENT_NOTIFY_TX handler), so every call
    // that does reach here is a terminal outcome, and EDONE is the successful one.
    g_confirmOk = (code == 0 || code == BLE_HS_EDONE);
    if (!g_confirmOk) {
      Serial.printf("ble: onStatus code=%d (%s)\n", code, NimBLEUtils::returnCodeToString(code));
    }
    xSemaphoreGive(g_confirm);
    // Every terminal status, indicate or notify, means one previously
    // submitted characteristic write finished and its resources were freed;
    // sendFast()'s notifyOnce() is the only consumer of these credits.
    xSemaphoreGive(g_txCredits);
  }

  void onSubscribe(NimBLECharacteristic* characteristic, NimBLEConnInfo& info, uint16_t subValue) override {
    // 0 = unsubscribed, 1 = notify, 2 = indicate, 3 = both.
    Serial.printf("ble: onSubscribe subValue=%u\n", (unsigned)subValue);
  }
};

ServerCallbacks g_serverCallbacks;
CaptureCharCallbacks g_captureCallbacks;

}  // namespace

void begin() {
  g_confirm = xSemaphoreCreateBinary();
  g_txCredits = xSemaphoreCreateCounting(kMaxInFlightNotifications, kMaxInFlightNotifications);

  NimBLEDevice::init(ON7O_BLE_DEVICE_NAME);

  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(&g_serverCallbacks);

  // NOTIFY carries the bulk AUDIO_CHUNK payload (fast, unconfirmed); INDICATE
  // carries CAPTURE_HEADER and CAPTURE_END, where knowing the phone actually
  // got the start/end of a capture matters more than transfer speed.
  NimBLEService* service = server->createService(kServiceUuid);
  g_captureChar = service->createCharacteristic(
      kCaptureCharUuid, NIMBLE_PROPERTY::NOTIFY | NIMBLE_PROPERTY::INDICATE);
  g_captureChar->setCallbacks(&g_captureCallbacks);

  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  // The name and the 128-bit service UUID together do not fit in the 31-byte
  // legacy advertising payload (3 flags + 17 name + 18 UUID = 38 bytes), so
  // NimBLE moves the name into scan response data once this is enabled,
  // leaving the primary packet free for the UUID the Android app filters on.
  advertising->enableScanResponse(true);
  advertising->setName(ON7O_BLE_DEVICE_NAME);
  advertising->addServiceUUID(service->getUUID());
  advertising->start();

  Serial.println("ble: advertising started");
}

bool connected() {
  return g_connected;
}

bool sendCapture(const int16_t* pcm, size_t sampleCount, int sampleRate, int channels, int bitsPerSample) {
  if (!g_connected) {
    return false;
  }

  Serial.printf("ble: sendCapture starting, connHandle=%u chunkBytes=%u\n",
                (unsigned)g_connHandle, (unsigned)sendChunkBytes());

  // A previous capture's credits could be stuck below max if it gave up
  // (link stall, timeout) before every outstanding notify's onStatus fired;
  // top up to a fresh, full set rather than deleting the semaphore outright,
  // since a late onStatus from that abandoned send could still be about to
  // give() it and a delete/recreate here would race that into a dangling
  // handle.
  for (UBaseType_t topUp = uxSemaphoreGetCount(g_txCredits); topUp < kMaxInFlightNotifications; ++topUp) {
    xSemaphoreGive(g_txCredits);
  }

  const uint8_t* bytes = reinterpret_cast<const uint8_t*>(pcm);
  const size_t totalBytes = sampleCount * sizeof(int16_t);

  // CAPTURE_HEADER. capturedAtLen is always 0: the stick has no clock, so
  // the phone stamps its own receipt time, per PROTOCOL.md.
  const size_t deviceIdLen = strnlen(ON7O_DEVICE_ID, kMaxDeviceIdBytes);
  size_t pos = kFrameHeaderBytes;
  g_headerFrame[pos++] = (uint8_t)deviceIdLen;
  memcpy(g_headerFrame + pos, ON7O_DEVICE_ID, deviceIdLen);
  pos += deviceIdLen;
  putU32LE(g_headerFrame + pos, (uint32_t)sampleRate);
  pos += 4;
  g_headerFrame[pos++] = (uint8_t)channels;
  g_headerFrame[pos++] = (uint8_t)bitsPerSample;
  g_headerFrame[pos++] = 0;  // capturedAtLen
  putFrameHeader(g_headerFrame, ON7O_PROTO_TYPE_CAPTURE_HEADER);
  if (!sendReliable(g_headerFrame, pos)) {
    Serial.println("ble: CAPTURE_HEADER not confirmed");
    return false;
  }

  size_t sent = 0;
  while (sent < totalBytes) {
    const size_t remaining = totalBytes - sent;
    const size_t n = remaining < ON7O_BLE_CHUNK_BYTES ? remaining : ON7O_BLE_CHUNK_BYTES;
    putFrameHeader(g_chunkFrame, ON7O_PROTO_TYPE_AUDIO_CHUNK);
    putU32LE(g_chunkFrame + kFrameHeaderBytes, (uint32_t)n);
    memcpy(g_chunkFrame + kFrameHeaderBytes + 4, bytes + sent, n);
    if (!sendFast(g_chunkFrame, kFrameHeaderBytes + 4 + n)) {
      Serial.printf("ble: AUDIO_CHUNK at offset %u could not be sent\n", (unsigned)sent);
      return false;
    }
    sent += n;
  }

  putFrameHeader(g_endFrame, ON7O_PROTO_TYPE_CAPTURE_END);
  putU32LE(g_endFrame + kFrameHeaderBytes, (uint32_t)totalBytes);
  if (!sendReliable(g_endFrame, sizeof(g_endFrame))) {
    Serial.println("ble: CAPTURE_END not confirmed");
    return false;
  }

  return true;
}

}  // namespace ble
