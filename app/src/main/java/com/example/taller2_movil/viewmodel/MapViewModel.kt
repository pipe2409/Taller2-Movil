package com.example.taller2_movil.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

// ── Estado de la pantalla del mapa ───────────────────────────────────────────
data class MapUiState(
    val userLocation: LatLng? = null,
    val routePoints: List<LatLng> = emptyList(),
    val searchedMarker: LatLng? = null,
    val searchedTitle: String = "",
    val longClickMarkers: List<Pair<LatLng, String>> = emptyList(),
    val followUser: Boolean = false,
    val isDarkMap: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val sensorManager =
        context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager

    private val lightSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            _uiState.update { it.copy(isDarkMap = event.values[0] < 50f) }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        registerLightSensor()
    }

    // ── Sensor de luminosidad ─────────────────────────────────────────────────
    private fun registerLightSensor() {
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorManager.registerListener(
            lightSensorListener,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    fun unregisterSensors() {
        sensorManager.unregisterListener(lightSensorListener)
    }

    override fun onCleared() {
        super.onCleared()
        unregisterSensors()
    }

    // ── Localización ──────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, null
                ).await()

                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    _uiState.update { state ->
                        state.copy(
                            userLocation = latLng,
                            routePoints = state.routePoints + latLng,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error obteniendo ubicación")
                }
            }
        }
    }

    fun setFollowUser(follow: Boolean) {
        _uiState.update { it.copy(followUser = follow) }
        if (follow) startFollowingUser()
    }

    private fun startFollowingUser() {
        viewModelScope.launch {
            while (_uiState.value.followUser) {
                fetchCurrentLocation()
                kotlinx.coroutines.delay(5000L)
            }
        }
    }

    // ── Geocoder: texto → coordenadas ─────────────────────────────────────────
    fun searchAddress(address: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1)
                if (!results.isNullOrEmpty()) {
                    val result = results[0]
                    val latLng = LatLng(result.latitude, result.longitude)
                    _uiState.update { state ->
                        state.copy(
                            searchedMarker = latLng,
                            searchedTitle = address,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Dirección no encontrada")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error buscando dirección")
                }
            }
        }
    }

    // ── Geocoder: coordenadas → texto ─────────────────────────────────────────
    fun addLongClickMarker(latLng: LatLng) {
        viewModelScope.launch {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val address = if (!results.isNullOrEmpty()) {
                    results[0].getAddressLine(0)
                } else {
                    "Lat: %.4f, Lng: %.4f".format(latLng.latitude, latLng.longitude)
                }
                _uiState.update { state ->
                    state.copy(
                        longClickMarkers = state.longClickMarkers + Pair(latLng, address)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val fallback = "Lat: %.4f, Lng: %.4f".format(latLng.latitude, latLng.longitude)
                _uiState.update { state ->
                    state.copy(
                        longClickMarkers = state.longClickMarkers + Pair(latLng, fallback)
                    )
                }
            }
        }
    }

    // ── Limpiar errores ───────────────────────────────────────────────────────
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
