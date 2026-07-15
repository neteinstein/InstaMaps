package org.neteinstein.instamaps.feature.videoprocessing.domain

import android.graphics.Bitmap

/**
 * A single decoded video frame. [bitmap] is caller-owned: whoever consumes a [VideoFrame] off the
 * extraction pipeline must call [Bitmap.recycle] on it once done (see
 * [ExtractLocationCandidatesUseCase]), or the app will exhaust native memory within a handful of
 * frames.
 */
data class VideoFrame(
    val timestampMs: Long,
    val bitmap: Bitmap,
)
