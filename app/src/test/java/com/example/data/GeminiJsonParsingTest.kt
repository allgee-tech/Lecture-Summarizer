package com.example.data

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests the JSON (de)serialization contract that [GeminiClient] relies on when
 * talking to the Gemini REST API. The Moshi instance is configured exactly like
 * the one inside the client so these tests exercise the real parsing behavior
 * without making any network calls.
 */
class GeminiJsonParsingTest {

  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  // --- GenerateContentResponse envelope (what the API returns) ---

  @Test
  fun `response envelope text is extracted the same way the client does it`() {
    val envelope = """
      {
        "candidates": [
          {
            "content": {
              "parts": [ { "text": "{\"title\":\"T\"}" } ]
            }
          }
        ]
      }
    """.trimIndent()

    val response = moshi.adapter(GenerateContentResponse::class.java).fromJson(envelope)

    val text = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
    assertEquals("{\"title\":\"T\"}", text)
  }

  @Test
  fun `response envelope with no candidates yields null text`() {
    val response = moshi.adapter(GenerateContentResponse::class.java)
      .fromJson("""{ "candidates": [] }""")

    assertNotNull(response)
    val text = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
    assertNull(text)
  }

  @Test
  fun `response envelope tolerates unknown fields from the API`() {
    val envelope = """
      {
        "candidates": [
          {
            "content": { "parts": [ { "text": "hello" } ], "role": "model" },
            "finishReason": "STOP",
            "avgLogprobs": -0.42
          }
        ],
        "usageMetadata": { "promptTokenCount": 10, "totalTokenCount": 42 },
        "modelVersion": "gemini-3.5-flash"
      }
    """.trimIndent()

    val response = moshi.adapter(GenerateContentResponse::class.java).fromJson(envelope)

    assertEquals(
      "hello",
      response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
    )
  }

  // --- LectureSummaryResult (the parsed study-guide payload) ---

  @Test
  fun `full lecture summary payload parses into all fields`() {
    val payload = """
      {
        "title": "Intro to Neural Networks",
        "summary": "**Neurons** are cool.",
        "takeaways": ["T1", "T2"],
        "flashcards": [
          { "front": "What is a neuron?", "back": "A weighted-sum unit." }
        ],
        "keywords": ["ReLU", "Backprop"],
        "summaryShort": "Short.",
        "summaryMedium": "Medium paragraph.",
        "summaryDetailed": "Detailed markdown."
      }
    """.trimIndent()

    val result = moshi.adapter(LectureSummaryResult::class.java).fromJson(payload)!!

    assertEquals("Intro to Neural Networks", result.title)
    assertEquals("**Neurons** are cool.", result.summary)
    assertEquals(listOf("T1", "T2"), result.takeaways)
    assertEquals(1, result.flashcards.size)
    assertEquals("What is a neuron?", result.flashcards[0].front)
    assertEquals("A weighted-sum unit.", result.flashcards[0].back)
    assertEquals(listOf("ReLU", "Backprop"), result.keywords)
    assertEquals("Short.", result.summaryShort)
    assertEquals("Medium paragraph.", result.summaryMedium)
    assertEquals("Detailed markdown.", result.summaryDetailed)
  }

  @Test
  fun `summary payload without optional fields leaves them null`() {
    val payload = """
      {
        "title": "Minimal",
        "summary": "S",
        "takeaways": [],
        "flashcards": []
      }
    """.trimIndent()

    val result = moshi.adapter(LectureSummaryResult::class.java).fromJson(payload)!!

    assertNull(result.keywords)
    assertNull(result.summaryShort)
    assertNull(result.summaryMedium)
    assertNull(result.summaryDetailed)
    assertTrue(result.takeaways.isEmpty())
    assertTrue(result.flashcards.isEmpty())
  }

