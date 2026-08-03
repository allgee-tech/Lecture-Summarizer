package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.MainDispatcherRule
import com.example.data.AppDatabase
import com.example.data.AudioRecorderController
import com.example.data.FlashcardJson
import com.example.data.Lecture
import com.example.data.LectureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Test double for microphone capture — no audio hardware involved. */
class FakeAudioRecorderController : AudioRecorderController {

  var started = false
  var stopped = false
  var cancelled = false
  var failOnStartWith: String? = null
  var failOnStop = false

  override fun start(outputFile: File) {
    failOnStartWith?.let { throw java.io.IOException(it) }
    started = true
    // Non-empty file so stopRecording() treats it as a valid capture.
    outputFile.writeBytes(ByteArray(64) { it.toByte() })
  }

  override fun stop() {
    if (failOnStop) throw RuntimeException("no frames captured")
    stopped = true
  }

  override fun cancel() {
    cancelled = true
  }
}

/**
 * Drives [LectureViewModel] with a real in-memory Room database. Coroutines
 * launched in `viewModelScope` run eagerly thanks to [MainDispatcherRule];
 * where state changes cross real Room threads we await them through the
 * exposed flows (`first { ... }`) instead of racing on instantaneous values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LectureViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private lateinit var db: AppDatabase
  private lateinit var repository: LectureRepository
  private lateinit var viewModel: LectureViewModel
  private lateinit var fakeRecorder: FakeAudioRecorderController

  private val apiKeyIsPlaceholder: Boolean
    get() = BuildConfig.GEMINI_API_KEY.isBlank() ||
      BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY"

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = LectureRepository(
      subjectDao = db.subjectDao(),
      lectureDao = db.lectureDao(),
      flashcardDao = db.flashcardDao(),
      studySlotDao = db.studySlotDao()
    )
    fakeRecorder = FakeAudioRecorderController()
    viewModel = LectureViewModel(app, repository) { fakeRecorder }
  }

  @After
  fun tearDown() {
    db.close()
  }

  /** Inserts a lecture (with two flashcards) directly through the repository. */
  private suspend fun seedLecture(
    subjectId: Int,
    title: String = "Neural Networks Intro",
    keywords: String = "ReLU, Sigmoid"
  ): Int {
    return repository.saveLectureWithFlashcards(
      Lecture(
        subjectId = subjectId,
        title = title,
        transcript = "transcript",
        summary = "summary",
        keywords = keywords
      ),
      listOf(
        FlashcardJson(front = "F1", back = "B1"),
        FlashcardJson(front = "F2", back = "B2")
      )
    ).toInt()
  }

  // --- Startup / seeding ---

  @Test
  fun `init seeds the four default subjects into an empty database`() = runTest {
    val subjects = viewModel.subjects.filter { it.isNotEmpty() }.first()

    assertEquals(4, subjects.size)
    assertEquals(
      listOf("Computer Science", "Economics & Finance", "General Science", "World History"),
      subjects.map { it.name }
    )
    assertTrue(subjects.all { it.colorHex.isNotBlank() })
  }

  // --- Navigation ---

  @Test
  fun `navigating to a lecture detail loads the lecture and its flashcards`() = runTest {
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId)

    viewModel.navigateTo(ScreenState.LectureDetail(lectureId))

    assertEquals(ScreenState.LectureDetail(lectureId), viewModel.currentScreen.value)
    assertEquals("Neural Networks Intro", viewModel.activeLecture.filterNotNull().first().title)
    assertEquals(2, viewModel.activeFlashcards.filter { it.isNotEmpty() }.first().size)
  }

  /** Creates a small fake audio file behind a file:// Uri for import tests. */
  private fun stageAudioFile(name: String = "source_sample.mp3"): Uri {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val file = File(app.cacheDir, "test_stage_$name")
    file.writeBytes(ByteArray(256) { (it % 7).toByte() })
    return Uri.fromFile(file)
  }

  @Test
  fun `navigating away from a recording stops and discards it`() = runTest {
    viewModel.navigateTo(ScreenState.RecordNew)
    viewModel.startRecording()
    assertTrue(viewModel.isRecording.value)

    viewModel.navigateTo(ScreenState.Dashboard)

    assertFalse(viewModel.isRecording.value)
    assertTrue("the capture must be cancelled", fakeRecorder.cancelled)
    assertFalse("no transcription should kick off", viewModel.isTranscribing.value)
  }

  // --- Analyze & save: validation ---

  @Test
  fun `analyze requires a selected subject`() = runTest {
    viewModel.analyzeAndSaveLecture()

    assertEquals("Please select a subject first!", viewModel.uiMessage.value)
    assertFalse(viewModel.isAnalyzing.value)
  }

  @Test
  fun `analyze requires a title`() = runTest {
    viewModel.selectedSubjectId.value = 1

    viewModel.analyzeAndSaveLecture()

    assertEquals("Please enter a lecture title!", viewModel.uiMessage.value)
  }

  @Test
  fun `analyze requires a transcript`() = runTest {
    viewModel.selectedSubjectId.value = 1
    viewModel.lectureTitle.value = "My Lecture"

    viewModel.analyzeAndSaveLecture()

    assertEquals("Please record or paste a lecture transcript!", viewModel.uiMessage.value)
  }

  // --- Analyze & save: happy path (demo mode) ---

  @Test
  fun `analyze saves the lecture with flashcards then opens its detail page`() = runTest {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    viewModel.selectedSubjectId.value = subjectId
    viewModel.lectureTitle.value = "Neural Networks 101"
    viewModel.transcriptInput.value = "A long lecture transcript about neurons."

    viewModel.analyzeAndSaveLecture()

    // The flow ends by navigating to the saved lecture's detail page.
    val screen = viewModel.currentScreen.first { it is ScreenState.LectureDetail }
      as ScreenState.LectureDetail
    assertEquals("Successfully summarized!", viewModel.uiMessage.value)
    assertFalse(viewModel.isAnalyzing.value)

    // The lecture and its demo flashcards are persisted in Room.
    val saved = repository.getLectureById(screen.lectureId).first()
    assertNotNull(saved)
    assertEquals("Neural Networks 101", saved!!.title)
    assertEquals(subjectId, saved.subjectId)
    assertTrue(saved.keywords.isNotBlank())
    assertEquals(5, repository.getFlashcardsForLecture(screen.lectureId).first().size)

    // The form is reset for the next lecture.
    assertEquals("", viewModel.lectureTitle.value)
    assertEquals("", viewModel.transcriptInput.value)
    assertNull(viewModel.selectedSubjectId.value)
  }

  // --- Lecture state toggles ---

  @Test
  fun `toggleKeywordMastery adds then removes a keyword`() = runTest {
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId)
    viewModel.navigateTo(ScreenState.LectureDetail(lectureId))
    val lecture = repository.getLectureById(lectureId).first()!!

    viewModel.toggleKeywordMastery(lecture, "ReLU")
    repository.getLectureById(lectureId).first {
      it?.masteredKeywords?.contains("ReLU") == true
    }

    viewModel.toggleKeywordMastery(
      repository.getLectureById(lectureId).first()!!, "ReLU"
    )
    repository.getLectureById(lectureId).first {
      it?.masteredKeywords?.contains("ReLU") == false
    }
  }

  @Test
  fun `toggleKeywordMastery keeps existing mastered keywords`() = runTest {
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId)
    viewModel.navigateTo(ScreenState.LectureDetail(lectureId))
    val lecture = repository.getLectureById(lectureId).first()!!

    viewModel.toggleKeywordMastery(lecture, "ReLU")
    repository.getLectureById(lectureId).first {
      it?.masteredKeywords?.contains("ReLU") == true
    }
    viewModel.toggleKeywordMastery(
      repository.getLectureById(lectureId).first()!!, "Sigmoid"
    )

    val mastered = repository.getLectureById(lectureId)
      .first { it?.masteredKeywords?.contains("Sigmoid") == true }!!
      .masteredKeywords.split(",").map { it.trim() }
    assertTrue(mastered.containsAll(listOf("ReLU", "Sigmoid")))
    // The active lecture state reflects the latest value too.
    assertTrue(
      viewModel.activeLecture.first { it?.masteredKeywords?.contains("Sigmoid") == true } != null
    )
  }

  @Test
  fun `toggleSummaryCompleted flips the completion flag`() = runTest {
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId)
    viewModel.navigateTo(ScreenState.LectureDetail(lectureId))
    val lecture = repository.getLectureById(lectureId).first()!!
    assertFalse(lecture.isSummaryCompleted)

    viewModel.toggleSummaryCompleted(lecture)

    assertTrue(repository.getLectureById(lectureId).first { it!!.isSummaryCompleted }!!.isSummaryCompleted)
  }

  @Test
  fun `toggleFlashcardMastery persists the new flag`() = runTest {
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId)
    val card = repository.getFlashcardsForLecture(lectureId).first().first { !it.isMastered }

    viewModel.toggleFlashcardMastery(card)

    repository.getFlashcardsForLecture(lectureId)
      .first { cards -> cards.single { it.id == card.id }.isMastered }
  }

  @Test
  fun `moveLectureToSubject re-files the lecture`() = runTest {
    val subjects = viewModel.subjects.filter { it.isNotEmpty() }.first()
    val (from, to) = subjects[0].id to subjects[1].id
    val lectureId = seedLecture(from)
    viewModel.navigateTo(ScreenState.LectureDetail(lectureId))
    val lecture = repository.getLectureById(lectureId).first()!!

    viewModel.moveLectureToSubject(lecture, to)

    assertEquals(to, repository.getLectureById(lectureId).first { it?.subjectId == to }!!.subjectId)
  }

  @Test
  fun `deleteLecture removes it and returns to the dashboard`() = runTest {
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId)
    viewModel.navigateTo(ScreenState.LectureDetail(lectureId))
    val lecture = repository.getLectureById(lectureId).first()!!

    viewModel.deleteLecture(lecture)

    viewModel.currentScreen.first { it == ScreenState.Dashboard }
    assertNull(repository.getLectureById(lectureId).first())
  }

  // --- Subjects via the ViewModel ---

  @Test
  fun `createSubject then deleteSubject round-trips through the database`() = runTest {
    viewModel.createSubject("Astrophysics", "#123456")
    val created = viewModel.subjects
      .first { list -> list.any { it.name == "Astrophysics" } }
      .single { it.name == "Astrophysics" }

    viewModel.deleteSubject(created)

    viewModel.subjects.first { list -> list.none { it.name == "Astrophysics" } }
  }

  // --- Microphone recording & audio import (with fake recorder) ---

  @Test
  fun `startRecording captures and stopRecording kicks off transcription`() = runTest {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)
    viewModel.transcriptInput.value = "stale text"

    viewModel.startRecording()

    assertTrue(viewModel.isRecording.value)
    assertEquals(0, viewModel.recordingDuration.value)
    assertEquals("", viewModel.transcriptInput.value)
    assertTrue(fakeRecorder.started)

    viewModel.startRecording() // second call is a no-op while recording
    assertTrue(viewModel.isRecording.value)

    viewModel.stopRecording()

    assertFalse(viewModel.isRecording.value)
    assertTrue(fakeRecorder.stopped)
    // In demo mode the attempted transcription ends in an actionable message.
    viewModel.uiMessage.filterNotNull().first { it.contains("API key") }
    assertFalse(viewModel.isTranscribing.value)
  }

  @Test
  fun `startRecording surfaces microphone failures`() = runTest {
    fakeRecorder.failOnStartWith = "mic busy"

    viewModel.startRecording()

    assertFalse(viewModel.isRecording.value)
    assertFalse(fakeRecorder.started)
    assertEquals("Could not start microphone: mic busy", viewModel.uiMessage.value)
  }

  @Test
  fun `stopping a too short recording discards the audio`() = runTest {
    fakeRecorder.failOnStop = true

    viewModel.startRecording()
    viewModel.stopRecording()

    assertEquals(
      "Recording was too short — no audio was captured.",
      viewModel.uiMessage.value
    )
    assertFalse(viewModel.isTranscribing.value)
  }

  @Test
  fun `permission denial explains fallback options`() = runTest {
    viewModel.onMicPermissionDenied()

    val message = viewModel.uiMessage.value
    assertNotNull(message)
    assertTrue(message!!.contains("Microphone permission"))
    assertTrue(message.contains("import an audio file"))
  }

  @Test
  fun `importAudio builds a friendly title and stages the file`() = runTest {
    viewModel.importAudio(stageAudioFile(), "neural_networks-final.mp3")

    viewModel.uiMessage.filterNotNull().first { it.startsWith("Audio imported") }
    assertEquals("neural_networks-final.mp3", viewModel.uploadedFileName.value)
    assertEquals("Neural Networks Final", viewModel.lectureTitle.value)
  }

  @Test
  fun `importAudio never overwrites a title the user typed`() = runTest {
    viewModel.lectureTitle.value = "Keep This Title"

    viewModel.importAudio(stageAudioFile(), "some_audio.mp3")

    viewModel.uiMessage.filterNotNull().first { it.startsWith("Audio imported") }
    assertEquals("Keep This Title", viewModel.lectureTitle.value)
  }

  @Test
  fun `transcribeUploadedAudio without an import warns the user`() = runTest {
    viewModel.transcribeUploadedAudio()

    assertEquals("Please choose an audio file first.", viewModel.uiMessage.value)
  }

  @Test
  fun `transcribeUploadedAudio transcribes the staged file through Gemini`() = runTest {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)
    viewModel.importAudio(stageAudioFile(), "week4_lecture.mp3")
    viewModel.uiMessage.filterNotNull().first { it.startsWith("Audio imported") }

    viewModel.transcribeUploadedAudio()

    // The staged local copy is what gets sent — demo mode reports the key error.
    viewModel.uiMessage.filterNotNull().first { it.contains("API key") }
    assertFalse(viewModel.isTranscribing.value)
  }

  @Test
  fun `clearUploadedAudio discards the staged import`() = runTest {
    viewModel.importAudio(stageAudioFile(), "lecture.mp3")
    viewModel.uiMessage.filterNotNull().first { it.startsWith("Audio imported") }

    viewModel.clearUploadedAudio()

    assertNull(viewModel.uploadedFileName.value)
    viewModel.transcribeUploadedAudio()
    assertEquals("Please choose an audio file first.", viewModel.uiMessage.value)
  }

  // --- Focus timer ---

  @Test
  fun `timer defaults to the 25 minute preset`() = runTest {
    assertEquals(1500, viewModel.timerSecondsLeft.value)
    assertEquals(1500, viewModel.timerPresetSeconds.value)
    assertFalse(viewModel.isTimerRunning.value)
  }

  @Test
  fun `setTimerPreset stops the timer and resets the countdown`() = runTest {
    viewModel.startTimer()

    viewModel.setTimerPreset(45)

    assertFalse(viewModel.isTimerRunning.value)
    assertEquals(2700, viewModel.timerPresetSeconds.value)
    assertEquals(2700, viewModel.timerSecondsLeft.value)
  }

  @Test
  fun `start pause and reset drive the timer state machine`() = runTest {
    viewModel.startTimer()
    assertTrue(viewModel.isTimerRunning.value)

    viewModel.startTimer() // no-op while already running
    assertTrue(viewModel.isTimerRunning.value)

    viewModel.pauseTimer()
    assertFalse(viewModel.isTimerRunning.value)

    viewModel.resetTimer()
    assertEquals(viewModel.timerPresetSeconds.value, viewModel.timerSecondsLeft.value)
    assertFalse(viewModel.isTimerRunning.value)
  }

  // --- Quiz generation (demo mode) ---

  @Test
  fun `generateQuiz produces demo questions for the active lecture`() = runTest {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)
    val subjectId = viewModel.subjects.filter { it.isNotEmpty() }.first().first().id
    val lectureId = seedLecture(subjectId, title = "Neural Networks Intro")
    val lecture = repository.getLectureById(lectureId).first()!!

    viewModel.generateQuizForActiveLecture(lecture)

    assertFalse(viewModel.isGeneratingQuiz.value)
    assertEquals("Quiz generated successfully! Test your knowledge.", viewModel.uiMessage.value)
    assertEquals(3, viewModel.quizQuestions.value.size)
    viewModel.quizQuestions.value.forEach { q ->
      assertTrue(q.correctOptionIndex in q.options.indices)
    }
  }

  // --- Keyword definitions (demo mode) ---

  @Test
  fun `selectKeyword loads and clears a demo definition`() = runTest {
    assumeTrue("requires the placeholder API key", apiKeyIsPlaceholder)

    viewModel.selectKeyword("Active Recall", "transcript")

    assertEquals("Active Recall", viewModel.selectedKeyword.value)
    val definition = viewModel.keywordDefinition.value
    assertNotNull(definition)
    assertTrue(definition!!.contains("retriev", ignoreCase = true))
    assertFalse(viewModel.isDefiningKeyword.value)

    viewModel.selectKeyword(null)

    assertNull(viewModel.selectedKeyword.value)
    assertNull(viewModel.keywordDefinition.value)
  }

  // --- Misc synchronous state ---

  @Test
  fun `theme toggle flips the dark mode flag`() = runTest {
    assertFalse(viewModel.isDarkMode.value)

    viewModel.toggleTheme()
    assertTrue(viewModel.isDarkMode.value)

    viewModel.toggleTheme()
    assertFalse(viewModel.isDarkMode.value)
  }

  @Test
  fun `clearMessage resets the ui message`() = runTest {
    viewModel.analyzeAndSaveLecture() // sets a validation message
    assertNotNull(viewModel.uiMessage.value)

    viewModel.clearMessage()

    assertNull(viewModel.uiMessage.value)
  }

  @Test
  fun `getDayName maps weekdays and rejects out-of-range values`() = runTest {
    assertEquals("Monday", viewModel.getDayName(1))
    assertEquals("Friday", viewModel.getDayName(5))
    assertEquals("Sunday", viewModel.getDayName(7))
    assertEquals("Unknown", viewModel.getDayName(0))
    assertEquals("Unknown", viewModel.getDayName(8))
  }
}
