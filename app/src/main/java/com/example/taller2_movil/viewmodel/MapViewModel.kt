package com.example.taller2_movil.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    val errorMessage: String? = null,
    // BONO: ruta trazada entre ubicación actual y destino seleccionado
    val routePolyline: List<LatLng> = emptyList(),
    val routeDestination: LatLng? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val sensorManager =
        context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager

    // ── Solicitud de actualización de ubicación ───────────────────────────────
    private val locationRequest = LocationRequest.Builder(3000L)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMinUpdateIntervalMillis(1000L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                _uiState.update { state ->
                    state.copy(
                        userLocation = latLng,
                        routePoints = state.routePoints + latLng
                    )
                }
            }
        }
    }

    // ── Sensor de luminosidad ─────────────────────────────────────────────────
    private val lightSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            _uiState.update { it.copy(isDarkMap = event.values[0] < 50f) }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        registerLightSensor()
    }

    private fun registerLightSensor() {
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorManager.registerListener(
            lightSensorListener,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    // ── Inicio/parada de actualizaciones de ubicación ────────────────────────
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun setFollowUser(follow: Boolean) {
        _uiState.update { it.copy(followUser = follow) }
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
                            routeDestination = latLng,
                            routePolyline = emptyList(),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Dirección no encontrada")
                    }
                }
            } catch (e: Exception) {
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
                        longClickMarkers = state.longClickMarkers + Pair(latLng, address),
                        routeDestination = latLng,
                        routePolyline = emptyList()
                    )
                }
            } catch (e: Exception) {
                val fallback = "Lat: %.4f, Lng: %.4f".format(latLng.latitude, latLng.longitude)
                _uiState.update { state ->
                    state.copy(
                        longClickMarkers = state.longClickMarkers + Pair(latLng, fallback),
                        routeDestination = latLng,
                        routePolyline = emptyList()
                    )
                }
            }
        }
    }

    // ── BONO: Directions API → ruta entre ubicación actual y destino ──────────
    fun fetchRoute(destination: LatLng) {
        val origin = _uiState.value.userLocation ?: run {
            _uiState.update { it.copy(errorMessage = "Ubicación actual no disponible") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val apiKey = getApiKey()
                val url = buildString {
                    append("https://maps.googleapis.com/maps/api/directions/json")
                    append("?origin=${origin.latitude},${origin.longitude}")
                    append("&destination=${destination.latitude},${destination.longitude}")
                    append("&key=$apiKey")
                }
                val responseText = withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.requestMethod = "GET"
                    conn.inputStream.bufferedReader().use { it.readText() }.also {
                        conn.disconnect()
                    }
                }
                val json = JSONObject(responseText)
                val status = json.optString("status")
                if (status == "OK") {
                    val encoded = json
                        .getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("overview_polyline")
                        .getString("points")
                    val points = decodePolyline(encoded)
                    _uiState.update { it.copy(routePolyline = points, isLoading = false) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Sin ruta disponible: $status")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Error al obtener ruta")
                }
            }
        }
    }

    // ── Obtener API key desde el manifest ────────────────────────────────────
    private fun getApiKey(): String {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            info.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ── Decodificador de Google Encoded Polyline ─────────────────────────────
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    // ── Limpiar errores ───────────────────────────────────────────────────────
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(lightSensorListener)
        stopLocationUpdates()
    }
}
