package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the pre-flight behavior of [GeminiClient.transcribeAudio] — argument
 * validation, the size guard, and the demo-mode (missing key) path. The
 * network success path requires a real API key and is therefore not exercised
 * here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiTranscriptionTest {

  private val apiKeyIsPlaceholder: Boolean
    get() = BuildConfig.GEMINI_API_KEY.isBlank() ||
      BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY"

  @Test
  fun `empty audio is rejected`() = runBlocking {
    val result = GeminiClient.transcribeAudio("audio/mp4", ByteArray(0))

    assertTrue(result is TranscriptionResult.Error)
    assertEquals(
      "The audio file is empty — nothing to transcribe.",
      (result as TranscriptionResult.Error).message
    )
  }

  @Test
  fun `oversize audio is rejected before touching the network`() = runBlocking {
    val result = GeminiClient.transcribeAudio("audio/mp4", ByteArray(1024), maxBytes = 64)

    assertTrue(result is TranscriptionResult.Error)
    assertTrue((result as TranscriptionResult.Error).message.contains("too large"))
  }

  @Test
  fun `default size limit stays under the API request cap`() {
    assertTrue(GeminiClient.MAX_INLINE_AUDIO_BYTES < 20 * 1024 * 1024)
  }

  @Test
  fun `missing API key returns an actionable error instead of crashing`() = runBlocking {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)

    val result = GeminiClient.transcribeAudio("audio/mp4", ByteArray(64) { 1 })

    assertTrue(result is TranscriptionResult.Error)
    val message = (result as TranscriptionResult.Error).message
    assertTrue(message.contains("API key"))
    assertTrue(message.contains(".env"))
  }
}
