package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.LectureRepository
import com.example.ui.LectureSummarizerApp
import com.example.ui.LectureViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable premium full edge-to-edge transparent drawing
        enableEdgeToEdge()

        // Setup local database & repository layers
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = LectureRepository(
            subjectDao = database.subjectDao(),
            lectureDao = database.lectureDao(),
            flashcardDao = database.flashcardDao(),
            studySlotDao = database.studySlotDao()
        )

        // Initialize state view-model with factory
        val factory = LectureViewModel.Factory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[LectureViewModel::class.java]

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.theme.PolishBackground
                ) {
                    LectureSummarizerApp(viewModel = viewModel)
                }
            }
        }
    }
}
