package com.neteinstein.instagramtogooglemaps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.neteinstein.instagramtogooglemaps.data.network.RetrofitFactory
import com.neteinstein.instagramtogooglemaps.data.repository.LocationRepositoryImpl
import com.neteinstein.instagramtogooglemaps.domain.usecase.ExtractLocationUseCase
import com.neteinstein.instagramtogooglemaps.domain.usecase.GetReelInfoUseCase

class MainViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val api = RetrofitFactory.createInstagramApi()
            val repository = LocationRepositoryImpl(api)
            val getReelInfoUseCase = GetReelInfoUseCase(repository)
            val extractLocationUseCase = ExtractLocationUseCase()
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(getReelInfoUseCase, extractLocationUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
