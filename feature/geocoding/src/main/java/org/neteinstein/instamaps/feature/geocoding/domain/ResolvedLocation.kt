package org.neteinstein.instamaps.feature.geocoding.domain

import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

/**
 * A single real-world place [LocationRepository] identified from a shared video's text.
 * [ResolveLocationUseCase] returns these ordered most-to-least likely to be the place the video is
 * actually about, since a caption/OCR pass often surfaces more than one candidate (the featured
 * spot plus a mentioned neighborhood, a cross-street, a chain's other locations, ...). [name] and
 * [address] are shown to the user so they can pick the right one themselves; [destination] is only
 * handed to `MapsLauncher` once they do.
 */
data class ResolvedLocation(
    val name: String,
    val address: String?,
    val destination: MapsDestination,
)
