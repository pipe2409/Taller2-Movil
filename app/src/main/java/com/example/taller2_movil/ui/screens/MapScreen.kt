package com.example.taller2_movil.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
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
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Iniciar actualizaciones de ubicación al conceder permisos
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.startLocationUpdates()
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Detener actualizaciones al salir de la pantalla
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLocationUpdates() }
    }

    // Seguir al usuario con la cámara
    LaunchedEffect(uiState.userLocation) {
        if (uiState.followUser) {
            uiState.userLocation?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        }
    }

    // Mover cámara al resultado de búsqueda
    LaunchedEffect(uiState.searchedMarker) {
        uiState.searchedMarker?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
        }
    }

    // Mostrar errores en Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Barra superior
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Toggle seguir usuario
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Seguir mi posición en el mapa",
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

                // Búsqueda de dirección
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    label = { Text("Buscar dirección") },
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

                // BONO: botón "Trazar ruta" — aparece cuando hay un destino seleccionado
                if (uiState.routeDestination != null) {
                    Button(
                        onClick = { viewModel.fetchRoute(uiState.routeDestination!!) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E3A6E),
                            contentColor = Color.White
                        ),
                        enabled = !uiState.isLoading && uiState.userLocation != null
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Calculando ruta…", fontSize = 14.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Directions,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Trazar ruta al destino", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Mapa principal
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
                // Marcador de ubicación actual
                uiState.userLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Mi ubicación"
                    )
                }

                // Polyline de recorrido (puntos de movimiento del usuario)
                if (uiState.routePoints.size >= 2) {
                    Polyline(
                        points = uiState.routePoints,
                        color = Color(0xFF5B4FC9),
                        width = 10f
                    )
                }

                // Marcador de dirección buscada
                uiState.searchedMarker?.let { pos ->
                    Marker(
                        state = MarkerState(position = pos),
                        title = uiState.searchedTitle,
                        snippet = uiState.searchedTitle
                    )
                }

                // Marcadores de long-click con dirección invertida
                uiState.longClickMarkers.forEach { (pos, address) ->
                    Marker(
                        state = MarkerState(position = pos),
                        title = address,
                        snippet = address
                    )
                }

                // BONO: Polyline de ruta trazada (Directions API)
                if (uiState.routePolyline.size >= 2) {
                    Polyline(
                        points = uiState.routePolyline,
                        color = Color(0xFF00C853),
                        width = 16f
                    )
                }
            }
        }
    }
}
