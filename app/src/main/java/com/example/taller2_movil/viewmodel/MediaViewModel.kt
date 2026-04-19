// viewmodel/MediaViewModel.kt
package com.example.taller2_movil.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── Modo de la pantalla ───────────────────────────────────────────────────────
enum class MediaMode { PHOTO, VIDEO }

// ── Estado de la pantalla de media ───────────────────────────────────────────
data class MediaUiState(
    val mode: MediaMode = MediaMode.PHOTO,
    val mediaUri: Uri? = null,
    val tempCameraUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MediaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    // ── Cambiar modo Foto / Video ─────────────────────────────────────────────
    fun setMode(mode: MediaMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                mediaUri = null,      // limpiar media al cambiar modo
                tempCameraUri = null
            )
        }
    }

    // ── Guardar URI temporal para la cámara ───────────────────────────────────
    fun setTempCameraUri(uri: Uri) {
        _uiState.update { it.copy(tempCameraUri = uri) }
    }

    // ── Confirmar media capturada (cámara) ────────────────────────────────────
    fun onCameraResult(success: Boolean) {
        if (success) {
            _uiState.update { it.copy(mediaUri = it.tempCameraUri) }
        }
    }

    // ── Confirmar media seleccionada (galería) ────────────────────────────────
    fun onGalleryResult(uri: Uri?) {
        uri?.let {
            _uiState.update { state -> state.copy(mediaUri = it) }
        }
    }

    // ── Limpiar error ─────────────────────────────────────────────────────────
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}