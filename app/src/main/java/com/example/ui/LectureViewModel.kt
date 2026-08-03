package com.example.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File

sealed interface ScreenState {
    object Dashboard : ScreenState
    object RecordNew : ScreenState
    data class LectureDetail(val lectureId: Int) : ScreenState
}

class LectureViewModel(
    application: Application,
    private val repository: LectureRepository,
    // Injectable so unit tests can substitute a fake microphone controller.
    private val audioRecorderFactory: () -> AudioRecorderController = {
        MediaRecorderController(application)
    }
) : AndroidViewModel(application) {

    private val _currentScreen = MutableStateFlow<ScreenState>(ScreenState.Dashboard)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    // Database Flows
    val subjects: StateFlow<List<Subject>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lectures: StateFlow<List<Lecture>> = repository.allLectures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active details
    private val _activeLecture = MutableStateFlow<Lecture?>(null)
    val activeLecture: StateFlow<Lecture?> = _activeLecture.asStateFlow()

    private val _activeFlashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val activeFlashcards: StateFlow<List<Flashcard>> = _activeFlashcards.asStateFlow()

    // Form states
    var selectedSubjectId = MutableStateFlow<Int?>(null)
    var lectureTitle = MutableStateFlow("")
    var transcriptInput = MutableStateFlow("")

    // Recording states (real microphone capture via [AudioRecorderController])
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0) // seconds
    val recordingDuration: StateFlow<Int> = _recordingDuration.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    // Keyword interactive definition state
    private val _selectedKeyword = MutableStateFlow<String?>(null)
    val selectedKeyword: StateFlow<String?> = _selectedKeyword.asStateFlow()

    private val _keywordDefinition = MutableStateFlow<String?>(null)
    val keywordDefinition: StateFlow<String?> = _keywordDefinition.asStateFlow()

    private val _isDefiningKeyword = MutableStateFlow(false)
    val isDefiningKeyword: StateFlow<Boolean> = _isDefiningKeyword.asStateFlow()

    // Global Dark Mode Theme State
    val isDarkMode = MutableStateFlow(false)

    fun toggleTheme() {
        isDarkMode.value = !isDarkMode.value
    }

    // Study Slots Flow
    val studySlots: StateFlow<List<StudySlot>> = repository.allStudySlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Quiz States
    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _isGeneratingQuiz = MutableStateFlow(false)
    val isGeneratingQuiz: StateFlow<Boolean> = _isGeneratingQuiz.asStateFlow()

    // Audio Import States
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _uploadedFileName = MutableStateFlow<String?>(null)
    val uploadedFileName: StateFlow<String?> = _uploadedFileName.asStateFlow()

    // Imported audio awaiting transcription (local app-storage copy + its MIME type)
    private val pendingAudioFile = MutableStateFlow<File?>(null)
    private val pendingAudioMimeType = MutableStateFlow<String?>(null)

    fun selectKeyword(keyword: String?, transcriptText: String = "") {
        if (keyword == null) {
            _selectedKeyword.value = null
            _keywordDefinition.value = null
            _isDefiningKeyword.value = false
            return
        }

        _selectedKeyword.value = keyword
        _isDefiningKeyword.value = true
        _keywordDefinition.value = null

        viewModelScope.launch {
            try {
                val definition = GeminiClient.defineKeyword(keyword, transcriptText)
                _keywordDefinition.value = definition
            } catch (e: Exception) {
                _keywordDefinition.value = "Failed to load definition: ${e.localizedMessage}"
            } finally {
                _isDefiningKeyword.value = false
            }
        }
    }

    // Focus Study Timer States
    private val _timerSecondsLeft = MutableStateFlow(1500) // 25 minutes default
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _timerPresetSeconds = MutableStateFlow(1500)
    val timerPresetSeconds: StateFlow<Int> = _timerPresetSeconds.asStateFlow()

    private var timerJob: Job? = null

    fun startTimer() {
        if (_isTimerRunning.value) return
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (_timerSecondsLeft.value > 0 && _isTimerRunning.value) {
                delay(1000)
                _timerSecondsLeft.value -= 1
            }
            if (_timerSecondsLeft.value == 0) {
                _isTimerRunning.value = false
                _uiMessage.value = "Great job! Your study session is complete!"
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        _timerSecondsLeft.value = _timerPresetSeconds.value
    }

    fun setTimerPreset(minutes: Int) {
        pauseTimer()
        val seconds = minutes * 60
        _timerPresetSeconds.value = seconds
        _timerSecondsLeft.value = seconds
    }

    private var recordingJob: Job? = null

    private companion object {
        const val TAG = "LectureViewModel"
    }

    init {
        // Seed default subjects if database is empty
        viewModelScope.launch {
            subjects.collect { list ->
                if (list.isEmpty()) {
                    repository.insertSubject(Subject(name = "Computer Science", colorHex = "#00E5FF"))
                    repository.insertSubject(Subject(name = "Economics & Finance", colorHex = "#FF4D6D"))
                    repository.insertSubject(Subject(name = "World History", colorHex = "#7000FF"))
                    repository.insertSubject(Subject(name = "General Science", colorHex = "#FFC107"))
                }
            }
        }
    }

    fun navigateTo(screen: ScreenState) {
        _currentScreen.value = screen
        if (screen is ScreenState.LectureDetail) {
            loadLectureDetails(screen.lectureId)
        } else {
            // Discard any in-progress capture when navigating away
            cancelRecording()
        }
    }

    private fun loadLectureDetails(lectureId: Int) {
        viewModelScope.launch {
            repository.getLectureById(lectureId).collect { lecture ->
                _activeLecture.value = lecture
            }
        }
        viewModelScope.launch {
            repository.getFlashcardsForLecture(lectureId).collect { cards ->
                _activeFlashcards.value = cards
            }
        }
    }

    // --- Microphone Recording (real capture) ---

    private var activeRecorder: AudioRecorderController? = null
    private var recordingFile: File? = null

    /**
     * Starts capturing microphone audio. The UI must obtain RECORD_AUDIO
     * permission before calling this.
     */
    fun startRecording() {
        if (_isRecording.value) return

        val context = getApplication<Application>()
        val file = File(
            context.cacheDir,
            "recording_${System.currentTimeMillis()}$RECORDED_AUDIO_FILE_EXTENSION"
        )
        val recorder = audioRecorderFactory()

        try {
            recorder.start(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start microphone recording", e)
            file.delete()
            _uiMessage.value = "Could not start microphone: ${e.localizedMessage ?: "unknown error"}"
            return
        }

        activeRecorder = recorder
        recordingFile = file
        _isRecording.value = true
        _recordingDuration.value = 0
        transcriptInput.value = ""

        // Tick real elapsed time while capturing.
        recordingJob = viewModelScope.launch {
            while (_isRecording.value) {
                delay(1000)
                _recordingDuration.value += 1
            }
        }
    }

    /** Stops the capture and kicks off transcription of what was recorded. */
    fun stopRecording() {
        if (!_isRecording.value && activeRecorder == null) return
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        val recorder = activeRecorder
        val file = recordingFile
        activeRecorder = null
        recordingFile = null

        if (recorder == null || file == null) return

        var failed = false
        try {
            recorder.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recording", e)
            failed = true
        }
        if (failed || !file.exists() || file.length() == 0L) {
            file.delete()
            _uiMessage.value = "Recording was too short — no audio was captured."
            return
        }

        transcribeAudioFile(file, RECORDED_AUDIO_MIME_TYPE)
    }

    /** Aborts an in-progress capture and discards the partial audio. */
    fun cancelRecording() {
        if (activeRecorder == null && !_isRecording.value) return
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        activeRecorder?.cancel()
        activeRecorder = null
        recordingFile?.delete()
        recordingFile = null
        _recordingDuration.value = 0
    }

    /** Called by the UI when the user denies microphone access. */
    fun onMicPermissionDenied() {
        _uiMessage.value = "Microphone permission is required to record lectures. " +
            "You can still import an audio file or paste a transcript below."
    }

    // --- Database Operations ---

    fun createSubject(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertSubject(Subject(name = name, colorHex = colorHex))
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    fun deleteLecture(lecture: Lecture) {
        viewModelScope.launch {
            repository.deleteLecture(lecture)
            navigateTo(ScreenState.Dashboard)
        }
    }

    fun toggleFlashcardMastery(card: Flashcard) {
        viewModelScope.launch {
            repository.updateFlashcardMastery(card.id, !card.isMastered)
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    // --- Analyze Transcript using Gemini API ---

    fun analyzeAndSaveLecture() {
        val subjectId = selectedSubjectId.value
        val title = lectureTitle.value.trim()
        val transcript = transcriptInput.value.trim()

        if (subjectId == null) {
            _uiMessage.value = "Please select a subject first!"
            return
        }
        if (title.isEmpty()) {
            _uiMessage.value = "Please enter a lecture title!"
            return
        }
        if (transcript.isEmpty()) {
            _uiMessage.value = "Please record or paste a lecture transcript!"
            return
        }

        _isAnalyzing.value = true
        _uiMessage.value = "AI is summarizing your lecture... Generating active flashcards..."

        viewModelScope.launch {
            try {
                // Find subject name
                val subjectList = subjects.value
                val subject = subjectList.find { it.id == subjectId }
                val subjectName = subject?.name ?: "General"

                val result = GeminiClient.summarizeLecture(
                    subjectName = subjectName,
                    lectureTitle = title,
                    transcriptText = transcript
                )

                if (result != null) {
                    val lectureToSave = Lecture(
                        subjectId = subjectId,
                        title = result.title,
                        transcript = transcript,
                        summary = result.summary,
                        audioDurationSec = _recordingDuration.value,
                        keywords = result.keywords?.joinToString(", ") ?: "",
                        summaryShort = result.summaryShort ?: "",
                        summaryMedium = result.summaryMedium ?: "",
                        summaryDetailed = result.summaryDetailed ?: ""
                    )
                    val newLectureId = repository.saveLectureWithFlashcards(lectureToSave, result.flashcards)
                    
                    // Reset form
                    lectureTitle.value = ""
                    transcriptInput.value = ""
                    selectedSubjectId.value = null
                    _recordingDuration.value = 0
                    
                    _isAnalyzing.value = false
                    _uiMessage.value = "Successfully summarized!"
                    navigateTo(ScreenState.LectureDetail(newLectureId.toInt()))
                } else {
                    _isAnalyzing.value = false
                    _uiMessage.value = "Failed to analyze lecture. Please try again."
                }
            } catch (e: Exception) {
                Log.e("LectureViewModel", "Error analyzing lecture", e)
                _isAnalyzing.value = false
                _uiMessage.value = "Error: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun toggleKeywordMastery(lecture: Lecture, keyword: String) {
        viewModelScope.launch {
            val list = lecture.masteredKeywords.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (list.contains(keyword)) {
                list.remove(keyword)
            } else {
                list.add(keyword)
            }
            val updatedLecture = lecture.copy(masteredKeywords = list.joinToString(", "))
            repository.updateLecture(updatedLecture)
            // If we have an active lecture, update its state
            if (_activeLecture.value?.id == lecture.id) {
                _activeLecture.value = updatedLecture
            }
        }
    }

    fun toggleSummaryCompleted(lecture: Lecture) {
        viewModelScope.launch {
            val updatedLecture = lecture.copy(isSummaryCompleted = !lecture.isSummaryCompleted)
            repository.updateLecture(updatedLecture)
            if (_activeLecture.value?.id == lecture.id) {
                _activeLecture.value = updatedLecture
            }
        }
    }

    // --- Study Planner Actions ---

    fun scheduleStudySlot(lectureId: Int, lectureTitle: String, dayOfWeek: Int, timeLabel: String) {
        viewModelScope.launch {
            val slot = StudySlot(
                lectureId = lectureId,
                lectureTitle = lectureTitle,
                dayOfWeek = dayOfWeek,
                timeLabel = timeLabel,
                isCompleted = false
            )
            repository.insertStudySlot(slot)
            showLocalNotification(
                "Study Session Scheduled",
                "Review for '$lectureTitle' is scheduled for ${getDayName(dayOfWeek)} at $timeLabel."
            )
        }
    }

    fun toggleStudySlotCompleted(slot: StudySlot) {
        viewModelScope.launch {
            repository.updateStudySlotStatus(slot.id, !slot.isCompleted)
            if (!slot.isCompleted) {
                showLocalNotification("Session Completed! 🎉", "Great job reviewing: ${slot.lectureTitle}!")
            }
        }
    }

    fun deleteStudySlot(slot: StudySlot) {
        viewModelScope.launch {
            repository.deleteStudySlot(slot)
        }
    }

    fun getDayName(day: Int): String {
        return when (day) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Unknown"
        }
    }

    private fun showLocalNotification(title: String, message: String) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        val channelId = "study_brain_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Study Planner Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled study reviews"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("LectureViewModel", "Error showing local notification", e)
        }
    }

    // --- Quiz Generation Action ---

    fun generateQuizForActiveLecture(lecture: Lecture) {
        _isGeneratingQuiz.value = true
        _quizQuestions.value = emptyList()
        _uiMessage.value = "AI is composing your active quiz challenge..."
        
        viewModelScope.launch {
            try {
                // Find subject name
                val subjectList = subjects.value
                val subject = subjectList.find { it.id == lecture.subjectId }
                val subjectName = subject?.name ?: "General"

                val questions = GeminiClient.generateQuiz(
                    subjectName = subjectName,
                    lectureTitle = lecture.title,
                    summaryText = lecture.summary,
                    keywords = lecture.keywords
                )

                if (questions != null && questions.isNotEmpty()) {
                    _quizQuestions.value = questions
                    _uiMessage.value = "Quiz generated successfully! Test your knowledge."
                } else {
                    _uiMessage.value = "Failed to generate quiz. Try again."
                }
            } catch (e: Exception) {
                Log.e("LectureViewModel", "Quiz generation error", e)
                _uiMessage.value = "Error generating quiz: ${e.localizedMessage}"
            } finally {
                _isGeneratingQuiz.value = false
            }
        }
    }

    // --- Course-Folder Move Action ---

    fun moveLectureToSubject(lecture: Lecture, newSubjectId: Int) {
        viewModelScope.launch {
            val updatedLecture = lecture.copy(subjectId = newSubjectId)
            repository.updateLecture(updatedLecture)
            if (_activeLecture.value?.id == lecture.id) {
                _activeLecture.value = updatedLecture
            }
            _uiMessage.value = "Moved to new subject folder!"
        }
    }

    // --- Audio File Import / Transcription Actions ---

    /**
     * Copies the user-picked audio into app storage (so we never depend on
     * outliving the source Uri's read grant), probes its real duration, and
     * stages it for transcription.
     */
    fun importAudio(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiMessage.value = "Importing audio file..."
            try {
                val context = getApplication<Application>()
                val cleanName = displayName.substringAfterLast('/')
                    .ifBlank { "imported_audio" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                val local = withContext(Dispatchers.IO) {
                    val dest = File(context.cacheDir, "import_${System.currentTimeMillis()}_$cleanName")
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("The selected file could not be opened")
                    stream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
                    dest
                }

                pendingAudioFile.value?.takeIf { it != local }?.delete()
                pendingAudioFile.value = local
                pendingAudioMimeType.value = audioMimeTypeFor(
                    displayName,
                    context.contentResolver.getType(uri)
                )
                _uploadedFileName.value = displayName

                val seconds = withContext(Dispatchers.IO) { probeAudioDurationSeconds(local) }
                if (seconds != null) _recordingDuration.value = seconds

                if (lectureTitle.value.isEmpty()) {
                    lectureTitle.value = deriveTitleFromFileName(displayName)
                }
                _uiMessage.value = "Audio imported. Tap Transcribe Audio with AI."
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import audio", e)
                _uiMessage.value = "Could not import audio: ${e.localizedMessage ?: "unknown error"}"
            }
        }
    }

    fun clearUploadedAudio() {
        _uploadedFileName.value = null
        pendingAudioMimeType.value = null
        pendingAudioFile.value?.delete()
        pendingAudioFile.value = null
    }

    /** Transcribes the previously imported audio file via Gemini. */
    fun transcribeUploadedAudio() {
        val file = pendingAudioFile.value
        if (file == null || !file.exists()) {
            _uiMessage.value = "Please choose an audio file first."
            return
        }
        transcribeAudioFile(file, pendingAudioMimeType.value ?: "audio/mp3")
    }

    /**
     * Shared transcription pipeline for recorded and imported audio:
     * reads the bytes, sends them to Gemini, and fills the transcript field.
     */
    private fun transcribeAudioFile(file: File, mimeType: String) {
        _isTranscribing.value = true
        _uiMessage.value = "Transcribing audio with Gemini..."
        viewModelScope.launch {
            val result = try {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                GeminiClient.transcribeAudio(mimeType = mimeType, audioBytes = bytes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read audio for transcription", e)
                TranscriptionResult.Error(
                    "Could not read the audio file: ${e.localizedMessage ?: "unknown error"}"
                )
            }
            _isTranscribing.value = false
            when (result) {
                is TranscriptionResult.Success -> {
                    transcriptInput.value = result.text
                    _uiMessage.value = "Transcription complete! Review it below, then tap Summarize."
                }
                is TranscriptionResult.Error -> {
                    _uiMessage.value = result.message
                }
            }
        }
    }

    /** Reads the media duration; returns null if the container can't be probed. */
    private fun probeAudioDurationSeconds(file: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            ms?.takeIf { it > 0 }?.let { (it / 1000).toInt() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not probe audio duration for ${file.name}", e)
            null
        } finally {
            retriever.release()
        }
    }

    private fun deriveTitleFromFileName(fileName: String): String {
        return fileName.substringAfterLast('/')
            .substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    // Factory Class
    class Factory(
        private val application: Application,
        private val repository: LectureRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LectureViewModel::class.java)) {
                return LectureViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
