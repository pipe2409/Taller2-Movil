package com.example.taller2_movil.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onOpenMap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeButton(
                text = "Abrir Cámara",
                icon = Icons.Filled.CameraAlt,
                isPrimary = true,
                onClick = onOpenCamera
            )

            HomeButton(
                text = "Abrir Mapa",
                icon = Icons.Filled.Map,
                isPrimary = false,
                onClick = onOpenMap
            )
        }
    }
}

@Composable
fun HomeButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isPrimary) Color(0xFF1E3A6E) else Color.White
    val contentColor = if (isPrimary) Color.White else Color(0xFF1E3A6E)
    val borderColor = if (isPrimary) Color.Transparent else Color(0xFFDDDDDD)

    Button(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        border = if (!isPrimary) {
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        } else null,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isPrimary) 4.dp else 0.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}