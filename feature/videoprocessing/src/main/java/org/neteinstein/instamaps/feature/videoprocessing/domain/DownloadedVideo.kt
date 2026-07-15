package org.neteinstein.instamaps.feature.videoprocessing.domain

import java.io.File

/**
 * A video downloaded to local, app-private cache storage, ready for frame extraction. [file]
 * always lives inside a unique per-download subdirectory so cleanup ([VideoDownloadRepository.delete])
 * is a single directory delete with no risk of colliding with another in-flight download.
 */
data class DownloadedVideo(
    val file: File,
)
