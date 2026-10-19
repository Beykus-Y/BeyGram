package com.beykus.beygram.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.AppearanceSettingsStore
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The appearance choice on screen. Null until the stored choice has been read, so nothing is drawn with a guessed theme. */
class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppearanceSettingsStore(application)
    private val _config = MutableStateFlow<AppearanceConfig?>(null)
    val config: StateFlow<AppearanceConfig?> = _config.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = store.config.first()
            _config.update { current -> current ?: stored }
        }
    }

    fun save(config: AppearanceConfig) {
        _config.value = config
        viewModelScope.launch {
            try {
                store.save(config)
            } catch (error: IOException) {
                // The choice stays on screen for this session. A failed write only means it is not kept after a restart.
            }
        }
    }
}
