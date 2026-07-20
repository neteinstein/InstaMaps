package org.neteinstein.instamaps.feature.videoprocessing.domain

import org.neteinstein.instamaps.core.common.LatLng

/**
 * Pure regex-based text parser run against any block of location-adjacent text this app gathers -
 * a video's caption/description (see [ExtractLocationCandidatesFromDescriptionUseCase]) or OCR'd
 * on-screen text from [ExtractLocationCandidatesUseCase]'s video frames - via the shared
 * [LocationTextAnalyzer] (see `feature:share`'s `ProcessSharedUrlUseCase` for how the ranked
 * candidates either source produces get resolved to a real place). Ported/generalized from the
 * original single-module app's caption parser, extended to return every signal found (ranked by
 * [LocationCandidate.confidence]) instead of only the first match, since callers gather text from
 * multiple sources/frames and want to try the best-ranked candidate first rather than committing
 * to whichever pattern happened to run first.
 *
 * Tries, in order of how unambiguous the signal is:
 * 1. An explicit marker (`Location:`, `loc:`, `place:`, `at:`, `in:`, 📍, 🗺️).
 * 2. A literal "lat, lng" coordinate pair.
 * 3. A CamelCase hashtag that looks like a place name (e.g. `#NewYorkCity`), or a single
 *    capitalized-word hashtag not in [GENERIC_HASHTAGS] (e.g. `#Dishoom`).
 * 4. A run of 2-4 capitalized words anywhere in the text (e.g. "Best tacos at Tacos El Gordo" or
 *    an ALL-CAPS on-screen overlay like "TONY'S PIZZA NYC") whose first word isn't a common,
 *    non-name sentence opener - see [extractCapitalizedPhrases]. This is the last-resort signal:
 *    many captions/overlays simply name the place with no marker, hashtag, or address at all, so
 *    rather than giving up, the plain-text guess most likely to be a name is still worth trying
 *    against the Places SDK's own fuzzy text search.
 */
class LocationTextParser {
    fun parse(text: String): List<LocationCandidate> {
        if (text.isBlank()) return emptyList()

        return listOfNotNull(
            extractExplicitLocation(text),
            extractCoordinates(text),
        ) + extractHashtagLocations(text) + extractCapitalizedPhrases(text)
    }

    private fun extractExplicitLocation(text: String): LocationCandidate.PlaceName? {
        for (pattern in EXPLICIT_PATTERNS) {
            val name = pattern.find(text)?.groupValues?.get(1)?.trim()
            if (!name.isNullOrBlank()) {
                return LocationCandidate.PlaceName(text = name, confidence = EXPLICIT_CONFIDENCE)
            }
        }
        return null
    }

    private fun extractCoordinates(text: String): LocationCandidate.Coordinates? {
        val match = COORDINATE_PATTERN.find(text) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        // Out-of-range values (e.g. a price/ratio that happens to look like "x.xx, y.yy") are a
        // false positive, not a malformed coordinate - LatLng's own validation is the source of
        // truth for what counts as a real coordinate pair, so we defer to it rather than
        // duplicating range checks here.
        val latLng = runCatching { LatLng(latitude = latitude, longitude = longitude) }.getOrNull() ?: return null
        return LocationCandidate.Coordinates(latLng = latLng, confidence = COORDINATES_CONFIDENCE)
    }

    private fun extractHashtagLocations(text: String): List<LocationCandidate.PlaceName> =
        HASHTAG_PATTERN.findAll(text).mapNotNull { match ->
            val spaced = match.groupValues[1].replace(CAMEL_CASE_BOUNDARY, " ").trim()
            if (spaced.isBlank()) return@mapNotNull null

            val isMultiWord = spaced.contains(' ')
            // A single capitalized word is a much weaker place-name signal than a multi-word
            // camelCase hashtag (e.g. #Dishoom vs #NewYorkCity) - it's just as likely to be a
            // generic tag someone happened to capitalize, so it's worth filtering the common ones
            // out rather than trying them all against the Places SDK.
            if (!isMultiWord && spaced.lowercase() in GENERIC_HASHTAGS) return@mapNotNull null

            val confidence = if (isMultiWord) HASHTAG_CAMEL_CASE_CONFIDENCE else HASHTAG_SINGLE_WORD_CONFIDENCE
            LocationCandidate.PlaceName(text = spaced, confidence = confidence)
        }.toList()

