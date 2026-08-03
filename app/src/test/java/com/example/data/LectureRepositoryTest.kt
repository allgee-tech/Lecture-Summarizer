package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [LectureRepository] and the Room layer (DAOs, entities, foreign-key
 * cascades and query ordering) against a real in-memory SQLite database, so
 * no Android device or emulator is required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LectureRepositoryTest {

  private lateinit var db: AppDatabase
  private lateinit var repository: LectureRepository

  @Before
  fun createDb() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = LectureRepository(
      subjectDao = db.subjectDao(),
      lectureDao = db.lectureDao(),
      flashcardDao = db.flashcardDao(),
      studySlotDao = db.studySlotDao()
    )
  }

  @After
  fun closeDb() {
    db.close()
  }

  private suspend fun insertSubject(name: String = "Computer Science"): Int {
    return repository.insertSubject(Subject(name = name, colorHex = "#00E5FF")).toInt()
  }

  private fun lectureFor(subjectId: Int, title: String = "Lecture", date: Long = 1_000L): Lecture {
    return Lecture(
      subjectId = subjectId,
      title = title,
      date = date,
      transcript = "transcript",
      summary = "summary"
    )
  }

  // --- Subjects ---

  @Test
  fun `subjects are returned ordered by name ascending`() = runTest {
    repository.insertSubject(Subject(name = "Zoology", colorHex = "#111111"))
    repository.insertSubject(Subject(name = "Algebra", colorHex = "#222222"))
    repository.insertSubject(Subject(name = "Music", colorHex = "#333333"))

    val names = repository.allSubjects.first().map { it.name }

    assertEquals(listOf("Algebra", "Music", "Zoology"), names)
  }

  // --- Lectures + flashcards ---

  @Test
  fun `saveLectureWithFlashcards links cards to the generated lecture id`() = runTest {
    val subjectId = insertSubject()
    val cards = listOf(
      FlashcardJson(front = "F1", back = "B1"),
      FlashcardJson(front = "F2", back = "B2")
    )

    val lectureId = repository.saveLectureWithFlashcards(lectureFor(subjectId), cards)
    assertTrue(lectureId > 0)

    val savedCards = repository.getFlashcardsForLecture(lectureId.toInt()).first()
    assertEquals(2, savedCards.size)
    assertTrue(savedCards.all { it.lectureId == lectureId.toInt() })
    assertEquals(setOf("F1", "F2"), savedCards.map { it.front }.toSet())
    assertTrue("new flashcards must start unmastered", savedCards.none { it.isMastered })
  }

  @Test
  fun `saveLectureWithFlashcards with no cards still saves the lecture`() = runTest {
    val subjectId = insertSubject()

    val lectureId = repository.saveLectureWithFlashcards(lectureFor(subjectId), emptyList())

    val lecture = repository.getLectureById(lectureId.toInt()).first()
    assertNotNull(lecture)
    assertTrue(repository.getFlashcardsForLecture(lectureId.toInt()).first().isEmpty())
  }

  @Test
  fun `getLecturesForSubject returns newest lectures first`() = runTest {
    val subjectId = insertSubject()
    repository.saveLectureWithFlashcards(
      lectureFor(subjectId, title = "Oldest", date = 1_000L), emptyList()
    )
    repository.saveLectureWithFlashcards(
      lectureFor(subjectId, title = "Newest", date = 3_000L), emptyList()
    )
    repository.saveLectureWithFlashcards(
      lectureFor(subjectId, title = "Middle", date = 2_000L), emptyList()
    )

    val titles = repository.getLecturesForSubject(subjectId).first().map { it.title }

    assertEquals(listOf("Newest", "Middle", "Oldest"), titles)
  }

  @Test
  fun `getLectureById returns null for a missing lecture`() = runTest {
    assertNull(repository.getLectureById(42).first())
  }

  @Test
  fun `deleting a lecture cascades to its flashcards`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository.saveLectureWithFlashcards(
      lectureFor(subjectId),
      listOf(FlashcardJson(front = "F", back = "B"))
    ).toInt()
    val lecture = repository.getLectureById(lectureId).first()!!

    repository.deleteLecture(lecture)

    assertNull(repository.getLectureById(lectureId).first())
    assertTrue(
      "flashcards must be removed by the ON DELETE CASCADE foreign key",
      repository.getFlashcardsForLecture(lectureId).first().isEmpty()
    )
  }

  @Test
  fun `deleting a subject cascades to its lectures and their flashcards`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository.saveLectureWithFlashcards(
      lectureFor(subjectId),
      listOf(FlashcardJson(front = "F", back = "B"))
    ).toInt()
    val subject = repository.allSubjects.first().single { it.id == subjectId }

    repository.deleteSubject(subject)

    assertTrue(repository.getLecturesForSubject(subjectId).first().isEmpty())
    assertTrue(repository.getFlashcardsForLecture(lectureId).first().isEmpty())
  }

  // --- Updates ---

  @Test
  fun `updateLecture persists changed fields`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository
      .saveLectureWithFlashcards(lectureFor(subjectId), emptyList())
      .toInt()
    val lecture = repository.getLectureById(lectureId).first()!!

    repository.updateLecture(
      lecture.copy(isSummaryCompleted = true, masteredKeywords = "ReLU, Sigmoid")
    )

    val updated = repository.getLectureById(lectureId).first()!!
    assertTrue(updated.isSummaryCompleted)
    assertEquals("ReLU, Sigmoid", updated.masteredKeywords)
  }

  @Test
  fun `updateFlashcardMastery toggles the flag`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository.saveLectureWithFlashcards(
      lectureFor(subjectId),
      listOf(FlashcardJson(front = "F", back = "B"))
    ).toInt()
    val cardId = repository.getFlashcardsForLecture(lectureId).first().single().id

    repository.updateFlashcardMastery(cardId, true)
    assertTrue(repository.getFlashcardsForLecture(lectureId).first().single().isMastered)

    repository.updateFlashcardMastery(cardId, false)
    assertTrue(!repository.getFlashcardsForLecture(lectureId).first().single().isMastered)
  }

  // --- Study slots ---

  @Test
  fun `study slots are ordered by day then time`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository
      .saveLectureWithFlashcards(lectureFor(subjectId), emptyList())
      .toInt()

    suspend fun slot(day: Int, time: String) = repository.insertStudySlot(
      StudySlot(lectureId = lectureId, lectureTitle = "L", dayOfWeek = day, timeLabel = time)
    )
    slot(3, "09:00 AM")
    slot(1, "05:00 PM")
    slot(1, "09:00 AM")

    val slots = repository.allStudySlots.first()

    assertEquals(listOf(1, 1, 3), slots.map { it.dayOfWeek })
    // timeLabel is ordered lexicographically by SQLite, and "05:00 PM" < "09:00 AM".
    assertEquals(listOf("05:00 PM", "09:00 AM", "09:00 AM"), slots.map { it.timeLabel })
  }

  @Test
  fun `updateStudySlotStatus marks a slot completed`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository
      .saveLectureWithFlashcards(lectureFor(subjectId), emptyList())
      .toInt()
    val slotId = repository.insertStudySlot(
      StudySlot(lectureId = lectureId, lectureTitle = "L", dayOfWeek = 2, timeLabel = "10:00 AM")
    ).toInt()

    repository.updateStudySlotStatus(slotId, true)

    assertTrue(repository.allStudySlots.first().single { it.id == slotId }.isCompleted)
  }

  @Test
  fun `deleting a lecture cascades to its study slots`() = runTest {
    val subjectId = insertSubject()
    val lectureId = repository
      .saveLectureWithFlashcards(lectureFor(subjectId), emptyList())
      .toInt()
    repository.insertStudySlot(
      StudySlot(lectureId = lectureId, lectureTitle = "L", dayOfWeek = 4, timeLabel = "11:00 AM")
    )
    val lecture = repository.getLectureById(lectureId).first()!!

    repository.deleteLecture(lecture)

    assertTrue(repository.allStudySlots.first().isEmpty())
  }
}
