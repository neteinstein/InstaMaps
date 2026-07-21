package org.neteinstein.instamaps.feature.history.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.neteinstein.instamaps.core.designsystem.component.PrimaryButton
import org.neteinstein.instamaps.core.history.domain.HistoryEntry
import org.neteinstein.instamaps.core.history.domain.HistoryLocation
import org.neteinstein.instamaps.feature.history.R
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import java.text.DateFormat
import java.util.Date

/** Stateful entry point: wires [HistoryViewModel] to [HistoryScreen]. */
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
    mapsLauncher: MapsLauncher = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HistoryScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenVideo = { url -> context.openUrl(url) },
        onOpenMaps = { location -> mapsLauncher.launch(location.toMapsDestination()) },
        modifier = modifier,
    )
}

/**
 * Stateless, preview/test-friendly screen: the last (up to 50) shared videos, newest first (see
 * `MAX_HISTORY_ENTRIES`), each clickable to reopen the original video, plus an "open in Google
 * Maps" CTA for the top-ranked location found for it, if any - see [HistoryEntryCard].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenMaps: (HistoryLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            uiState.isLoading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            uiState.entries.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        HistoryEntryCard(entry = entry, onOpenVideo = onOpenVideo, onOpenMaps = onOpenMaps)
                    }
                }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: HistoryEntry,
    onOpenVideo: (String) -> Unit,
    onOpenMaps: (HistoryLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenVideo(entry.url) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.history_open_video),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.formattedTimestamp(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.url,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val topLocation = entry.locations.firstOrNull()
            if (topLocation != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = topLocation.name, style = MaterialTheme.typography.bodyMedium)
                        val address = topLocation.address
                        if (!address.isNullOrBlank()) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.locations.size > 1) {
                            Text(
                                text = stringResource(R.string.history_more_locations, entry.locations.size - 1),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.history_open_maps),
                    onClick = { onOpenMaps(topLocation) },
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_no_location_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun HistoryEntry.formattedTimestamp(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

/** Mirrors how `feature:geocoding`'s `ResolvedLocation.destination` builds its query. */
private fun HistoryLocation.toMapsDestination(): MapsDestination {
    val query = if (!address.isNullOrBlank()) "$name, $address" else name
    return MapsDestination(query = query)
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
