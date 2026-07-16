package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * Fetches a shared video's metadata - just caption/description text, not the media itself -
 * behind a shared social media URL. Backed by a single `yt-dlp --dump-json` call (see
 * [org.neteinstein.instamaps.feature.videoprocessing.data.YtDlpVideoMetadataRepository]), which is
 * far cheaper than [VideoDownloadRepository.download]'s full media download, which is exactly why
 * [ExtractLocationCandidatesFromDescriptionUseCase] tries it before the video-OCR pipeline.
 *
 * Mirrors [TextRecognitionRepository.recognizeText]'s convention: an empty string (not null)
 * represents "no description text was found", keeping downstream analysis
 * ([LocationTextAnalyzer]) blank-checking one way for both OCR'd and caption text.
 */
interface VideoMetadataRepository {
    suspend fun fetchDescription(url: String): Result<String>
}
