package org.neteinstein.instamaps.feature.videoprocessing.domain

import android.graphics.Bitmap

/** On-device OCR over a single video frame. */
interface TextRecognitionRepository {
    suspend fun recognizeText(bitmap: Bitmap): Result<String>
}
