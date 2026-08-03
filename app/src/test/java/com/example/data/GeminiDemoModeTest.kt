package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tests for the offline "demo mode" fallbacks inside [GeminiClient]. These
 * cover the pure functions directly (`createDemoResult`, `createDemoQuiz`)
 * plus the demo path of `defineKeyword`, which runs whenever the API key is
 * not configured — exactly the state of a fresh checkout.
 */
class GeminiDemoModeTest {

  private val apiKeyIsPlaceholder: Boolean
    get() = BuildConfig.GEMINI_API_KEY.isBlank() ||
      BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY"

  // --- createDemoResult ---

  @Test
  fun `demo result uses the provided lecture title`() {
    val result = GeminiClient.createDemoResult("Biology", "Cell Division", "transcript text")

    assertEquals("Cell Division", result.title)
  }

  @Test
  fun `demo result falls back to a subject-based title when blank`() {
    val result = GeminiClient.createDemoResult("Biology", "   ", "transcript text")

    assertEquals("Introduction to Biology", result.title)
  }

  @Test
  fun `demo result is a complete study guide`() {
    val result = GeminiClient.createDemoResult("Chemistry", "Bonds", "transcript")

    assertTrue(result.summary.isNotBlank())
    assertTrue(result.summaryShort!!.isNotBlank())
    assertTrue(result.summaryMedium!!.isNotBlank())
    assertTrue(result.summaryDetailed!!.isNotBlank())
    assertEquals(6, result.keywords!!.size)
    assertEquals(3, result.takeaways.size)
    assertEquals(5, result.flashcards.size)
    result.flashcards.forEach { card ->
      assertTrue("flashcard front must not be blank", card.front.isNotBlank())
      assertTrue("flashcard back must not be blank", card.back.isNotBlank())
    }
    result.takeaways.forEach { takeaway ->
      assertTrue(takeaway.isNotBlank())
    }
  }

  // --- createDemoQuiz: branch selection by lecture title ---

  @Test
  fun `demo quiz picks the neural network branch`() {
    val questions = GeminiClient.createDemoQuiz("CS", "Introduction to Neural Networks", "")

    assertEquals(3, questions.size)
  }

  @Test
  fun `demo quiz picks the economics branch`() {
    val questions = GeminiClient.createDemoQuiz("Econ", "Law of Supply and Demand", "")

    assertEquals(2, questions.size)
  }

  @Test
  fun `demo quiz picks the history branch`() {
    val questions = GeminiClient.createDemoQuiz("History", "Fall of the Roman Empire", "")

    assertEquals(2, questions.size)
  }

  @Test
  fun `demo quiz falls back to generic study questions for other titles`() {
    val questions = GeminiClient.createDemoQuiz("Physics", "Quantum Chromodynamics", "")

    assertEquals(2, questions.size)
  }

  @Test
  fun `every demo quiz question is internally consistent`() {
    val titles = listOf(
      "Neural Networks",
      "Supply, Demand and Price",
      "Roman Empire",
      "Unrelated Title"
    )

    titles.forEach { title ->
      val questions = GeminiClient.createDemoQuiz("Any", title, "")
      assertTrue(questions.isNotEmpty())
      questions.forEach { q ->
        assertTrue(q.question.isNotBlank())
        assertTrue(q.explanation.isNotBlank())
        assertTrue("needs at least 2 options", q.options.size >= 2)
        assertEquals(
          "options must be distinct",
          q.options.size,
          q.options.distinct().size
        )
        assertTrue(
          "correctOptionIndex ${q.correctOptionIndex} must be inside ${q.options.indices}",
          q.correctOptionIndex in q.options.indices
        )
      }
    }
  }

  // --- defineKeyword: demo path (only when no real API key is configured) ---

  @Test
  fun `known keywords return curated definitions in demo mode`() = runBlocking {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)

    val definition = GeminiClient.defineKeyword("Active Recall", "any transcript")

    assertTrue(definition.contains("retriev", ignoreCase = true))
    assertFalse(definition.startsWith("Error"))
  }

  @Test
  fun `keyword lookup is case and whitespace tolerant`() = runBlocking {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)

    val definition = GeminiClient.defineKeyword("  SPACED REPETITION  ", "")

    assertTrue(definition.contains("intervals", ignoreCase = true))
  }

  @Test
  fun `unknown keyword gets a demo fallback that names the keyword`() = runBlocking {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)

    val definition = GeminiClient.defineKeyword("Mitochondria", "")

    assertTrue(definition.contains("Mitochondria"))
  }
}
