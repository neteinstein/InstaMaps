package org.neteinstein.instamaps.feature.instagramauth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.neteinstein.instamaps.core.instagramauth.domain.SaveInstagramSessionUseCase

/**
 * Thin ViewModel for the WebView login screen: the WebView itself owns all login-flow state (URL
 * loading, form submission, cookies) - this class's only job is persisting the session once
 * [InstagramLoginScreen] detects a successful login via its `WebViewClient`, then flipping
 * [loginCompleted] so [InstagramLoginRoute] can navigate back to the pending share.
 */
class InstagramLoginViewModel(
    private val saveInstagramSessionUseCase: SaveInstagramSessionUseCase,
) : ViewModel() {
    private val _loginCompleted = MutableStateFlow(false)
    val loginCompleted: StateFlow<Boolean> = _loginCompleted.asStateFlow()

    fun onSessionCaptured(cookieHeader: String) {
        viewModelScope.launch {
            saveInstagramSessionUseCase(cookieHeader)
            _loginCompleted.value = true
        }
    }
}
