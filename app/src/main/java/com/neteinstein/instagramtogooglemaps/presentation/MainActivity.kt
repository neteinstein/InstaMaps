package com.neteinstein.instagramtogooglemaps.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.neteinstein.instagramtogooglemaps.R
import com.neteinstein.instagramtogooglemaps.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.processSharedUrl(sharedText)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is MainUiState.Idle -> showIdle()
                is MainUiState.Loading -> showLoading()
                is MainUiState.LocationFound -> showLocationFound(state.location)
                is MainUiState.Error -> showError(state.message)
            }
        }
    }

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tilLocation.visibility = View.GONE
        binding.btnAddToMaps.visibility = View.GONE
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.tilLocation.visibility = View.GONE
        binding.btnAddToMaps.visibility = View.GONE
    }

    private fun showLocationFound(location: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tilLocation.visibility = View.VISIBLE
        binding.btnAddToMaps.visibility = View.VISIBLE
        binding.etLocation.setText(location)
        binding.btnAddToMaps.setOnClickListener {
            openGoogleMaps(binding.etLocation.text.toString())
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tilLocation.visibility = View.GONE
        binding.btnAddToMaps.visibility = View.GONE
        binding.tvError.text = message
    }

    private fun openGoogleMaps(location: String) {
        if (location.isBlank()) {
            Toast.makeText(this, R.string.error_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(location)}")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
}
