package org.neteinstein.instamaps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.neteinstein.instamaps.data.network.RetrofitFactory
import org.neteinstein.instamaps.data.repository.LocationRepositoryImpl
import org.neteinstein.instamaps.domain.usecase.ExtractLocationUseCase
import org.neteinstein.instamaps.domain.usecase.GetReelInfoUseCase

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
