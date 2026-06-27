package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.Flashcard
import com.example.data.Lecture
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportLectureToPdf(
        context: Context,
        lecture: Lecture,
        flashcards: List<Flashcard>
    ) {
        try {
            val pdfDocument = PdfDocument()
            
            // Standard A4 dimensions in points: 595 x 842
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f
            val contentWidth = pageWidth - (margin * 2)

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Paint styles
            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42) // Deep Slate
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(99, 102, 241) // Polish Indigo Primary
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = Color.rgb(100, 116, 139) // Slate Gray
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }

            val headingPaint = Paint().apply {
                color = Color.rgb(30, 41, 59) // Dark Gray
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(15, 23, 42) // Charcoal Black
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val bodyBoldPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val dividerPaint = Paint().apply {
                color = Color.rgb(226, 232, 240) // Slate Divider
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

            var currentY = margin + 20f

            // Helper to handle page overflows automatically
            fun checkPageOverflow(requiredHeight: Float) {
                if (currentY + requiredHeight > pageHeight - margin - 30f) {
                    // Draw Footer on current page
                    canvas.drawText("Page $pageNumber", margin, pageHeight - margin + 10f, footerPaint)
                    val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                    canvas.drawText("AI Lecture Companion • $dateStr", pageWidth - margin - 180f, pageHeight - margin + 10f, footerPaint)

                    pdfDocument.finishPage(page)
                    
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = margin + 20f
                }
            }

            // Custom wrapped text drawer with automatic height tracking and page break checking
            fun drawParagraph(text: String, isBold: Boolean = false) {
                val paintToUse = if (isBold) bodyBoldPaint else bodyPaint
                val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (words.isEmpty()) return

                var currentLine = StringBuilder()
                val lineSpacing = 15f

                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                    val testWidth = paintToUse.measureText(testLine)
                    if (testWidth > contentWidth) {
                        checkPageOverflow(lineSpacing)
                        canvas.drawText(currentLine.toString(), margin, currentY, paintToUse)
                        currentY += lineSpacing
                        currentLine = StringBuilder(word)
                    } else {
                        currentLine.append(if (currentLine.isEmpty()) word else " $word")
                    }
                }
                if (currentLine.isNotEmpty()) {
                    checkPageOverflow(lineSpacing)
                    canvas.drawText(currentLine.toString(), margin, currentY, paintToUse)
                    currentY += lineSpacing
                }
            }

            // 1. Draw PDF Header
            checkPageOverflow(80f)
            canvas.drawText("AI LECTURE COMPANION STUDY GUIDE", margin, currentY, subtitlePaint)
            currentY += 24f
            
            // Draw title wrapped if too long
            val titleWords = lecture.title.split("\\s+".toRegex())
            var currentTitleLine = StringBuilder()
            for (word in titleWords) {
                val testLine = if (currentTitleLine.isEmpty()) word else "${currentTitleLine} $word"
                if (titlePaint.measureText(testLine) > contentWidth) {
                    canvas.drawText(currentTitleLine.toString(), margin, currentY, titlePaint)
                    currentY += 24f
                    currentTitleLine = StringBuilder(word)
                } else {
                    currentTitleLine.append(if (currentTitleLine.isEmpty()) word else " $word")
                }
            }
            canvas.drawText(currentTitleLine.toString(), margin, currentY, titlePaint)
            currentY += 18f

            val formattedDate = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date(lecture.date))
            canvas.drawText("Date Generated: $formattedDate", margin, currentY, metaPaint)
            currentY += 20f

            canvas.drawLine(margin, currentY, pageWidth - margin, currentY, dividerPaint)
            currentY += 25f

            // 2. Core Themes (Keywords)
            if (lecture.keywords.isNotBlank()) {
                checkPageOverflow(40f)
                canvas.drawText("Core Themes & Key Terms", margin, currentY, headingPaint)
                currentY += 16f

                val masteredList = lecture.masteredKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val terms = lecture.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                
                var termsLine = StringBuilder("Key Phrases: ")
                terms.forEachIndexed { idx, term ->
                    val isMastered = masteredList.contains(term.lowercase())
                    val marker = if (isMastered) " [★ $term (Mastered)]" else " [$term]"
                    val comma = if (idx == terms.size - 1) "" else ","
                    termsLine.append(marker).append(comma)
                }
                drawParagraph(termsLine.toString())
                currentY += 15f
            }

            // 3. Executive Summary
            checkPageOverflow(40f)
            canvas.drawText("Executive Lecture Summary", margin, currentY, headingPaint)
            currentY += 18f

            val summaryCleaned = lecture.summary
                .replace("#", "")
                .replace("*", "")
                .replace("`", "")
                .replace("- ", "• ")

            val paragraphs = summaryCleaned.split("\n")
            for (para in paragraphs) {
                val trimmed = para.trim()
                if (trimmed.isEmpty()) {
                    currentY += 10f
                    continue
                }
                drawParagraph(trimmed)
            }
            currentY += 25f

            // 4. Study Flashcards
            if (flashcards.isNotEmpty()) {
                checkPageOverflow(40f)
                canvas.drawLine(margin, currentY, pageWidth - margin, currentY, dividerPaint)
                currentY += 25f

                checkPageOverflow(30f)
                canvas.drawText("Review Flashcards", margin, currentY, headingPaint)
                currentY += 20f

                flashcards.forEachIndexed { index, card ->
                    checkPageOverflow(80f)
                    val cardNum = index + 1
                    val masteryIndicator = if (card.isMastered) " (Mastered ★)" else ""
                    
                    canvas.drawText("Flashcard $cardNum$masteryIndicator", margin, currentY, subtitlePaint)
                    currentY += 15f
                    
                    canvas.drawText("Q: ", margin, currentY, bodyBoldPaint)
                    val qText = card.front.replace("*", "").replace("`", "")
                    drawParagraph(qText)
                    
                    canvas.drawText("A: ", margin, currentY, bodyBoldPaint)
                    val aText = card.back.replace("*", "").replace("`", "")
                    drawParagraph(aText)
                    
                    currentY += 15f
                }
            }

            // Draw Final Page Footer
            canvas.drawText("Page $pageNumber", margin, pageHeight - margin + 10f, footerPaint)
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
            canvas.drawText("AI Lecture Companion • $dateStr", pageWidth - margin - 180f, pageHeight - margin + 10f, footerPaint)

            pdfDocument.finishPage(page)

            // Save PDF document
            val titleSlug = lecture.title.replace("\\s+".toRegex(), "_").lowercase().filter { it.isLetterOrDigit() || it == '_' }
            val fileName = "Study_Guide_${titleSlug}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            // Share/Open via Intent
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Study Guide: ${lecture.title}")
                putExtra(Intent.EXTRA_TEXT, "Here is your generated offline study guide for lecture '${lecture.title}'. Included is the AI summary, key themes, and review study flashcards.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, "Export Study Guide PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
