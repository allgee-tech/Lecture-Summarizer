package com.example.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Abstraction over microphone capture so the ViewModel stays unit-testable
 * (tests substitute a fake instead of touching real audio hardware).
 */
interface AudioRecorderController {
    /**
     * Configures and starts capturing microphone audio into [outputFile].
     * Throws if the microphone is unavailable or the recorder fails to start.
     */
    fun start(outputFile: File)

    /**
     * Stops capturing and finalizes the output file.
     * Throws if the recording was too short to produce valid audio.
     */
    fun stop()

    /** Aborts the capture best-effort; the output file should be discarded. */
    fun cancel()
}

/**
 * Production [AudioRecorderController] backed by [MediaRecorder].
 * Records AAC speech inside an MP4 container (.m4a) at 64 kbps mono, which is
 * ~0.5 MB per minute — roughly 35 minutes of lecture fits well under the
 * Gemini API's 20 MB inline-request limit.
 */
class MediaRecorderController(private val context: Context) : AudioRecorderController {

    private var recorder: MediaRecorder? = null

    override fun start(outputFile: File) {
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(64_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setOutputFile(outputFile.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
        } catch (e: Exception) {
            mediaRecorder.release()
            throw e
        }
    }

    override fun stop() {
        val active = recorder ?: return
        try {
            // stop() throws if called before any samples were captured.
            active.stop()
        } finally {
            active.reset()
            active.release()
            recorder = null
        }
    }

    override fun cancel() {
        val active = recorder ?: return
        try {
            active.reset()
        } catch (_: Exception) {
            // best-effort cleanup
        } finally {
            active.release()
            recorder = null
        }
    }
}

/** MIME type used for in-app recordings (.m4a from [MediaRecorderController]). */
const val RECORDED_AUDIO_MIME_TYPE = "audio/mp4"

const val RECORDED_AUDIO_FILE_EXTENSION = ".m4a"

/**
 * Best-effort audio MIME type for a user-imported file. The file extension wins
 * (resolvers frequently report an unhelpful generic type); unknown extensions
 * fall back to the resolver's type, then to audio/mp3.
 */
fun audioMimeTypeFor(fileName: String, resolverMimeType: String? = null): String {
    val fromExtension = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mp3"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg", "opus", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        "aiff", "aif" -> "audio/aiff"
        else -> null
    }
    val fromResolver = resolverMimeType?.takeIf { it.startsWith("audio/") }
    return fromExtension ?: fromResolver ?: "audio/mp3"
}
