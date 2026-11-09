package com.rshock.quanjing.auto

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OcrEngine {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognize(bmp: Bitmap): List<Text.Line> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { result ->
                    val lines = mutableListOf<Text.Line>()
                    result.textBlocks.forEach { b -> lines.addAll(b.lines) }
                    cont.resume(lines)
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
}