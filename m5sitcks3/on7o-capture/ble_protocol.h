// Frame constants for the on7o Bluetooth bridge protocol.
//
// Mirrors android/core/src/main/kotlin/org/on7o/bridge/core/protocol/BridgeProtocol.kt.
// See android/PROTOCOL.md for the full frame-format spec. This is a handful
// of constants duplicated by hand across C and Kotlin rather than generated,
// since a shared-codegen setup would be more machinery than the two files
// drifting apart is worth guarding against.
#pragma once

#define ON7O_PROTO_MAGIC0 'O'
#define ON7O_PROTO_MAGIC1 'N'
#define ON7O_PROTO_MAGIC2 '7'
#define ON7O_PROTO_MAGIC3 'O'
#define ON7O_PROTO_VERSION 0x01

#define ON7O_PROTO_TYPE_CAPTURE_HEADER 0x01
#define ON7O_PROTO_TYPE_AUDIO_CHUNK    0x02
#define ON7O_PROTO_TYPE_CAPTURE_END    0x03
#define ON7O_PROTO_TYPE_CAPTURE_ACK    0x04
