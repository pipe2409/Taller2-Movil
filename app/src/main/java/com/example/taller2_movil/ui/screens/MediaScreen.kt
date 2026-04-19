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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // true = Foto, false = Video
    var isPhotoMode by remember { mutableStateOf(true) }

    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // --- Permisos ---
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // --- Launchers ---

    // Tomar foto
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) mediaUri = tempCameraUri
    }

    // Grabar video
    val takeVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) mediaUri = tempCameraUri
    }

    // Seleccionar foto de galería
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { mediaUri = it } }

    // Seleccionar video de galería
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { mediaUri = it } }

    // --- Helpers para crear URI temporal de cámara ---
    fun createTempUri(isPhoto: Boolean): Uri {
        val ext = if (isPhoto) ".jpg" else ".mp4"
        val file = File.createTempFile(
            "media_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}",
            ext,
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Cuando cambia el modo, limpiar media anterior
    LaunchedEffect(isPhotoMode) { mediaUri = null }

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .windowInsetsPadding(WindowInsets.systemBars) // <--- ESTO SOLUCIONA LA OBSTRUCCIÓN
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
                onCheckedChange = { isPhotoMode = !it },
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
                mediaUri == null -> {
                    Text(
                        text = if (isPhotoMode) "Sin foto" else "Sin video",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                isPhotoMode -> {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = "Foto seleccionada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    VideoPlayer(
                        uri = mediaUri!!,
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
                        tempCameraUri = createTempUri(isPhotoMode)
                        if (isPhotoMode) {
                            takePhotoLauncher.launch(tempCameraUri!!)
                        } else {
                            takeVideoLauncher.launch(tempCameraUri!!)
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
                    if (isPhotoMode) {
                        pickPhotoLauncher.launch("image/*")
                    } else {
                        pickVideoLauncher.launch("video/*")
                    }
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
