package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String
)

@Entity(
    tableName = "lectures",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId"])]
)
data class Lecture(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val transcript: String,
    val summary: String,
    val audioDurationSec: Int = 0,
    val keywords: String = "",
    val masteredKeywords: String = "",
    val isSummaryCompleted: Boolean = false,
    val summaryShort: String = "",
    val summaryMedium: String = "",
    val summaryDetailed: String = ""
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = Lecture::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lectureId"])]
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lectureId: Int,
    val front: String,
    val back: String,
    val isMastered: Boolean = false
)

@Entity(
    tableName = "study_slots",
    foreignKeys = [
        ForeignKey(
            entity = Lecture::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lectureId"])]
)
data class StudySlot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lectureId: Int,
    val lectureTitle: String,
    val dayOfWeek: Int, // 1 to 7 (Mon to Sun)
    val timeLabel: String, // e.g. "10:30 AM"
    val isCompleted: Boolean = false
)
