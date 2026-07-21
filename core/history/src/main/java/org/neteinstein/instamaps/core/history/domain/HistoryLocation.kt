package org.neteinstein.instamaps.core.history.domain

/**
 * A minimal, geocoding-agnostic snapshot of one place a share resolved to, kept purely for
 * display in the history list - see [HistoryEntry]. Deliberately doesn't depend on
 * `feature:geocoding`'s `ResolvedLocation` - core modules never depend on feature modules, only
 * the other way around - so `feature:share` maps `ResolvedLocation` down to this shape when
 * recording a finished share (see `RecordHistoryEntryUseCase`'s call site in
 * `ProcessSharedUrlWorker`), and `feature:history` maps it back up to a `feature:maps`
 * `MapsDestination` to build the "open in Google Maps" CTA.
 */
data class HistoryLocation(
    val name: String,
    val address: String?,
)
