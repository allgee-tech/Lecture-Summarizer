package com.example.data

import kotlinx.coroutines.flow.Flow

class LectureRepository(
    private val subjectDao: SubjectDao,
    private val lectureDao: LectureDao,
    private val flashcardDao: FlashcardDao,
    private val studySlotDao: StudySlotDao
) {
    val allSubjects: Flow<List<Subject>> = subjectDao.getAllSubjects()
    val allLectures: Flow<List<Lecture>> = lectureDao.getAllLectures()
    val allStudySlots: Flow<List<StudySlot>> = studySlotDao.getAllStudySlots()

    fun getLecturesForSubject(subjectId: Int): Flow<List<Lecture>> {
        return lectureDao.getLecturesBySubject(subjectId)
    }

    fun getLectureById(id: Int): Flow<Lecture?> {
        return lectureDao.getLectureById(id)
    }

    fun getFlashcardsForLecture(lectureId: Int): Flow<List<Flashcard>> {
        return flashcardDao.getFlashcardsForLecture(lectureId)
    }

    suspend fun insertSubject(subject: Subject): Long {
        return subjectDao.insertSubject(subject)
    }

    suspend fun deleteSubject(subject: Subject) {
        subjectDao.deleteSubject(subject)
    }

    suspend fun insertStudySlot(slot: StudySlot): Long {
        return studySlotDao.insertStudySlot(slot)
    }

    suspend fun updateStudySlotStatus(id: Int, isCompleted: Boolean) {
        studySlotDao.updateStudySlotStatus(id, isCompleted)
    }

    suspend fun deleteStudySlot(slot: StudySlot) {
        studySlotDao.deleteStudySlot(slot)
    }

    /**
     * Inserts a lecture and its accompanying flashcards into the database.
     */
    suspend fun saveLectureWithFlashcards(
        lecture: Lecture,
        flashcardList: List<FlashcardJson>
    ): Long {
        val lectureId = lectureDao.insertLecture(lecture).toInt()
        val flashcardsToSave = flashcardList.map {
            Flashcard(
                lectureId = lectureId,
                front = it.front,
                back = it.back,
                isMastered = false
            )
        }
        if (flashcardsToSave.isNotEmpty()) {
            flashcardDao.insertFlashcards(flashcardsToSave)
        }
        return lectureId.toLong()
    }

    suspend fun deleteLecture(lecture: Lecture) {
        lectureDao.deleteLecture(lecture)
    }

    suspend fun updateLecture(lecture: Lecture) {
        lectureDao.updateLecture(lecture)
    }

    suspend fun updateFlashcardMastery(flashcardId: Int, isMastered: Boolean) {
        flashcardDao.updateFlashcardMastery(flashcardId, isMastered)
    }
}