  @Test
  fun `summary payload missing a required field throws JsonDataException`() {
    // The client wraps parsing in try/catch and returns null on failure; this
    // test documents WHY that catch exists — Moshi rejects incomplete payloads.
    val payloadMissingTitle = """
      {
        "summary": "S",
        "takeaways": [],
        "flashcards": []
      }
    """.trimIndent()

    try {
      moshi.adapter(LectureSummaryResult::class.java).fromJson(payloadMissingTitle)
      fail("Expected a JsonDataException for a payload missing 'title'")
    } catch (expected: JsonDataException) {
      // expected — Moshi enforces non-null required fields
    }
  }

  @Test
  fun `completely malformed payload throws instead of returning garbage`() {
    try {
      moshi.adapter(LectureSummaryResult::class.java).fromJson("this is not json at all")
      fail("Expected parsing to fail for malformed input")
    } catch (expected: JsonDataException) {
      // JsonEncodingException (a JsonDataException subclass) is expected here
    }
  }

  // --- QuizResponse payload ---

  @Test
  fun `quiz payload parses questions with correct indices`() {
    val payload = """
      {
        "questions": [
          {
            "question": "Q1?",
            "options": ["A", "B", "C", "D"],
            "correctOptionIndex": 2,
            "explanation": "Because C."
          },
          {
            "question": "Q2?",
            "options": ["Only", "Two"],
            "correctOptionIndex": 0,
            "explanation": "First is right."
          }
        ]
      }
    """.trimIndent()

    val response = moshi.adapter(QuizResponse::class.java).fromJson(payload)

    assertNotNull(response)
    assertEquals(2, response!!.questions.size)
    response.questions.forEach { question ->
      assertTrue(
        "correctOptionIndex must point inside options",
        question.correctOptionIndex in question.options.indices
      )
    }
    assertEquals("Q1?", response.questions[0].question)
    assertEquals(listOf("A", "B", "C", "D"), response.questions[0].options)
    assertEquals(2, response.questions[0].correctOptionIndex)
    assertEquals("Because C.", response.questions[0].explanation)
  }

  // --- Request serialization (what we send to the API) ---

  @Test
  fun `request serializes with the JSON key names the API expects`() {
    val request = GenerateContentRequest(
      contents = listOf(Content(parts = listOf(Part(text = "sum this")))),
      generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.3),
      systemInstruction = Content(parts = listOf(Part(text = "output JSON only")))
    )

    val json = moshi.adapter(GenerateContentRequest::class.java).toJson(request)

    assertTrue(json.contains("\"contents\""))
    assertTrue(json.contains("\"generationConfig\""))
    assertTrue(json.contains("\"responseMimeType\":\"application/json\""))
    assertTrue(json.contains("\"temperature\":0.3"))
    assertTrue(json.contains("\"systemInstruction\""))
    assertTrue(json.contains("\"parts\""))
    assertTrue(json.contains("\"text\":\"sum this\""))
  }

  @Test
  fun `request omits optional blocks when they are not set`() {
    val request = GenerateContentRequest(
      contents = listOf(Content(parts = listOf(Part(text = "define this"))))
    )

    val json = moshi.adapter(GenerateContentRequest::class.java).toJson(request)

    assertTrue(json.contains("\"contents\""))
    // Moshi omits null values by default
    assertTrue(!json.contains("generationConfig"))
    assertTrue(!json.contains("systemInstruction"))
  }

  @Test
  fun `audio part serializes as inline_data with snake case keys`() {
    // The Gemini API expects inline audio as {"inline_data": {"mime_type", "data"}}.
    val request = GenerateContentRequest(
      contents = listOf(
        Content(
          parts = listOf(
            Part(text = "Transcribe this lecture"),
            Part(inlineData = InlineData(mimeType = "audio/mp4", data = "QUJD"))
          )
        )
      )
    )

    val json = moshi.adapter(GenerateContentRequest::class.java).toJson(request)

    assertTrue(json.contains("\"text\":\"Transcribe this lecture\""))
    assertTrue(json.contains("\"inline_data\":{\"mime_type\":\"audio/mp4\",\"data\":\"QUJD\"}"))
  }
}
