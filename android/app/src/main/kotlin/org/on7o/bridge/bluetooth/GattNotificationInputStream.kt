package org.on7o.bridge.bluetooth

import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Bridges BLE characteristic-changed callbacks into a blocking InputStream,
 * so core.protocol.FrameReader (transport-agnostic) can read a BLE link the
 * same way it would read an RFCOMM socket or any other byte stream.
 */
class GattNotificationInputStream : InputStream() {

    private val closeSentinel = ByteArray(0)
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var current: ByteArray? = null
    private var offset = 0

    /** Called from the GATT callback thread as indications arrive. */
    fun offer(bytes: ByteArray) {
        queue.put(bytes)
    }

    override fun close() {
        queue.put(closeSentinel)
    }

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            val chunk = current
            if (chunk != null && offset < chunk.size) {
                val n = minOf(len, chunk.size - offset)
                System.arraycopy(chunk, offset, b, off, n)
                offset += n
                if (offset >= chunk.size) current = null
                return n
            }
            val next = queue.take()
            if (next === closeSentinel) return -1
            current = next
            offset = 0
        }
    }
}
