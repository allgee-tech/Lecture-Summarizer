package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.PolishPrimary
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the display/parse helpers from LectureScreens.kt:
 * duration & date formatting, subject color parsing with fallback,
 * and `**bold**` markdown parsing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiFormattingTest {

  private lateinit var defaultLocale: Locale
  private lateinit var defaultTimeZone: TimeZone

  @Before
  fun pinLocaleAndTimeZone() {
    defaultLocale = Locale.getDefault()
    defaultTimeZone = TimeZone.getDefault()
    Locale.setDefault(Locale.US)
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  }

  @After
  fun restoreLocaleAndTimeZone() {
    Locale.setDefault(defaultLocale)
    TimeZone.setDefault(defaultTimeZone)
  }

  // --- formatDuration ---

  @Test
  fun `formatDuration renders mm ss`() {
    assertEquals("00:00", formatDuration(0))
    assertEquals("00:05", formatDuration(5))
    assertEquals("01:01", formatDuration(61))
    assertEquals("10:00", formatDuration(600))
    assertEquals("25:00", formatDuration(1500)) // default focus preset
  }

  @Test
  fun `formatDuration keeps counting minutes past one hour`() {
    assertEquals("60:00", formatDuration(3600))
  }

  // --- formatDate ---

  @Test
  fun `formatDate renders a US style date`() {
    assertEquals("Jan 1, 1970", formatDate(0L))
    assertEquals("Nov 14, 2023", formatDate(1_700_000_000_000L))
  }

  // --- parseColor ---

  @Test
  fun `parseColor parses valid hex colors`() {
    assertEquals(Color(0xFFFF0000), parseColor("#FF0000"))
    assertEquals(Color(0xFF00E5FF), parseColor("#00e5ff"))
  }

  @Test
  fun `parseColor falls back to the primary color for invalid input`() {
    assertEquals(PolishPrimary, parseColor("#GGGGGG"))
    assertEquals(PolishPrimary, parseColor(""))
    assertEquals(PolishPrimary, parseColor("not a color"))
  }

  // --- parseBoldMarkdown ---

  @Test
  fun `plain text produces no styled spans`() {
    val result = parseBoldMarkdown("just plain text")

    assertEquals("just plain text", result.text)
    assertTrue(result.spanStyles.isEmpty())
  }

  @Test
  fun `double asterisks mark their content as bold`() {
    val result = parseBoldMarkdown("**bold** and normal")

    assertEquals("bold and normal", result.text)
    assertEquals(1, result.spanStyles.size)
    val boldSpan = result.spanStyles.single()
    assertEquals(FontWeight.Bold, boldSpan.item.fontWeight)
    assertEquals("bold", result.text.substring(boldSpan.start, boldSpan.end))
  }

  @Test
  fun `multiple bold sections each get their own span`() {
    val result = parseBoldMarkdown("a **b** c **d** e")

    assertEquals("a b c d e", result.text)
    assertEquals(2, result.spanStyles.size)
    val boldWords = result.spanStyles.map { result.text.substring(it.start, it.end) }
    assertEquals(listOf("b", "d"), boldWords)
  }

  @Test
  fun `unbalanced markers still render without crashing`() {
    // Documents current behavior: a dangling "**" treats the remainder as bold.
    val result = parseBoldMarkdown("**oops")

    assertEquals("oops", result.text)
    assertEquals(1, result.spanStyles.size)
  }
}
