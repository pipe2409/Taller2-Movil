package com.example.taller2_movil.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

// ─── Estilos JSON del mapa ───────────────────────────────────────────────────
private const val MAP_STYLE_DARK = """
[
  { "elementType": "geometry", "stylers": [{ "color": "#212121" }] },
  { "elementType": "labels.text.fill", "stylers": [{ "color": "#757575" }] },
  { "elementType": "labels.text.stroke", "stylers": [{ "color": "#212121" }] },
  { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#000000" }] },
  { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#484848" }] },
  { "featureType": "poi", "elementType": "geometry", "stylers": [{ "color": "#2c2c2c" }] }
]
"""

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Permisos de localización ──────────────────────────────────────────────
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // ── Estado del mapa ───────────────────────────────────────────────────────
    val cameraPositionState = rememberCameraPositionState()
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var followUser by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var searchedMarker by remember { mutableStateOf<LatLng?>(null) }
    var searchedTitle by remember { mutableStateOf("") }
    var longClickMarkers by remember { mutableStateOf<List<Pair<LatLng, String>>>(emptyList()) }
    var addressInput by remember { mutableStateOf("") }

    // ── Sensor de luminosidad ─────────────────────────────────────────────────
    var isDarkMap by remember { mutableStateOf(false) }
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val mapStyleOptions = remember(isDarkMap) {
        if (isDarkMap) MapStyleOptions(MAP_STYLE_DARK) else null
    }

    // Registrar sensor de luminosidad
    DisposableEffect(Unit) {
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                isDarkMap = event.values[0] < 50f
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // ── Obtener ubicación actual ──────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        if (!locationPermissions.allPermissionsGranted) return
        scope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, null
                ).await()
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    userLocation = latLng
                    routePoints = routePoints + latLng
                    if (followUser || routePoints.size == 1) {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Solicitar permisos y luego obtener ubicación al iniciar
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            fetchCurrentLocation()
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Seguir usuario cuando followUser = true
    LaunchedEffect(followUser) {
        if (followUser) {
            while (followUser) {
                fetchCurrentLocation()
                kotlinx.coroutines.delay(5000L)
            }
        }
    }

    // ── Geocoder: texto → coordenadas ─────────────────────────────────────────
    fun searchAddress(address: String) {
        if (address.isBlank()) return
        scope.launch {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1)
                if (!results.isNullOrEmpty()) {
                    val result = results[0]
                    val latLng = LatLng(result.latitude, result.longitude)
                    searchedMarker = latLng
                    searchedTitle = address
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Geocoder: coordenadas → texto ─────────────────────────────────────────
    fun reverseGeocode(latLng: LatLng): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!results.isNullOrEmpty()) results[0].getAddressLine(0) else "Desconocido"
        } catch (e: Exception) {
            "Desconocido"
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Barra superior con toggle y búsqueda
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mover el mapa con la posición del usuario?",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = followUser,
                    onCheckedChange = { followUser = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF1E3A6E),
                        checkedThumbColor = Color.White
                    )
                )
            }

            OutlinedTextField(
                value = addressInput,
                onValueChange = { addressInput = it },
                label = { Text("Dirección") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF1E3A6E)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1E3A6E),
                    focusedLabelColor = Color(0xFF1E3A6E)
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        searchAddress(addressInput)
                    }
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                )
            )
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = locationPermissions.allPermissionsGranted,
                mapStyleOptions = mapStyleOptions
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                zoomControlsEnabled = true
            ),
            onMapLongClick = { latLng ->
                scope.launch {
                    val address = reverseGeocode(latLng)
                    longClickMarkers = longClickMarkers + Pair(latLng, address)
                }
            }
        ) {
            userLocation?.let { loc ->
                Marker(
                    state = MarkerState(position = loc),
                    title = "Mi ubicación"
                )
            }

            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color = Color(0xFF5B4FC9),
                    width = 12f
                )
            }

            searchedMarker?.let { pos ->
                Marker(
                    state = MarkerState(position = pos),
                    title = searchedTitle,
                    snippet = searchedTitle
                )
            }

            longClickMarkers.forEach { (pos, address) ->
                Marker(
                    state = MarkerState(position = pos),
                    title = address,
                    snippet = address
                )
            }
        }
    }
}