package com.neteinstein.instagramtogooglemaps.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.instagramtogooglemaps.domain.usecase.ExtractLocationUseCase
import com.neteinstein.instagramtogooglemaps.domain.usecase.GetReelInfoUseCase
import kotlinx.coroutines.launch

class MainViewModel(
    private val getReelInfoUseCase: GetReelInfoUseCase,
    private val extractLocationUseCase: ExtractLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableLiveData<MainUiState>(MainUiState.Idle)
    val uiState: LiveData<MainUiState> = _uiState

    fun processSharedUrl(url: String) {
        _uiState.value = MainUiState.Loading
        viewModelScope.launch {
            val reelResult = getReelInfoUseCase(url)
            reelResult.fold(
                onSuccess = { reelInfo ->
                    val location = extractLocationUseCase(reelInfo.description)
                    if (location != null) {
                        _uiState.value = MainUiState.LocationFound(location.name)
                    } else {
                        _uiState.value = MainUiState.Error("No location found in the reel description")
                    }
                },
                onFailure = { error ->
                    _uiState.value = MainUiState.Error(
                        error.message ?: "Failed to fetch reel information"
                    )
                }
            )
        }
    }
}
