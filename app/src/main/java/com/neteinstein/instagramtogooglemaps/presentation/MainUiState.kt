package com.neteinstein.instagramtogooglemaps.presentation

sealed class MainUiState {
    object Idle : MainUiState()
    object Loading : MainUiState()
    data class LocationFound(val location: String) : MainUiState()
    data class Error(val message: String) : MainUiState()
}
