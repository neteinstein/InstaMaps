package org.neteinstein.instamaps.feature.videoprocessing.di

import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.videoprocessing.data.MediaMetadataRetrieverFrameExtractor
import org.neteinstein.instamaps.feature.videoprocessing.data.MlKitEntityExtractionRepository
import org.neteinstein.instamaps.feature.videoprocessing.data.MlKitTextRecognitionRepository
import org.neteinstein.instamaps.feature.videoprocessing.data.YtDlpVideoDownloadRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.EntityExtractionRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.FrameExtractorRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.LocationTextParser
import org.neteinstein.instamaps.feature.videoprocessing.domain.TextRecognitionRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoDownloadRepository

val videoProcessingModule =
    module {
        single { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
        single { EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()) }

        // Constructed with androidContext() explicitly (rather than constructor-injected via a
        // plain get<Context>()) to match the pattern already established in
        // feature:geocoding's Koin module.
        single<VideoDownloadRepository> {
            YtDlpVideoDownloadRepository(context = androidContext(), dispatcherProvider = get())
        }
        singleOf(::MediaMetadataRetrieverFrameExtractor) { bind<FrameExtractorRepository>() }
        singleOf(::MlKitTextRecognitionRepository) { bind<TextRecognitionRepository>() }
        singleOf(::MlKitEntityExtractionRepository) { bind<EntityExtractionRepository>() }

        factory { LocationTextParser() }
        factory {
            ExtractLocationCandidatesUseCase(
                videoDownloadRepository = get(),
                frameExtractorRepository = get(),
                textRecognitionRepository = get(),
                entityExtractionRepository = get(),
                locationTextParser = get(),
                dispatcherProvider = get(),
            )
        }
    }
