package com.vextech.smart_edit.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vextech.smart_edit.core.library.OcrPolicies
import com.vextech.smart_edit.core.library.OcrScript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrService @Inject constructor(@ApplicationContext private val context: Context) {
    data class Block(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Result(val text: String, val language: String?, val blocks: List<Block>)

    suspend fun recognize(uri: Uri, script: OcrScript = OcrScript.LATIN): Result {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer: TextRecognizer = when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
        return try {
            val text = recognizer.process(image).await()
            val lang = if (text.text.isBlank()) null else {
                val identifier = LanguageIdentification.getClient()
                try { identifier.identifyLanguage(text.text).await() } finally { identifier.close() }
            }
            Result(
                text = text.text,
                language = lang?.takeUnless { it == "und" },
                blocks = text.textBlocks.mapNotNull { b -> b.boundingBox?.let { r -> Block(b.text, r.left, r.top, r.right, r.bottom) } },
            )
        } finally {
            recognizer.close()
        }
    }

    /**
     * Clockwise turn (0/90/180/270) that makes the page text upright. Reads the page at all four turns and keeps the one
     * that yields clearly the most text; returns 0 when unsure. Latin script only, so non-Latin pages usually return 0.
     */
    suspend fun detectOrientation(bitmap: Bitmap): Int {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val scores = IntArray(4)
            for (i in 0..3) scores[i] = recognizer.process(InputImage.fromBitmap(bitmap, i * 90)).await().text.count { it.isLetterOrDigit() }
            OcrPolicies.pageRotation(scores)
        } finally {
            recognizer.close()
        }
    }
}
