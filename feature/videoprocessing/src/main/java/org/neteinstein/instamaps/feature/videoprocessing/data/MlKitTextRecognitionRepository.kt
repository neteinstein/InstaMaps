package org.neteinstein.instamaps.feature.videoprocessing.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.tasks.await
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.feature.videoprocessing.domain.TextRecognitionRepository

/**
 * On-device OCR via ML Kit's Latin-script text recognizer. [recognizer] is expensive to spin up,
 * so Koin provides a single shared instance for the app's lifetime rather than one per frame.
 */
class MlKitTextRecognitionRepository(
    private val recognizer: TextRecognizer,
) : TextRecognitionRepository {
    override suspend fun recognizeText(bitmap: Bitmap): Result<String> =
        safeCall(mapError = { AppError.PlatformUnavailable("Text recognition failed", it) }) {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image).await().text
        }
}
