package org.on7o.bridge.core.sync

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UploadClientTest {

    private val server = MockWebServer()
    private lateinit var client: UploadClient
    private lateinit var audioFile: File

    @Before
    fun setUp() {
        server.start()
        client = UploadClient(OkHttpClient())
        audioFile = Files.createTempFile("capture", ".wav").toFile()
        audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    @After
    fun tearDown() {
        server.close()
        audioFile.delete()
    }

    @Test
    fun `uploads the wav file with the contract the server's IngestController expects`() {
        server.enqueue(MockResponse.Builder().code(201).addHeader("Location", "/api/thoughts/abc123").build())

        val result = client.upload(
            baseUrl = server.url("/").toString(),
            audioFile = audioFile,
            deviceId = "sticks3-01",
            sampleRate = 16000,
            channels = 1,
            bitsPerSample = 16,
            capturedAt = "2026-09-02T14:03:00Z",
        )

        assertEquals(UploadResult.Success("abc123"), result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val target = request.target
        assertTrue(target.startsWith("/api/thoughts/audio?"))
        assertTrue(target.contains("device=sticks3-01"))
        assertTrue(target.contains("sampleRate=16000"))
        assertTrue(target.contains("channels=1"))
        assertTrue(target.contains("bits=16"))
        assertTrue(target.contains("format=wav"))
        assertEquals(4L, request.bodySize)
    }

    @Test
    fun `a 5xx response is reported as retryable, a 4xx is not`() {
        server.enqueue(MockResponse.Builder().code(503).build())
        val serverError = client.upload(server.url("/").toString(), audioFile, "sticks3-01", 16000, 1, 16, "2026-09-02T14:03:00Z")
        assertTrue(serverError is UploadResult.Failure)
        assertTrue((serverError as UploadResult.Failure).retryable)

        server.enqueue(MockResponse.Builder().code(400).build())
        val clientError = client.upload(server.url("/").toString(), audioFile, "sticks3-01", 16000, 1, 16, "2026-09-02T14:03:00Z")
        assertTrue(clientError is UploadResult.Failure)
        assertTrue(!(clientError as UploadResult.Failure).retryable)
    }
}
