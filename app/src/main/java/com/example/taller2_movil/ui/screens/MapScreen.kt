package com.example.taller2_movil.ui.screens

import android.Manifest
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taller2_movil.viewmodel.MapViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*

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
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
    var addressInput by remember { mutableStateOf("") }

    val mapStyleOptions = remember(uiState.isDarkMap) {
        if (uiState.isDarkMap) MapStyleOptions(MAP_STYLE_DARK) else null
    }

    // Solicitar permisos al iniciar
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.fetchCurrentLocation()
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Mover la cámara cuando cambia la ubicación del usuario o se busca algo
    LaunchedEffect(uiState.userLocation, uiState.searchedMarker) {
        if (uiState.followUser) {
            uiState.userLocation?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        } else if (uiState.searchedMarker != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(uiState.searchedMarker!!, 15f))
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .windowInsetsPadding(WindowInsets.systemBars)
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
                    checked = uiState.followUser,
                    onCheckedChange = { viewModel.setFollowUser(it) },
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
                        viewModel.searchAddress(addressInput)
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
                viewModel.addLongClickMarker(latLng)
            }
        ) {
            uiState.userLocation?.let { loc ->
                Marker(
                    state = MarkerState(position = loc),
                    title = "Mi ubicación"
                )
            }

            if (uiState.routePoints.size >= 2) {
                Polyline(
                    points = uiState.routePoints,
                    color = Color(0xFF5B4FC9),
                    width = 12f
                )
            }

            uiState.searchedMarker?.let { pos ->
                Marker(
                    state = MarkerState(position = pos),
                    title = uiState.searchedTitle,
                    snippet = uiState.searchedTitle
                )
            }

            uiState.longClickMarkers.forEach { (pos, address) ->
                Marker(
                    state = MarkerState(position = pos),
                    title = address,
                    snippet = address
                )
            }
        }
    }

    // Mostrar Snackbar de error si existe
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            // Aquí podrías mostrar un Toast o Snackbar
            viewModel.clearError()
        }
    }
}