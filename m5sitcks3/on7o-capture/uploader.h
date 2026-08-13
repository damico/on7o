// Streams a capture to the on7o server while the user is still talking.
#pragma once

#include <stdint.h>
#include <stddef.h>

namespace uploader {

/**
 * Opens the connection and writes the request headers.
 *
 * The body uses chunked transfer encoding, so the device does not need to know
 * how long the thought will be before it starts sending — which is the whole
 * point of push-to-talk.
 */
bool begin();

/** Sends one block of PCM samples as a single HTTP chunk. */
bool write(const int16_t* samples, size_t count);

/**
 * Terminates the body and reads the response.
 *
 * Returns the HTTP status code (201 on success), or a negative value when the
 * connection failed before a status line arrived.
 */
int finish();

/** Drops the connection without completing the request. */
void abort();

/** Total PCM bytes handed to write() during the current capture. */
size_t bytesSent();

}  // namespace uploader
