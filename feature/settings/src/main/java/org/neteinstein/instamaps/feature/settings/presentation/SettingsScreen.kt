package org.neteinstein.instamaps.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.neteinstein.instamaps.core.designsystem.component.PrimaryButton
import org.neteinstein.instamaps.feature.settings.R

/**
 * Stateful entry point: wires [SettingsViewModel] to [SettingsScreen].
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveClicked = viewModel::onSaveClicked,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless, preview/test-friendly screen: title bar up top, the API key field as the sole body
 * content, and the Save button pinned to the bottom of the screen via [Scaffold]'s `bottomBar` -
 * not just the bottom of a scrolling column - so it stays put regardless of field content length.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                PrimaryButton(text = stringResource(R.string.settings_save), onClick = onSaveClicked)
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
            OutlinedTextField(
                value = uiState.apiKeyInput,
                onValueChange = onApiKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.settings_api_key_label)) },
                supportingText = {
                    Text(
                        text =
                            if (uiState.justSaved) {
                                stringResource(R.string.settings_saved_confirmation)
                            } else {
                                stringResource(R.string.settings_api_key_supporting_text)
                            },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            )
        }
    }
}
