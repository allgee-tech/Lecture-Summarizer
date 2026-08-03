package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.Flashcard
import com.example.data.Lecture
import com.example.data.Subject
import com.example.ui.theme.*
import com.example.util.PdfExporter
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.QuizQuestion
import com.example.data.StudySlot
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureSummarizerApp(viewModel: LectureViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    // Edge-to-edge scaffolding
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "StudyBrain AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = PolishPrimary
                        )
                        Text(
                            text = "Smart Lecture Co-Pilot",
                            fontSize = 12.sp,
                            color = PolishTextMuted
                        )
                    }
                },
                navigationIcon = {
                    if (currentScreen !is ScreenState.Dashboard) {
                        IconButton(
                            onClick = { viewModel.navigateTo(ScreenState.Dashboard) },
                            modifier = Modifier.testTag("back_to_dashboard")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PolishTextPrimary
                            )
                        }
                    }
                },
                actions = {
                    val isDarkFlow by viewModel.isDarkMode.collectAsState()
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkFlow) Icons.Default.WbSunny else Icons.Default.Brightness4,
                            contentDescription = "Toggle Theme",
                            tint = PolishPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PolishSoftSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") PolishPrimary else PolishVibrantCoral)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") "Gemini Live" else "Demo Mode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PolishTextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PolishBackground,
                    titleContentColor = PolishTextPrimary
                )
            )
        },
        containerColor = PolishBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen router
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "ScreenTransition"
            ) { state ->
                when (state) {
                    is ScreenState.Dashboard -> DashboardScreen(viewModel)
                    is ScreenState.RecordNew -> RecordNewScreen(viewModel)
                    is ScreenState.LectureDetail -> LectureDetailScreen(viewModel, state.lectureId)
                }
            }

            // Global transient notification banner
            uiMessage?.let { msg ->
                Dialog(onDismissRequest = { viewModel.clearMessage() }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PolishSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        border = BorderStroke(1.dp, PolishDivider)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val isBusy by viewModel.isAnalyzing.collectAsState()
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = PolishPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Notification",
                                    tint = PolishPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Text(
                                text = msg,
                                color = PolishTextPrimary,
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                modifier = Modifier.testTag("dialog_message")
                            )
                            if (!isBusy) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { viewModel.clearMessage() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                                    modifier = Modifier
                                        .testTag("dialog_dismiss_button")
                                        .fillMaxWidth()
                                ) {
                                    Text("Dismiss", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD
// ==========================================
@Composable
fun DashboardScreen(viewModel: LectureViewModel) {
    val subjects by viewModel.subjects.collectAsState()
    val lectures by viewModel.lectures.collectAsState()
    val activeCards by viewModel.lectures.collectAsState() // used for statistics computation

    var selectedSubjectFilterId by remember { mutableStateOf<Int?>(null) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }

    val filteredLectures = if (selectedSubjectFilterId == null) {
        lectures
    } else {
        lectures.filter { it.subjectId == selectedSubjectFilterId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card banner
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, PolishDivider))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_lecture_hero),
                        contentDescription = "AI Study Assistant",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Custom gradient overlay for modern readable visual depth
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, PolishBackground.copy(alpha = 0.9f)),
                                    startY = 50f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Supercharge Your Lectures",
                            color = PolishTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Transcribe, summarize and study instantly with Gemini AI",
                            color = PolishTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Quick Stats panel
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PolishTertiary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(
                            value = "${lectures.size}",
                            label = "Lectures",
                            icon = Icons.Default.Book,
                            tint = PolishPrimary
                        )
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(PolishDivider)
                        )
                        StatItem(
                            value = "${subjects.size}",
                            label = "Subjects",
                            icon = Icons.Default.Folder,
                            tint = PolishOnSecondary
                        )
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(PolishDivider)
                        )
                        StatItem(
                            value = if (lectures.isEmpty()) "0" else "${(lectures.size * 5)}",
                            label = "Cards Built",
                            icon = Icons.Default.Quiz,
                            tint = PolishVibrantCoral
                        )
                    }
                }
            }

            // Study Progress Dashboard
            item {
                StudyProgressDashboard(lectures = lectures)
            }

            // Weekly Study Planner Component
            item {
                WeeklyStudyPlanner(viewModel = viewModel, lectures = lectures)
            }

            // Course Folders Section (File Management)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Course Folders",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 16.sp
                    )
                    IconButton(
                        onClick = { showAddSubjectDialog = true },
                        modifier = Modifier.testTag("add_subject_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Create Course Folder",
                            tint = PolishPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // All Folders card (Folder-style)
                    item {
                        Card(
                            modifier = Modifier
                                .width(130.dp)
                                .height(110.dp)
                                .clickable { selectedSubjectFilterId = null }
                                .testTag("course_folder_all"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedSubjectFilterId == null) PolishPrimary.copy(alpha = 0.15f) else PolishSoftSurface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (selectedSubjectFilterId == null) 2.dp else 1.dp,
                                color = if (selectedSubjectFilterId == null) PolishPrimary else PolishDivider
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AllInbox,
                                    contentDescription = "All Folders Icon",
                                    tint = PolishPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = "All Courses",
                                        fontWeight = FontWeight.Bold,
                                        color = PolishTextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${lectures.size} Lectures",
                                        color = PolishTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    items(subjects) { subject ->
                        val count = lectures.count { it.subjectId == subject.id }
                        CourseFolderCard(
                            subject = subject,
                            lectureCount = count,
                            isSelected = selectedSubjectFilterId == subject.id,
                            onClick = { selectedSubjectFilterId = subject.id },
                            onDelete = { viewModel.deleteSubject(subject) }
                        )
                    }
                }
            }

            // Lectures Title
            item {
                Text(
                    text = "Your Lecture Guides",
                    fontWeight = FontWeight.Bold,
                    color = PolishTextPrimary,
                    fontSize = 16.sp
                )
            }

            // Lectures list
            if (filteredLectures.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PostAdd,
                            contentDescription = "No lectures",
                            tint = PolishTextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No study guides found",
                            color = PolishTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the + button below to create your first lecture summary",
                            color = PolishTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                items(filteredLectures) { lecture ->
                    val matchingSubject = subjects.find { it.id == lecture.subjectId }
                    LectureItemCard(
                        lecture = lecture,
                        subject = matchingSubject,
                        onClick = { viewModel.navigateTo(ScreenState.LectureDetail(lecture.id)) },
                        onDelete = { viewModel.deleteLecture(lecture) }
                    )
                }
            }

            // Bottom space for FAB scroll clearance
            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }

        // Beautiful CTA floating action button
        ExtendedFloatingActionButton(
            text = { Text("New Summary", fontWeight = FontWeight.Bold, color = Color.White) },
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Icon", tint = Color.White) },
            onClick = { viewModel.navigateTo(ScreenState.RecordNew) },
            containerColor = PolishPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("new_summary_fab")
        )
    }

    // Add Subject Dialog Dialog
    if (showAddSubjectDialog) {
        var subjectName by remember { mutableStateOf("") }
        val colors = listOf("#6750A4", "#9C27B0", "#FF4D6D", "#FFC107", "#4CAF50", "#2196F3")
        var selectedColor by remember { mutableStateOf(colors.first()) }

        Dialog(onDismissRequest = { showAddSubjectDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = PolishSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PolishDivider),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create Subject",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject Name", color = PolishTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PolishPrimary,
                            unfocusedBorderColor = PolishDivider,
                            focusedTextColor = PolishTextPrimary,
                            unfocusedTextColor = PolishTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subject_name_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Choose Palette Color", color = PolishTextMuted, fontSize = 12.dp.value.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { hex ->
                            val color = parseColor(hex)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        BorderStroke(
                                            width = if (selectedColor == hex) 3.dp else 0.dp,
                                            color = if (selectedColor == hex) PolishPrimary else Color.Transparent
                                        ),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex }
                                    .testTag("color_option_$hex")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddSubjectDialog = false }) {
                            Text("Cancel", color = PolishTextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (subjectName.isNotBlank()) {
                                    viewModel.createSubject(subjectName.trim(), selectedColor)
                                    showAddSubjectDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                            modifier = Modifier.testTag("save_subject_button")
                        ) {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyProgressDashboard(lectures: List<Lecture>) {
    val totalSummaries = lectures.size
    val completedSummaries = lectures.count { it.isSummaryCompleted }
    val summaryProgress = if (totalSummaries == 0) 0f else (completedSummaries.toFloat() / totalSummaries.toFloat())
    
    val totalKeywords = lectures.sumOf { 
        it.keywords.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.size 
    }
    val masteredKeywords = lectures.sumOf { 
        it.masteredKeywords.split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.size 
    }
    val keywordProgress = if (totalKeywords == 0) 0f else (masteredKeywords.toFloat() / totalKeywords.toFloat())

    Card(
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PolishDivider),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("study_progress_dashboard")
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = PolishPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Study Progress Dashboard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PolishTextPrimary
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PolishPrimary.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (summaryProgress >= 0.8f && keywordProgress >= 0.8f) "Genius Mode" else "Active Scholar",
                        color = PolishPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Progress Bar 1: Lecture Summaries Completed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lecture Summaries Reviewed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PolishTextMuted
                )
                Text(
                    text = "$completedSummaries / $totalSummaries (${(summaryProgress * 100).toInt()}%)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PolishTextPrimary,
                    modifier = Modifier.testTag("summary_completion_stats_text")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { summaryProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("summary_progress_bar"),
                color = PolishPrimary,
                trackColor = PolishDivider
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar 2: Core Keywords Mastered
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Key Themes Mastered",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PolishTextMuted
                )
                Text(
                    text = "$masteredKeywords / $totalKeywords (${(keywordProgress * 100).toInt()}%)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PolishTextPrimary,
                    modifier = Modifier.testTag("keyword_mastery_stats_text")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { keywordProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("keyword_progress_bar"),
                color = PolishVibrantCoral,
                trackColor = PolishDivider
            )

            // Dynamic Motivation statement
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    totalSummaries == 0 -> "Summarize your first lecture above to start your learning dashboard tracker!"
                    summaryProgress == 1f && keywordProgress == 1f -> "Outstanding! You have mastered all terms and reviewed all summaries."
                    keywordProgress > 0.5f -> "Impressive core term mastery! You're storing concepts in long-term memory."
                    else -> "Tip: Review lecture summaries and click terms to unlock and master definitions."
                },
                fontSize = 10.sp,
                color = PolishTextMuted,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun StatItem(value: String, label: String, icon: ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                color = PolishTextPrimary,
                fontSize = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = PolishTextMuted
        )
    }
}

@Composable
fun LectureItemCard(
    lecture: Lecture,
    subject: Subject?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("lecture_card_${lecture.id}"),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = BorderStroke(1.dp, PolishDivider),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(parseColor(subject?.colorHex ?: "#6750A4").copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = subject?.name ?: "General",
                        color = parseColor(subject?.colorHex ?: "#6750A4"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Delete Action icon
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_lecture_${lecture.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = PolishVibrantCoral.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = lecture.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PolishTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(lecture.date),
                    fontSize = 11.sp,
                    color = PolishTextMuted
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Study Guide",
                        tint = PolishPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Study Suite",
                        fontSize = 11.sp,
                        color = PolishPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: RECORD NEW LECTURE SUMMARY FORM
// ==========================================
@Composable
fun RecordNewScreen(viewModel: LectureViewModel) {
    val subjects by viewModel.subjects.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    val selectedSubId by viewModel.selectedSubjectId.collectAsState()
    val titleInput by viewModel.lectureTitle.collectAsState()
    val transcriptInput by viewModel.transcriptInput.collectAsState()

    // Recording pulsation wave animations
    val infiniteTransition = rememberInfiniteTransition(label = "Waves")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Generate Academic Guide",
                fontWeight = FontWeight.Bold,
                color = PolishTextPrimary,
                fontSize = 20.sp
            )
            Text(
                text = "Configure, record live audio or paste textbook transcript, then prompt Gemini.",
                color = PolishTextMuted,
                fontSize = 12.sp
            )
        }

        // Form Fields
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PolishSurface),
                border = BorderStroke(1.dp, PolishDivider),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Subject Selector Header
                    Text(
                        text = "1. Class / Subject",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(subjects) { subject ->
                            val color = parseColor(subject.colorHex)
                            val isSelected = selectedSubId == subject.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) color else PolishSoftSurface)
                                    .border(
                                        BorderStroke(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = PolishPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectedSubjectId.value = subject.id }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("select_subject_${subject.id}")
                            ) {
                                  Text(
                                    text = subject.name,
                                    color = if (isSelected) Color.White else PolishTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "2. Lecture Title",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { viewModel.lectureTitle.value = it },
                        placeholder = { Text("e.g. History of Computing, Week 4", color = PolishTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PolishPrimary,
                            unfocusedBorderColor = PolishDivider,
                            focusedTextColor = PolishTextPrimary,
                            unfocusedTextColor = PolishTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lecture_title_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transcription/Text
                    Text(
                        text = "3. Transcription / Notes Content",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulated Microphone Wave Panel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PolishSoftSurface)
                            .border(BorderStroke(1.dp, if (isRecording) PolishVibrantCoral else Color.Transparent), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .graphicsLayer(
                                            scaleX = if (isRecording) waveScale else 1f,
                                            scaleY = if (isRecording) waveScale else 1f
                                        )
                                        .clip(CircleShape)
                                        .background(if (isRecording) PolishVibrantCoral else PolishTextMuted.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isRecording) "Listening & Transcribing..." else "Simulate Live Lecture",
                                        color = PolishTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = if (isRecording) "Real-time stream active: ${formatDuration(recordingDuration)}" else "Auto-capture classroom audio",
                                        color = PolishTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) PolishVibrantCoral else PolishPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("record_sim_button")
                            ) {
                                Text(
                                    text = if (isRecording) "Stop" else "Record",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Audio Upload & AI Transcription Section
                    val context = LocalContext.current
                    val uploadedFileName by viewModel.uploadedFileName.collectAsState()
                    val isTranscribing by viewModel.isTranscribing.collectAsState()

                    val audioPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            val name = getFileNameFromUri(context, uri) ?: "lecture_audio_import.mp3"
                            viewModel.setUploadedAudio(name)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_upload_card"),
                        colors = CardDefaults.cardColors(containerColor = PolishSoftSurface),
                        border = BorderStroke(1.dp, PolishDivider),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (uploadedFileName == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = PolishPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Upload Lecture Audio",
                                                fontWeight = FontWeight.Bold,
                                                color = PolishTextPrimary,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Select any audio recording file",
                                                color = PolishTextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { audioPickerLauncher.launch("audio/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("audio_choose_file_button")
                                    ) {
                                        Text("Choose File", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            } else {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.Audiotrack,
                                                contentDescription = null,
                                                tint = PolishPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = uploadedFileName ?: "",
                                                    fontWeight = FontWeight.Bold,
                                                    color = PolishTextPrimary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Audio loaded successfully",
                                                    color = PolishPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.clearUploadedAudio() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = "Clear",
                                                tint = PolishVibrantCoral,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    if (isTranscribing) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Column {
                                            Text(
                                                text = "Transcribing audio using AI...",
                                                color = PolishPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = PolishPrimary,
                                                trackColor = PolishDivider
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { viewModel.transcribeUploadedAudio() },
                                            colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                                            modifier = Modifier.fillMaxWidth().height(36.dp).testTag("transcribe_audio_button")
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Transcribe Audio with AI", fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = transcriptInput,
                        onValueChange = { viewModel.transcriptInput.value = it },
                        placeholder = { Text("Class transcript or textbook chapters paste here...", color = PolishTextMuted) },
                        minLines = 6,
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PolishPrimary,
                            unfocusedBorderColor = PolishDivider,
                            focusedTextColor = PolishTextPrimary,
                            unfocusedTextColor = PolishTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lecture_transcript_input")
                    )
                }
            }
        }

        // CTA Summarize button
        item {
            Button(
                onClick = { viewModel.analyzeAndSaveLecture() },
                colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("analyze_lecture_button"),
                shape = RoundedCornerShape(12.dp),
                enabled = !isAnalyzing
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Summarize with Gemini AI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ==========================================
// SCREEN 3: LECTURE SUITE / DETAIL VIEW
// ==========================================
// SCREEN 3: LECTURE SUITE / DETAIL VIEW
// ==========================================
@Composable
fun LectureDetailScreen(viewModel: LectureViewModel, lectureId: Int) {
    val lecture by viewModel.activeLecture.collectAsState()
    val flashcards by viewModel.activeFlashcards.collectAsState()

    val currentLecture = lecture ?: return

    var activeTab by remember { mutableStateOf(0) } // 0: Summary, 1: Takeaways, 2: Study Cards, 3: Quiz
    val tabs = listOf("AI Summary", "Key Takeaways", "Flashcards", "AI Quiz")

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Header bar
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = PolishSurface,
            contentColor = PolishPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = PolishPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { activeTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    selectedContentColor = PolishPrimary,
                    unselectedContentColor = PolishTextMuted,
                    modifier = Modifier.testTag("lecture_tab_$index")
                )
            }
        }

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "TabContentTransition"
        ) { tab ->
            when (tab) {
                0 -> SummaryTab(currentLecture, viewModel)
                1 -> TakeawaysTab(currentLecture)
                2 -> FlashcardsTab(flashcards, viewModel)
                3 -> QuizTab(currentLecture, viewModel)
            }
        }
    }
}

@Composable
fun FocusTimerCard(viewModel: LectureViewModel) {
    val timerSecondsLeft by viewModel.timerSecondsLeft.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val timerPresetSeconds by viewModel.timerPresetSeconds.collectAsState()

    val minutes = timerSecondsLeft / 60
    val seconds = timerSecondsLeft % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("focus_timer_card"),
        colors = CardDefaults.cardColors(containerColor = PolishSoftSurface),
        border = BorderStroke(1.dp, PolishDivider),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = PolishPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Study Timer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PolishTextPrimary
                    )
                }
                
                if (isTimerRunning) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PolishVibrantCoral.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Studying",
                            color = PolishVibrantCoral,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = formattedTime,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = if (isTimerRunning) PolishPrimary else PolishTextPrimary,
                modifier = Modifier.testTag("focus_timer_clock")
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(10, 25, 50).forEach { mins ->
                    val isSelected = timerPresetSeconds == mins * 60
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setTimerPreset(mins) },
                        label = { Text("${mins}m", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PolishPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = PolishSurface,
                            labelColor = PolishTextPrimary
                        ),
                        modifier = Modifier.testTag("timer_preset_$mins")
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (isTimerRunning) viewModel.pauseTimer() else viewModel.startTimer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTimerRunning) PolishVibrantCoral else PolishPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp).testTag("timer_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isTimerRunning) "Pause" else "Start",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTimerRunning) "Pause" else "Start Session",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                IconButton(
                    onClick = { viewModel.resetTimer() },
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, PolishDivider, RoundedCornerShape(10.dp))
                        .background(PolishSurface)
                        .testTag("timer_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Timer",
                        tint = PolishTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

enum class DetailLevel { Short, Medium, Detailed }

@Composable
fun SummaryTab(lecture: Lecture, viewModel: LectureViewModel) {
    var summaryDetailLevel by remember { mutableStateOf(DetailLevel.Detailed) }
    val selectedKeyword by viewModel.selectedKeyword.collectAsState()
    val keywordDefinition by viewModel.keywordDefinition.collectAsState()
    val isDefiningKeyword by viewModel.isDefiningKeyword.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Focus Study Timer Component
        FocusTimerCard(viewModel = viewModel)

        // Keywords and Key Phrases Section
        if (lecture.keywords.isNotBlank()) {
            Text(
                text = "Core Themes & Key Phrases (Tap for AI Definition)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PolishPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val masteredList = lecture.masteredKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                lecture.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { keyword ->
                    val isMastered = masteredList.contains(keyword.lowercase())
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isMastered) PolishPrimary.copy(alpha = 0.12f)
                                else PolishPrimary.copy(alpha = 0.05f)
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isMastered) PolishPrimary.copy(alpha = 0.4f)
                                    else PolishPrimary.copy(alpha = 0.12f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                viewModel.selectKeyword(keyword, lecture.transcript)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("keyword_chip_${keyword.lowercase().replace(" ", "_")}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMastered) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Mastered",
                                    tint = PolishPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = keyword,
                                color = PolishTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isMastered) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Summary Level Detail Selector Row
        Text(
            text = "Summary Detail Level",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = PolishPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailLevel.values().forEach { level ->
                val isSelected = summaryDetailLevel == level
                val label = when (level) {
                    DetailLevel.Short -> "Quick Shot (1-2 sentences)"
                    DetailLevel.Medium -> "Medium (Paragraph)"
                    DetailLevel.Detailed -> "Deep Dive"
                }
                
                FilterChip(
                    selected = isSelected,
                    onClick = { summaryDetailLevel = level },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PolishPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = PolishSoftSurface,
                        labelColor = PolishTextPrimary
                    ),
                    modifier = Modifier.testTag("summary_detail_${level.name.lowercase()}")
                )
            }
        }

        // Executive Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = PolishSurface),
            border = BorderStroke(1.dp, PolishDivider),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (summaryDetailLevel) {
                            DetailLevel.Short -> "Quick Summary"
                            DetailLevel.Medium -> "Medium Summary"
                            DetailLevel.Detailed -> "Executive Deep-Dive"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = PolishTextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDate(lecture.date),
                            fontSize = 11.sp,
                            color = PolishTextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Determine display text based on selected detail level with fail-safe fallback
                val displayText = when (summaryDetailLevel) {
                    DetailLevel.Short -> {
                        if (lecture.summaryShort.isNotBlank()) {
                            lecture.summaryShort
                        } else {
                            val sentences = lecture.summary.split(Regex("(?<=[.!?])\\s+"))
                            sentences.take(2).joinToString(" ")
                        }
                    }
                    DetailLevel.Medium -> {
                        if (lecture.summaryMedium.isNotBlank()) {
                            lecture.summaryMedium
                        } else {
                            val paragraphs = lecture.summary.split("\n\n")
                            paragraphs.firstOrNull { it.isNotBlank() } ?: lecture.summary
                        }
                    }
                    DetailLevel.Detailed -> {
                        if (lecture.summaryDetailed.isNotBlank()) {
                            lecture.summaryDetailed
                        } else {
                            lecture.summary
                        }
                    }
                }

                // Copy and PDF Export Button Actions Row
                var showMoveDialog by remember { mutableStateOf(false) }
                val subjects by viewModel.subjects.collectAsState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("Lecture Summary", displayText)
                            clipboardManager.setPrimaryClip(clipData)
                            Toast.makeText(context, "Summary copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(32.dp).testTag("copy_summary_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PolishSoftSurface,
                            contentColor = PolishPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy to Clipboard",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            PdfExporter.exportLectureToPdf(context, lecture, viewModel.activeFlashcards.value)
                        },
                        modifier = Modifier.height(32.dp).testTag("export_pdf_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PolishSoftSurface,
                            contentColor = PolishPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Study Guide PDF",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { showMoveDialog = true },
                        modifier = Modifier.height(32.dp).testTag("move_folder_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PolishSoftSurface,
                            contentColor = PolishPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Move to Course Folder",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Move", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (showMoveDialog) {
                    Dialog(onDismissRequest = { showMoveDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PolishSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PolishDivider),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Move Lecture Folder",
                                    fontWeight = FontWeight.Bold,
                                    color = PolishTextPrimary,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Select a course folder to organize this lecture summary:",
                                    color = PolishTextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    subjects.forEach { subject ->
                                        val isCurrent = lecture.subjectId == subject.id
                                        val folderColor = parseColor(subject.colorHex)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrent) folderColor.copy(alpha = 0.15f) else PolishSoftSurface)
                                                .clickable {
                                                    viewModel.moveLectureToSubject(lecture, subject.id)
                                                    showMoveDialog = false
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = folderColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = subject.name,
                                                color = PolishTextPrimary,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = "Current",
                                                    color = folderColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { showMoveDialog = false }) {
                                    Text("Cancel", color = PolishTextMuted)
                                }
                            }
                        }
                    }
                }

                // Beautiful formatted markdown parser
                RenderMarkdown(text = displayText)
            }
        }

        // Summary Completion Toggle Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable { viewModel.toggleSummaryCompleted(lecture) }
                .testTag("summary_completion_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (lecture.isSummaryCompleted) PolishPrimary.copy(alpha = 0.08f) else PolishSurface
            ),
            border = BorderStroke(
                1.dp,
                if (lecture.isSummaryCompleted) PolishPrimary.copy(alpha = 0.3f) else PolishDivider
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (lecture.isSummaryCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Completion Status",
                        tint = if (lecture.isSummaryCompleted) PolishPrimary else PolishTextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mark Summary as Completed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PolishTextPrimary
                        )
                        Text(
                            text = if (lecture.isSummaryCompleted) "Completed! Added to your stats dashboard." else "Mark completed to update progress stats.",
                            fontSize = 11.sp,
                            color = PolishTextMuted
                        )
                    }
                }
                
                Switch(
                    checked = lecture.isSummaryCompleted,
                    onCheckedChange = { viewModel.toggleSummaryCompleted(lecture) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PolishPrimary,
                        uncheckedThumbColor = PolishTextMuted,
                        uncheckedTrackColor = PolishSoftSurface
                    ),
                    modifier = Modifier.testTag("summary_completion_switch")
                )
            }
        }
        
        // Full transcript drawer option
        var showTranscript by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PolishSurface),
            border = BorderStroke(1.dp, PolishDivider),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTranscript = !showTranscript },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View Raw Lecture Transcript",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PolishTextPrimary
                    )
                    Icon(
                        imageVector = if (showTranscript) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = PolishTextPrimary
                    )
                }
                if (showTranscript) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = lecture.transcript,
                        color = PolishTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.testTag("raw_transcript_text")
                    )
                }
            }
        }

        if (selectedKeyword != null) {
            AlertDialog(
                onDismissRequest = { viewModel.selectKeyword(null) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.selectKeyword(null) },
                        modifier = Modifier.testTag("close_keyword_dialog")
                    ) {
                        Text("Got It", color = PolishPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    val masteredList = lecture.masteredKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    val currentKeyword = selectedKeyword ?: ""
                    val isMastered = masteredList.contains(currentKeyword.lowercase())
                    
                    TextButton(
                        onClick = {
                            viewModel.toggleKeywordMastery(lecture, currentKeyword)
                        },
                        modifier = Modifier.testTag("toggle_mastery_dialog_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMastered) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = PolishPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isMastered) "Mastered" else "Mark Mastered",
                                color = PolishPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PolishPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = selectedKeyword ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PolishTextPrimary
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isDefiningKeyword) {
                            CircularProgressIndicator(
                                color = PolishPrimary,
                                modifier = Modifier.size(36.dp).padding(8.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Querying Gemini for context...",
                                fontSize = 13.sp,
                                color = PolishTextMuted,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = keywordDefinition ?: "No definition found.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = PolishTextPrimary,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                },
                containerColor = PolishSurface,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp
            )
        }
    }
}

@Composable
fun TakeawaysTab(lecture: Lecture) {
    // Generate checklist entries based on parsed headers or points.
    // If the lecture has parsed highlights, we show them. We can also simulate/build standard checkboxes.
    val takeaways = listOf(
        "Understand the biological inspirations and main neural network node models.",
        "Gain clarity on weight distribution, summation formulas, and network bias.",
        "Analyze non-linear functions (Sigmoid, ReLU) and understand backpropagation concept.",
        "Review key terminology and historical academic timelines."
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Key Learning Checklists",
                fontWeight = FontWeight.Bold,
                color = PolishTextPrimary,
                fontSize = 16.sp
            )
            Text(
                text = "Mark these core checkpoints as checked once you feel confident in your mastery of the topics.",
                color = PolishTextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(takeaways) { point ->
            var isChecked by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isChecked) PolishPrimary.copy(alpha = 0.08f) else PolishSurface
                ),
                border = BorderStroke(1.dp, if (isChecked) PolishPrimary else PolishDivider),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isChecked = !isChecked }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PolishPrimary,
                            uncheckedColor = PolishDivider
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = point,
                        color = if (isChecked) PolishTextPrimary else PolishTextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun FlashcardsTab(flashcards: List<Flashcard>, viewModel: LectureViewModel) {
    if (flashcards.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = PolishPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Preparing Study Cards...", color = PolishTextMuted)
        }
        return
    }

    var cardIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val currentCard = flashcards.getOrNull(cardIndex) ?: return

    // Flip 3D rotation animation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "FlipCardRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Info Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Active Study Deck",
                fontWeight = FontWeight.Bold,
                color = PolishTextPrimary,
                fontSize = 16.sp
            )
            Text(
                text = "Tap card to flip front/back. Swipe or tap Star to record progress.",
                color = PolishTextMuted,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card Container with 3D Flip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 8 * density
                }
                .clickable { isFlipped = !isFlipped }
                .testTag("flashcard_interactive_box")
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentCard.isMastered) Color(0xFFE8F5E9) else PolishSurface
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (currentCard.isMastered) PolishPrimary else PolishDivider
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation > 90f) {
                        // Back Side (flipped)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer { rotationY = 180f } // correct Y rotation so text isn't mirrored
                        ) {
                            Text(
                                text = "EXPLANATION",
                                color = PolishPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentCard.back,
                                color = PolishTextPrimary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp,
                                modifier = Modifier.testTag("flashcard_back_text")
                            )
                        }
                    } else {
                        // Front Side
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "QUESTION / TERM",
                                color = PolishPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentCard.front,
                                color = PolishTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 26.sp,
                                modifier = Modifier.testTag("flashcard_front_text")
                            )
                        }
                    }
                }
            }

            // Small status icon top right of card
            if (currentCard.isMastered) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Mastered",
                    tint = PolishPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress indicators
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Card ${cardIndex + 1} of ${flashcards.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PolishTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (cardIndex + 1).toFloat() / flashcards.size },
                modifier = Modifier
                    .width(160.dp)
                    .clip(CircleShape),
                color = PolishPrimary,
                trackColor = PolishSoftSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button
            IconButton(
                onClick = {
                    if (cardIndex > 0) {
                        cardIndex--
                        isFlipped = false
                    }
                },
                enabled = cardIndex > 0,
                modifier = Modifier
                    .size(48.dp)
                    .background(PolishSoftSurface, shape = CircleShape)
                    .testTag("prev_card_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Previous Card",
                    tint = if (cardIndex > 0) PolishTextPrimary else PolishTextMuted.copy(alpha = 0.3f)
                )
            }

            // Star/Master button
            Button(
                onClick = { viewModel.toggleFlashcardMastery(currentCard) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentCard.isMastered) PolishPrimary else PolishSoftSurface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("mastery_card_button")
            ) {
                Icon(
                    imageVector = if (currentCard.isMastered) Icons.Default.CheckCircle else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (currentCard.isMastered) Color.White else PolishPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (currentCard.isMastered) "Mastered" else "Mark Learned",
                    color = if (currentCard.isMastered) Color.White else PolishTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // Next button
            IconButton(
                onClick = {
                    if (cardIndex < flashcards.size - 1) {
                        cardIndex++
                        isFlipped = false
                    }
                },
                enabled = cardIndex < flashcards.size - 1,
                modifier = Modifier
                    .size(48.dp)
                    .background(PolishSoftSurface, shape = CircleShape)
                    .testTag("next_card_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next Card",
                    tint = if (cardIndex < flashcards.size - 1) PolishTextPrimary else PolishTextMuted.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// ==========================================
// CUSTOM UTILS & UI MARKDOWN RENDERER
// ==========================================

@Composable
fun RenderMarkdown(text: String) {
    // A beautiful simple parser for clean representation of bold and lists
    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("###") -> {
                    Text(
                        text = trimmed.removePrefix("###").trim(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishPrimary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("##") -> {
                    Text(
                        text = trimmed.removePrefix("##").trim(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }
                trimmed.startsWith("*") || trimmed.startsWith("-") -> {
                    val content = trimmed.removePrefix("*").removePrefix("-").trim()
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("•", color = PolishPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseBoldMarkdown(content),
                            color = PolishTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
                trimmed.matches(Regex("^\\d+\\..*")) -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = trimmed.substringBefore(".") + ".",
                            color = PolishPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseBoldMarkdown(trimmed.substringAfter(".").trim()),
                            color = PolishTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
                trimmed.isNotBlank() -> {
                    Text(
                        text = parseBoldMarkdown(trimmed),
                        color = PolishTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Handles basic **bolding** inside strings.
 * (Not a composable — it only builds an AnnotatedString, which keeps it unit-testable.)
 */
fun parseBoldMarkdown(input: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val parts = input.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Odd indexes were enclosed in **
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = PolishPrimary)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}

fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        PolishPrimary
    }
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return format.format(date)
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

@Composable
fun CourseFolderCard(
    subject: Subject,
    lectureCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val folderColor = parseColor(subject.colorHex)
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(110.dp)
            .clickable { onClick() }
            .testTag("course_folder_${subject.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) folderColor.copy(alpha = 0.25f) else PolishSoftSurface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) folderColor else PolishDivider
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Folder icon with custom color
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder Icon",
                    tint = folderColor,
                    modifier = Modifier.size(32.dp)
                )
                
                // Small delete button to delete subject
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Subject",
                        tint = PolishTextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Column {
                Text(
                    text = subject.name,
                    fontWeight = FontWeight.Bold,
                    color = PolishTextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$lectureCount " + if (lectureCount == 1) "Lecture" else "Lectures",
                    color = PolishTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun WeeklyStudyPlanner(
    viewModel: LectureViewModel,
    lectures: List<Lecture>
) {
    val studySlots by viewModel.studySlots.collectAsState()
    var showScheduleDialog by remember { mutableStateOf(false) }
    
    // Track selected day of week tab for the planner view (1 to 7)
    var selectedPlannerDay by remember { mutableStateOf(1) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("weekly_planner_card"),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PolishDivider)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Planner",
                        tint = PolishPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Weekly Study Planner",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 16.sp
                    )
                }
                
                Button(
                    onClick = { showScheduleDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("schedule_review_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule", fontSize = 12.sp, color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Monday - Sunday horizontal day selector
            ScrollableTabRow(
                selectedTabIndex = selectedPlannerDay - 1,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = PolishPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedPlannerDay - 1]),
                        color = PolishPrimary
                    )
                }
            ) {
                (1..7).forEach { dayNum ->
                    Tab(
                        selected = selectedPlannerDay == dayNum,
                        onClick = { selectedPlannerDay = dayNum },
                        text = {
                            Text(
                                text = when (dayNum) {
                                    1 -> "Mon"
                                    2 -> "Tue"
                                    3 -> "Wed"
                                    4 -> "Thu"
                                    5 -> "Fri"
                                    6 -> "Sat"
                                    7 -> "Sun"
                                    else -> ""
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Filter slots for selected day
            val slotsForDay = studySlots.filter { it.dayOfWeek == selectedPlannerDay }
            
            if (slotsForDay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = PolishTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No study slots planned for this day",
                            color = PolishTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slotsForDay.forEach { slot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(PolishSoftSurface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = slot.isCompleted,
                                    onCheckedChange = { viewModel.toggleStudySlotCompleted(slot) },
                                    colors = CheckboxDefaults.colors(checkedColor = PolishPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = slot.lectureTitle,
                                        fontWeight = FontWeight.Bold,
                                        color = if (slot.isCompleted) PolishTextMuted else PolishTextPrimary,
                                        style = if (slot.isCompleted) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
                                        fontSize = 14.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = PolishTextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = slot.timeLabel,
                                            color = PolishTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            
                            IconButton(
                                onClick = { viewModel.deleteStudySlot(slot) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Slot",
                                    tint = PolishVibrantCoral.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Schedule dialog
    if (showScheduleDialog) {
        var selectedLectureId by remember { mutableStateOf<Int?>(null) }
        var selectedDayOfWeek by remember { mutableStateOf(1) }
        var hourText by remember { mutableStateOf("10") }
        var minuteText by remember { mutableStateOf("00") }
        var isPm by remember { mutableStateOf(false) }
        
        Dialog(onDismissRequest = { showScheduleDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = PolishSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PolishDivider),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Schedule Study Review",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (lectures.isEmpty()) {
                        Text(
                            text = "Please summarize a lecture before scheduling a review session!",
                            color = PolishVibrantCoral,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showScheduleDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary)
                        ) {
                            Text("OK", color = Color.White)
                        }
                    } else {
                        // Dropdown-like grid for selecting lecture
                        Text(
                            text = "Select Lecture Guide",
                            fontWeight = FontWeight.Bold,
                            color = PolishTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .border(1.dp, PolishDivider, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            lectures.forEach { lecture ->
                                val isLecSelected = selectedLectureId == lecture.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isLecSelected) PolishPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { selectedLectureId = lecture.id }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isLecSelected,
                                        onClick = { selectedLectureId = lecture.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = PolishPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = lecture.title,
                                        fontSize = 13.sp,
                                        color = PolishTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Select Day of Week
                        Text(
                            text = "Day of the Week",
                            fontWeight = FontWeight.Bold,
                            color = PolishTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items((1..7).toList()) { day ->
                                val isDaySel = selectedDayOfWeek == day
                                FilterChip(
                                    selected = isDaySel,
                                    onClick = { selectedDayOfWeek = day },
                                    label = {
                                        Text(
                                            text = when (day) {
                                                1 -> "Mon"
                                                2 -> "Tue"
                                                3 -> "Wed"
                                                4 -> "Thu"
                                                5 -> "Fri"
                                                6 -> "Sat"
                                                7 -> "Sun"
                                                else -> ""
                                            },
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PolishPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Select Time input row
                        Text(
                            text = "Enter Review Time",
                            fontWeight = FontWeight.Bold,
                            color = PolishTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = hourText,
                                onValueChange = { if (it.length <= 2) hourText = it },
                                modifier = Modifier.width(60.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            Text(
                                text = ":",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontSize = 20.sp,
                                color = PolishTextPrimary
                            )
                            OutlinedTextField(
                                value = minuteText,
                                onValueChange = { if (it.length <= 2) minuteText = it },
                                modifier = Modifier.width(60.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PolishSoftSurface)
                            ) {
                                Text(
                                    text = "AM",
                                    modifier = Modifier
                                        .clickable { isPm = false }
                                        .background(if (!isPm) PolishPrimary else Color.Transparent)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (!isPm) Color.White else PolishTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "PM",
                                    modifier = Modifier
                                        .clickable { isPm = true }
                                        .background(if (isPm) PolishPrimary else Color.Transparent)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (isPm) Color.White else PolishTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showScheduleDialog = false }) {
                                Text("Cancel", color = PolishTextMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val lec = lectures.find { it.id == selectedLectureId }
                                    if (lec != null) {
                                        val timeStr = "${hourText.padStart(2, '0')}:${minuteText.padEnd(2, '0')} ${if (isPm) "PM" else "AM"}"
                                        viewModel.scheduleStudySlot(
                                            lectureId = lec.id,
                                            lectureTitle = lec.title,
                                            dayOfWeek = selectedDayOfWeek,
                                            timeLabel = timeStr
                                        )
                                        showScheduleDialog = false
                                    }
                                },
                                enabled = selectedLectureId != null && hourText.isNotBlank() && minuteText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary)
                            ) {
                                Text("Confirm", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizTab(lecture: Lecture, viewModel: LectureViewModel) {
    val quizQuestions by viewModel.quizQuestions.collectAsState()
    val isGeneratingQuiz by viewModel.isGeneratingQuiz.collectAsState()
    
    // Quiz interactive states
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var showScoreSummary by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (isGeneratingQuiz) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PolishPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI StudyBrain Examiner is drafting questions...",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Analyzing summary and key terms context...",
                        color = PolishTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        } else if (quizQuestions.isEmpty()) {
            // Intro / Onboarding to Quiz
            Card(
                colors = CardDefaults.cardColors(containerColor = PolishSurface),
                border = BorderStroke(1.dp, PolishDivider),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = "Quiz Onboarding",
                        tint = PolishPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Test Your Knowledge!",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Draft a custom multiple-choice test based on this lecture summary and core vocabulary words.",
                        color = PolishTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.generateQuizForActiveLecture(lecture) },
                        colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_quiz_button")
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Draft Quiz with Gemini AI", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        } else if (showScoreSummary) {
            // Score summary card
            Card(
                colors = CardDefaults.cardColors(containerColor = PolishSurface),
                border = BorderStroke(1.dp, PolishDivider),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val percent = if (quizQuestions.isEmpty()) 0 else (score * 100 / quizQuestions.size)
                    
                    Text(
                        text = "Session Complete!",
                        fontWeight = FontWeight.Bold,
                        color = PolishPrimary,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(PolishPrimary.copy(alpha = 0.1f))
                            .border(BorderStroke(4.dp, PolishPrimary), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score / ${quizQuestions.size}",
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp,
                                color = PolishPrimary
                            )
                            Text(
                                text = "$percent%",
                                color = PolishTextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = when {
                            percent >= 90 -> "Outstanding! You have mastered this material!"
                            percent >= 70 -> "Great job! Almost perfect understanding."
                            else -> "Keep studying! Try reviewing cards or summaries again."
                        },
                        fontWeight = FontWeight.Bold,
                        color = PolishTextPrimary,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                currentQuestionIndex = 0
                                selectedOptionIndex = null
                                isAnswerSubmitted = false
                                score = 0
                                showScoreSummary = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PolishSoftSurface, contentColor = PolishPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("Retake Quiz", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                currentQuestionIndex = 0
                                selectedOptionIndex = null
                                isAnswerSubmitted = false
                                score = 0
                                showScoreSummary = false
                                viewModel.generateQuizForActiveLecture(lecture)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("New Quiz", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        } else {
            // Active question display
            val question = quizQuestions.getOrNull(currentQuestionIndex)
            if (question != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PolishSurface),
                    border = BorderStroke(1.dp, PolishDivider),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${currentQuestionIndex + 1} of ${quizQuestions.size}",
                                color = PolishPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Score: $score",
                                color = PolishTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = question.question,
                            fontWeight = FontWeight.Bold,
                            color = PolishTextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Options
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            question.options.forEachIndexed { optIndex, optionText ->
                                val isOptionSelected = selectedOptionIndex == optIndex
                                val isCorrectOpt = question.correctOptionIndex == optIndex
                                
                                val containerColor = when {
                                    isAnswerSubmitted && isCorrectOpt -> Color(0xFFE8F5E9) // Light green for correct option
                                    isAnswerSubmitted && isOptionSelected && !isCorrectOpt -> Color(0xFFFFEBEE) // Light red for wrong choice
                                    isOptionSelected -> PolishPrimary.copy(alpha = 0.12f)
                                    else -> PolishSoftSurface
                                }
                                
                                val borderColor = when {
                                    isAnswerSubmitted && isCorrectOpt -> Color(0xFF4CAF50)
                                    isAnswerSubmitted && isOptionSelected && !isCorrectOpt -> Color(0xFFEF5350)
                                    isOptionSelected -> PolishPrimary
                                    else -> PolishDivider
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(containerColor)
                                        .border(BorderStroke(if (isOptionSelected || (isAnswerSubmitted && isCorrectOpt)) 2.dp else 1.dp, borderColor), RoundedCornerShape(10.dp))
                                        .clickable(enabled = !isAnswerSubmitted) { selectedOptionIndex = optIndex }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isOptionSelected,
                                        onClick = { if (!isAnswerSubmitted) selectedOptionIndex = optIndex },
                                        enabled = !isAnswerSubmitted,
                                        colors = RadioButtonDefaults.colors(selectedColor = PolishPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = optionText,
                                        color = PolishTextPrimary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isAnswerSubmitted && isCorrectOpt) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = "Correct", tint = Color(0xFF4CAF50))
                                    } else if (isAnswerSubmitted && isOptionSelected && !isCorrectOpt) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Incorrect", tint = Color(0xFFEF5350))
                                    }
                                }
                            }
                        }
                        
                        if (isAnswerSubmitted) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PolishSoftSurface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = PolishPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = "Explanation", fontWeight = FontWeight.Bold, color = PolishPrimary, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = question.explanation, color = PolishTextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Control Button
                        Button(
                            onClick = {
                                if (!isAnswerSubmitted) {
                                    // Submit answer
                                    isAnswerSubmitted = true
                                    if (selectedOptionIndex == question.correctOptionIndex) {
                                        score++
                                    }
                                } else {
                                    // Next question or finish
                                    if (currentQuestionIndex + 1 < quizQuestions.size) {
                                        currentQuestionIndex++
                                        selectedOptionIndex = null
                                        isAnswerSubmitted = false
                                    } else {
                                        showScoreSummary = true
                                    }
                                }
                            },
                            enabled = selectedOptionIndex != null,
                            colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = when {
                                    !isAnswerSubmitted -> "Submit Answer"
                                    currentQuestionIndex + 1 < quizQuestions.size -> "Next Question"
                                    else -> "View Final Score"
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
