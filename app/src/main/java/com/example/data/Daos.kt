package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Delete
    suspend fun deleteSubject(subject: Subject)
}

@Dao
interface LectureDao {
    @Query("SELECT * FROM lectures ORDER BY date DESC")
    fun getAllLectures(): Flow<List<Lecture>>

    @Query("SELECT * FROM lectures WHERE subjectId = :subjectId ORDER BY date DESC")
    fun getLecturesBySubject(subjectId: Int): Flow<List<Lecture>>

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    fun getLectureById(id: Int): Flow<Lecture?>

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    suspend fun getLectureByIdSync(id: Int): Lecture?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: Lecture): Long

    @Update
    suspend fun updateLecture(lecture: Lecture)

    @Delete
    suspend fun deleteLecture(lecture: Lecture)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE lectureId = :lectureId")
    fun getFlashcardsForLecture(lectureId: Int): Flow<List<Flashcard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: Flashcard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<Flashcard>)

    @Query("UPDATE flashcards SET isMastered = :isMastered WHERE id = :id")
    suspend fun updateFlashcardMastery(id: Int, isMastered: Boolean)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcard(id: Int)
}

@Dao
interface StudySlotDao {
    @Query("SELECT * FROM study_slots ORDER BY dayOfWeek ASC, timeLabel ASC")
    fun getAllStudySlots(): Flow<List<StudySlot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySlot(slot: StudySlot): Long

    @Query("UPDATE study_slots SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateStudySlotStatus(id: Int, isCompleted: Boolean)

    @Delete
    suspend fun deleteStudySlot(slot: StudySlot)
}