    /**
     * Last-resort heuristic: a run of 2-4 consecutive capitalized words, anywhere in the text,
     * not preceded by a common sentence-opener/filler word. Matches both Title Case ("Tony's
     * Pizza") and ALL CAPS ("TONY'S PIZZA") since on-screen video overlays are frequently
     * stylized in one case or the other. Deliberately simple (no real NLP/NER model on-device) -
     * it will occasionally pick up a menu item or a generic phrase, but that's an acceptable
     * trade-off for a fallback that otherwise finds nothing at all - this tier's low confidence
     * means it's only ever tried after every stronger signal has failed, and the Places SDK's
     * own fuzzy text search does the rest of the work of turning "a likely name" into a real
     * result.
     */
    private fun extractCapitalizedPhrases(text: String): List<LocationCandidate.PlaceName> =
        CAPITALIZED_PHRASE_PATTERN.findAll(text)
            .map { it.value.trim() }
            .filter { phrase -> phrase.substringBefore(' ').lowercase() !in LEADING_STOPWORDS }
            .map { LocationCandidate.PlaceName(text = it, confidence = CAPITALIZED_PHRASE_CONFIDENCE) }
            .toList()

    private companion object {
        val EXPLICIT_PATTERNS =
            listOf(
                Regex("\\b(?:location|loc|place|at|in):\\s*([^\\n#@]+)", RegexOption.IGNORE_CASE),
                Regex("📍\\s*([^\\n#@]+)"),
                Regex("🗺️?\\s*([^\\n#@]+)"),
            )
        val COORDINATE_PATTERN = Regex("(-?\\d{1,3}\\.\\d+),\\s*(-?\\d{1,3}\\.\\d+)")
        val HASHTAG_PATTERN = Regex("#([A-Z][a-zA-Z]*)")
        val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")

        // Words starting with an uppercase letter, then any mix of letters/apostrophes/hyphens
        // (so both "Tony's" and ALL-CAPS "TONY'S" match the same token), 2 to 4 in a row,
        // separated by plain spaces/tabs only - NOT '\s' - so a phrase never spans a line break
        // and glues together words from what are usually two unrelated caption lines/sentences.
        val CAPITALIZED_PHRASE_PATTERN = Regex("\\b[A-Z][A-Za-z'-]*(?:[ \\t]+[A-Z][A-Za-z'-]*){1,3}\\b")

        const val EXPLICIT_CONFIDENCE = 0.9f
        const val COORDINATES_CONFIDENCE = 0.95f
        const val HASHTAG_CAMEL_CASE_CONFIDENCE = 0.5f
        const val CAPITALIZED_PHRASE_CONFIDENCE = 0.4f
        const val HASHTAG_SINGLE_WORD_CONFIDENCE = 0.35f

        // Not exhaustive - just the sentence openers/fillers common enough in social captions to
        // be worth filtering before they're tried against the Places SDK as a "place name".
        val LEADING_STOPWORDS =
            setOf(
                "this", "that", "these", "those", "the", "a", "an", "best", "great", "amazing",
                "awesome", "incredible", "insane", "crazy", "perfect", "look", "watch", "try",
                "check", "come", "visit", "my", "our", "we", "you", "your", "just", "here", "there",
                "when", "where", "what", "why", "how", "who", "is", "are", "it", "if", "so", "now",
                "yes", "no", "wow", "omg", "literally", "honestly", "first", "last", "every", "some",
                "any", "all", "more", "most", "such", "really", "very",
            )

        // Not exhaustive - just the generic single-word hashtags common enough in food/travel
        // content to be worth filtering before they're tried against the Places SDK as a "place
        // name" on their own (unlike a multi-word camelCase hashtag, a single word is a weak
        // enough signal that these false positives aren't worth the wasted search).
        val GENERIC_HASHTAGS =
            setOf(
                "foodie", "travel", "travelgram", "wanderlust", "vacation", "trip", "explore",
                "explorepage", "viral", "fyp", "foryou", "foryoupage", "tiktok", "instagram",
                "instagood", "reels", "reel", "shorts", "short", "love", "blessed", "grateful",
                "motivation", "fitness", "gym", "workout", "ootd", "fashion", "style", "art",
                "music", "nature", "sunset", "sunrise", "weekend", "mood", "vibes", "aesthetic",
                "comedy", "funny", "vlog", "lifestyle", "trending", "follow", "followme",
                "subscribe", "video", "new", "best", "top", "must", "musttry", "musteat",
                "musthave", "mustvisit", "hiddengem", "gem", "local", "worldwide",
                "photooftheday", "picoftheday", "yummy", "delicious", "tasty", "foodporn",
                "foodstagram", "dinner", "lunch", "breakfast", "brunch", "restaurant", "cafe",
                "bar", "nightlife", "party", "fun", "happy", "life", "family", "friends",
            )
    }
}
