package com.beykus.beygram.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.data.appearance.AppearanceSettingsStore
import com.beykus.beygram.data.appearance.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The theme choice on screen. Null until the stored choice has been read, so nothing is drawn with a guessed theme. */
class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppearanceSettingsStore(application)
    private val _themeMode = MutableStateFlow<ThemeMode?>(null)
    val themeMode: StateFlow<ThemeMode?> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = store.themeMode.first()
            _themeMode.update { current -> current ?: stored }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            try {
                store.setThemeMode(mode)
            } catch (error: IOException) {
                // The choice stays on screen for this session. A failed write only means it is not kept after a restart.
            }
        }
    }
}
