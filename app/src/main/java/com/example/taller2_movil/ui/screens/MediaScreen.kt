package com.example.taller2_movil.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.taller2_movil.viewmodel.MediaMode
import com.example.taller2_movil.viewmodel.MediaViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaScreen(
    onBack: () -> Unit,
    viewModel: MediaViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // --- Permisos ---
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // --- Launchers ---

    // Tomar foto
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.onCameraResult(success)
    }

    // Grabar video
    val takeVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        viewModel.onCameraResult(success)
    }

    // Seleccionar de galería
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onGalleryResult(uri)
    }

    // --- Helper para crear URI temporal de cámara ---
    fun createTempUri(isPhoto: Boolean): Uri {
        val ext = if (isPhoto) ".jpg" else ".mp4"
        val file = File.createTempFile(
            "media_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}",
            ext,
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val isPhotoMode = uiState.mode == MediaMode.PHOTO

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // Toggle Foto / Video
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Foto",
                fontSize = 15.sp,
                fontWeight = if (isPhotoMode) FontWeight.Bold else FontWeight.Normal,
                color = if (isPhotoMode) Color(0xFF1E3A6E) else Color.Gray
            )
            Switch(
                checked = !isPhotoMode,
                onCheckedChange = { 
                    viewModel.setMode(if (it) MediaMode.VIDEO else MediaMode.PHOTO) 
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1E3A6E),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF1E3A6E)
                )
            )
            Text(
                text = "Video",
                fontSize = 15.sp,
                fontWeight = if (!isPhotoMode) FontWeight.Bold else FontWeight.Normal,
                color = if (!isPhotoMode) Color(0xFF1E3A6E) else Color.Gray
            )
        }

        // Área de previsualización
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.mediaUri == null -> {
                    Text(
                        text = if (isPhotoMode) "Sin foto" else "Sin video",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                isPhotoMode -> {
                    AsyncImage(
                        model = uiState.mediaUri,
                        contentDescription = "Foto seleccionada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    VideoPlayer(
                        uri = uiState.mediaUri!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Botones de acción
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Botón Tomar Foto / Grabar Video
            Button(
                onClick = {
                    if (cameraPermission.status.isGranted) {
                        val uri = createTempUri(isPhotoMode)
                        viewModel.setTempCameraUri(uri)
                        if (isPhotoMode) {
                            takePhotoLauncher.launch(uri)
                        } else {
                            takeVideoLauncher.launch(uri)
                        }
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A6E),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isPhotoMode) "Tomar Foto" else "Grabar Video",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Botón Seleccionar de Galería
            Button(
                onClick = {
                    val mimeType = if (isPhotoMode) "image/*" else "video/*"
                    pickMediaLauncher.launch(mimeType)
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A6E),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isPhotoMode) "Seleccionar Foto" else "Seleccionar Video",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- Componente VideoPlayer ---
@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}
