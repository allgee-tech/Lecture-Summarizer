package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Log

// --- Gemini API Models ---

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

// --- Parsed Lecture Summary Target Model ---

@JsonClass(generateAdapter = true)
data class FlashcardJson(
    @Json(name = "front") val front: String,
    @Json(name = "back") val back: String
)

@JsonClass(generateAdapter = true)
data class QuizQuestion(
    @Json(name = "question") val question: String,
    @Json(name = "options") val options: List<String>,
    @Json(name = "correctOptionIndex") val correctOptionIndex: Int,
    @Json(name = "explanation") val explanation: String
)

@JsonClass(generateAdapter = true)
data class QuizResponse(
    @Json(name = "questions") val questions: List<QuizQuestion>
)

@JsonClass(generateAdapter = true)
data class LectureSummaryResult(
    @Json(name = "title") val title: String,
    @Json(name = "summary") val summary: String,
    @Json(name = "takeaways") val takeaways: List<String>,
    @Json(name = "flashcards") val flashcards: List<FlashcardJson>,
    @Json(name = "keywords") val keywords: List<String>? = null,
    @Json(name = "summaryShort") val summaryShort: String? = null,
    @Json(name = "summaryMedium") val summaryMedium: String? = null,
    @Json(name = "summaryDetailed") val summaryDetailed: String? = null
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Sends a lecture transcript to Gemini and requests a structured summary and flashcards.
     */
    suspend fun summarizeLecture(
        subjectName: String,
        lectureTitle: String,
        transcriptText: String
    ): LectureSummaryResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is missing or is placeholder!")
            return createDemoResult(subjectName, lectureTitle, transcriptText)
        }

        val prompt = """
            You are an expert academic summarizer for university students. Analyze the following lecture text for the subject '$subjectName' with proposed title '$lectureTitle'.
            
            Transcript text:
            ${"\"\"\""}
            $transcriptText
            ${"\"\"\""}
            
            Produce a highly professional, comprehensive, and clear study guide.
            Your output must be a single JSON object matching the following structure:
            {
              "title": "A highly accurate and concise main title for this specific lecture",
              "summary": "An executive summary written in elegant, clear Markdown. Use bold headers, bullet points for structure, clean formatting, and academic style. Highlight important theories, names, dates, or formulas.",
              "summaryShort": "A very short summary consisting of exactly 1-2 concise, high-impact sentences representing the core takeaway.",
              "summaryMedium": "A medium summary consisting of exactly one cohesive and well-structured paragraph (4-6 sentences) summarizing the key points.",
              "summaryDetailed": "A highly detailed summary formatted in clear Markdown with subheaders, multiple paragraphs, and structured bullet points highlighting technical details, equations, or deep concepts.",
              "keywords": [
                "keyword/phrase 1",
                "keyword/phrase 2",
                ... (provide between 5 and 10 highly relevant keywords or key phrases representing the main themes and concepts discussed)
              ],
              "takeaways": [
                "Key takeaway 1 (comprehensive sentence)",
                "Key takeaway 2 (comprehensive sentence)",
                "Key takeaway 3..."
              ],
              "flashcards": [
                {
                  "front": "A core term, formula, concept, or question",
                  "back": "The definition, explanation, or direct answer to the front"
                },
                ... (create 5-8 highly relevant flashcards based on the material)
              ]
            }
        """.trimIndent()

        val systemInstruction = """
            You are an academic summarization engine. You must output ONLY valid, parsable JSON matching the requested schema. Do not enclose the JSON in markdown code blocks or add any trailing text.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d(TAG, "Successfully received response: $jsonText")
                moshi.adapter(LectureSummaryResult::class.java).fromJson(jsonText)
            } else {
                Log.e(TAG, "Received empty response from Gemini.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ", e)
            null
        }
    }

    /**
     * Dynamically generates a brief definition or context snippet for a selected key term.
     */
    suspend fun defineKeyword(keyword: String, transcriptText: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Provide localized, smart fallback definitions for the key terms
            return when (keyword.lowercase().trim()) {
                "active recall" -> "A highly effective study technique where you stimulate your memory by retrieving information rather than passively reviewing it."
                "spaced repetition" -> "A learning methodology where material is reviewed at increasing intervals of time to optimize memory retention."
                "academic co-pilot" -> "An AI companion tailored to academic work, helping students summarize lectures, synthesize topics, and test knowledge."
                "retention metrics" -> "Quantitative measures of how much information a learner retains over a specified duration of time."
                "study flashcards" -> "Bite-sized cards featuring a question on the front and answer on the back, used to practice active retrieval."
                "information sifting" -> "The cognitive or automated process of filtering relevant, high-impact concepts from large volumes of unstructured data."
                else -> "This is a demo definition for '$keyword'. In a live environment with an active Gemini API key, this would be generated in real-time based specifically on your lecture's transcript content!"
            }
        }

        val prompt = """
            Provide a brief academic definition and contextual explanation (2-3 sentences, maximum 60 words) for the term or phrase: '$keyword'.
            
            Use the following context/transcript if relevant:
            ${"\"\"\""}
            $transcriptText
            ${"\"\"\""}
            
            Format the response nicely without prefixing it with the term itself. Make it direct, engaging, and professional.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.5
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Could not generate definition."
        } catch (e: Exception) {
            Log.e(TAG, "Error generating keyword definition: ", e)
            "Error generating definition: ${e.localizedMessage}"
        }
    }

    /**
     * Fallback demo summarizer in case the API key is not configured, to keep the app fully functional and delightful.
     */
    fun createDemoResult(subjectName: String, lectureTitle: String, transcriptText: String): LectureSummaryResult {
        return LectureSummaryResult(
            title = lectureTitle.ifBlank() { "Introduction to $subjectName" },
            summary = """
                ### Executive Summary — $subjectName
                
                This is a **high-fidelity summary** generated in **Demo Mode** because the Gemini API key was not configured. In a live environment, this would be generated in real-time by **Gemini 3.5 Flash**!
                
                *   **Lecture Core Focus**: $lectureTitle
                *   **Key Concept**: Synthesizing academic material into active study material.
                *   **Main Highlights**:
                    1.  *Active Recall*: Using flashcards to trigger memory retrieval.
                    2.  *Spaced Repetition*: Systematically reviewing cards over increasing intervals.
                    3.  *Information Compaction*: Condensing long lecture materials into structured summaries.
                
                ### Academic Analysis
                Students who use AI to condense and structure transcripts show a **35% increase** in retention metrics. Flashcard-based active recall reinforces synaptic connections and ensures long-term storage of formulas and core terminology.
            """.trimIndent(),
            summaryShort = "Learn to supercharge your study sessions with automated lecture summaries, active recall cards, and structured learning tracking.",
            summaryMedium = "This educational co-pilot utilizes state-of-the-art AI to transcribe, extract keywords, and outline university lecture materials. By creating dynamic flashcards and interactive progress checklists, it converts long, passive classroom audio into active study tools.",
            summaryDetailed = """
                ### Comprehensive Academic Analysis

                Our research indicates that transitioning from passive listening to active retrieval leads to a **35% increase** in long-term information retention. This guide outlines the key methodologies to achieve this:
                
                *   **Automated Information Sifting**: Extracting key phrases and ideas systematically decreases cognitive overload during study sessions.
                *   **Systematized Card Retrieval**: Constructing targeted flashcards forces the brain to perform active recall, strengthening synaptic pathways.
                *   **Subject-Based Cataloging**: Organizing resources logically makes it seamless to recall information across interdisciplinary domains.
            """.trimIndent(),
            keywords = listOf("Active Recall", "Spaced Repetition", "Academic Co-Pilot", "Retention Metrics", "Study flashcards", "Information Sifting"),
            takeaways = listOf(
                "Active recall using interactive study flashcards significantly enhances long-term retention of university material.",
                "Executive summaries provide a structured, high-level map of concepts for rapid pre-exam review.",
                "Proper categorization of lectures by subject helps organize cognitive loads during study cycles."
            ),
            flashcards = listOf(
                FlashcardJson(
                    front = "Active Recall",
                    back = "A learning technique where the student actively stimulates their memory during the learning process by retrieving information."
                ),
                FlashcardJson(
                    front = "Spaced Repetition",
                    back = "An learning method where reviews are spaced out over increasing intervals of time to exploit the psychological spacing effect."
                ),
                FlashcardJson(
                    front = "Gemini 3.5 Flash",
                    back = "Google's lightweight, high-speed multimodal model optimized for low latency text, reasoning, and summarization tasks."
                ),
                FlashcardJson(
                    front = "Moshi",
                    back = "A modern JSON library for Android that makes it easy to parse JSON into Kotlin data classes."
                ),
                FlashcardJson(
                    front = "Room Database",
                    back = "An Android Jetpack library that provides an abstraction layer over SQLite for fluent local database access."
                )
            )
        )
    }

    /**
     * Sends a lecture's summary and keywords to Gemini to generate multiple choice quiz questions.
     */
    suspend fun generateQuiz(
        subjectName: String,
        lectureTitle: String,
        summaryText: String,
        keywords: String
    ): List<QuizQuestion>? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is missing or is placeholder for Quiz Generation!")
            return createDemoQuiz(subjectName, lectureTitle, keywords)
        }

        val prompt = """
            You are an expert academic examiner. Based on the following study summary for '$lectureTitle' ($subjectName) and its core keywords, generate 4-5 high-quality, challenging multiple-choice questions (MCQs) to test student understanding.
            
            Keywords: $keywords
            
            Summary text:
            ${"\"\"\""}
            $summaryText
            ${"\"\"\""}
            
            Output must be a single JSON object containing a 'questions' list matching this structure:
            {
              "questions": [
                {
                  "question": "A clear, challenging question targeting a key concept",
                  "options": [
                    "Incorrect Option A",
                    "Incorrect Option B",
                    "Correct Option C",
                    "Incorrect Option D"
                  ],
                  "correctOptionIndex": 2,
                  "explanation": "A detailed explanation of why the correct option is right and others are wrong."
                },
                ...
              ]
            }
        """.trimIndent()

        val systemInstruction = """
            You are an academic test generator. You must output ONLY valid, parsable JSON matching the requested schema. Do not enclose the JSON in markdown code blocks or add any trailing text.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d(TAG, "Quiz successfully generated: $jsonText")
                moshi.adapter(QuizResponse::class.java).fromJson(jsonText)?.questions
            } else {
                Log.e(TAG, "Received empty response from Gemini for quiz.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating quiz from Gemini API: ", e)
            null
        }
    }

    /**
     * Fallback high quality quiz questions in demo mode.
     */
    fun createDemoQuiz(subjectName: String, lectureTitle: String, keywords: String): List<QuizQuestion> {
        val lowercaseTitle = lectureTitle.lowercase()
        return when {
            lowercaseTitle.contains("neural") || lowercaseTitle.contains("network") || lowercaseTitle.contains("brain") -> {
                listOf(
                    QuizQuestion(
                        question = "What is the primary role of an activation function in an Artificial Neural Network?",
                        options = listOf(
                            "To multiply weights by their corresponding inputs",
                            "To introduce non-linearity into the network, preventing it from behaving like a simple linear regression model",
                            "To calculate the total sum of all bias inputs",
                            "To store backup connections in case neurons decay"
                        ),
                        correctOptionIndex = 1,
                        explanation = "An activation function introduces non-linearity, which allows neural networks to learn complex patterns. Without it, the network would just perform linear combinations."
                    ),
                    QuizQuestion(
                        question = "Which of the following activation functions is commonly used to introduce non-linearity?",
                        options = listOf(
                            "Linear Step",
                            "Simple Summation",
                            "ReLU (Rectified Linear Unit)",
                            "Bisection Constant"
                        ),
                        correctOptionIndex = 2,
                        explanation = "ReLU (Rectified Linear Unit) is one of the most popular activation functions, outputting the input directly if positive, and zero otherwise, introducing vital non-linearity."
                    ),
                    QuizQuestion(
                        question = "How are numerical inputs processed by a single artificial neuron?",
                        options = listOf(
                            "Inputs are divided by weights and bias is subtracted",
                            "Inputs are multiplied by connection weights, summed together, and passed through an activation function",
                            "Inputs bypass the weight matrix to ensure maximum velocity",
                            "Inputs are averaged and stored in temporary memory registers"
                        ),
                        correctOptionIndex = 1,
                        explanation = "A neuron sums the weighted inputs, adds a bias term, and passes this net input through an activation function to generate the final output."
                    )
                )
            }
            lowercaseTitle.contains("supply") || lowercaseTitle.contains("demand") || lowercaseTitle.contains("market") || lowercaseTitle.contains("price") -> {
                listOf(
                    QuizQuestion(
                        question = "What happens to the supply curve if a resource shock decreases production capabilities?",
                        options = listOf(
                            "The supply curve shifts rightward, lowering equilibrium price",
                            "The supply curve shifts leftward, causing a shortage and pushing prices up",
                            "The supply curve remains static while demand collapses instantly",
                            "The supply curve transforms into a vertical line representing infinite elasticity"
                        ),
                        correctOptionIndex = 1,
                        explanation = "A resource shock that restricts supply shifts the supply curve to the left, which decreases quantity supplied and raises the equilibrium price."
                    ),
                    QuizQuestion(
                        question = "What does price elasticity of demand measure?",
                        options = listOf(
                            "How responsive producers are to fluctuations in factory overhead",
                            "How responsive buyers are to changes in price",
                            "The total quantity of goods shipped divided by total consumer net worth",
                            "The historical deviation of product quality"
                        ),
                        correctOptionIndex = 1,
                        explanation = "Price elasticity of demand measures the percentage change in quantity demanded in response to a percentage change in price."
                    )
                )
            }
            lowercaseTitle.contains("rome") || lowercaseTitle.contains("roman") || lowercaseTitle.contains("history") -> {
                listOf(
                    QuizQuestion(
                        question = "Which emperor partitioned the Roman Empire into Eastern and Western sectors to improve administration?",
                        options = listOf(
                            "Julius Caesar",
                            "Emperor Diocletian",
                            "Emperor Constantine",
                            "Augustus Caesar"
                        ),
                        correctOptionIndex = 1,
                        explanation = "Emperor Diocletian partitioned the Roman Empire to make it easier to govern, which eventually led to distinct administrative developments and accelerated the collapse of the Western Roman Empire."
                    ),
                    QuizQuestion(
                        question = "What major factor is cited by historians as a primary contributor to the collapse of the Western Roman Empire around 476 CE?",
                        options = listOf(
                            "An sudden outbreak of agricultural space debris",
                            "A combination of political corruption, economic hyperinflation, military decay, and barbarian migrations",
                            "The invention of paper currency and commercial banks",
                            "A sudden and massive solar eclipse"
                        ),
                        correctOptionIndex = 1,
                        explanation = "Historians agree that Rome's fall was multifactorial, including internal issues like inflation and political corruption alongside external pressures such as Goth and Vandal migrations."
                    )
                )
            }
            else -> {
                listOf(
                    QuizQuestion(
                        question = "Which technique is characterized by retrieving information actively from memory rather than passively reviewing notes?",
                        options = listOf(
                            "Passive Highlighting",
                            "Active Recall",
                            "Textbook Re-reading",
                            "Subconscious Listening"
                        ),
                        correctOptionIndex = 1,
                        explanation = "Active Recall forces the brain to retrieve information, which strengthens neural pathways and memory retention far better than passive re-reading."
                    ),
                    QuizQuestion(
                        question = "How does spacing review sessions over increasing intervals of time benefit memory?",
                        options = listOf(
                            "It triggers the Spaced Repetition effect, optimizing synaptic storage intervals",
                            "It causes temporary amnesia, forcing complete rebuilds of information",
                            "It decreases total sleep requirements during final exams",
                            "It eliminates the need for active recall practice"
                        ),
                        correctOptionIndex = 0,
                        explanation = "Spaced repetition uses the spacing effect where reviewing information at spaced intervals enhances the learning curve and memory consolidation."
                    )
                )
            }
        }
    }
}
