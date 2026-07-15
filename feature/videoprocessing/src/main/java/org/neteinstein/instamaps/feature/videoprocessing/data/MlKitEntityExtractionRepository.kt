package org.neteinstein.instamaps.feature.videoprocessing.data

import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import kotlinx.coroutines.tasks.await
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.feature.videoprocessing.domain.EntityExtractionRepository

/**
 * On-device address extraction via ML Kit's Entity Extraction API.
 *
 * Unlike DateTime/FlightNumber/Iban/Money/etc., ML Kit has no strongly-typed `AddressEntity` -
 * confirmed by decompiling the real `entity-extraction:16.0.0-beta6` AAR, [Entity] only exposes
 * [Entity.TYPE_ADDRESS] as a type tag with no structured accessor. The only information available
 * for an address hit is the matched substring, [com.google.mlkit.nl.entityextraction.EntityAnnotation.getAnnotatedText],
 * so that's all this repository reads.
 */
class MlKitEntityExtractionRepository(
    private val entityExtractor: EntityExtractor,
) : EntityExtractionRepository {
    override suspend fun extractAddresses(text: String): Result<List<String>> =
        safeCall(mapError = { AppError.PlatformUnavailable("Entity extraction failed", it) }) {
            if (text.isBlank()) {
                emptyList()
            } else {
                entityExtractor.downloadModelIfNeeded().await()

                val params =
                    EntityExtractionParams.Builder(text)
                        .setEntityTypesFilter(setOf(Entity.TYPE_ADDRESS))
                        .build()

                entityExtractor
                    .annotate(params)
                    .await()
                    .filter { annotation -> annotation.entities.any { it.type == Entity.TYPE_ADDRESS } }
                    .map { annotation -> annotation.annotatedText }
                    .distinct()
            }
        }
}
