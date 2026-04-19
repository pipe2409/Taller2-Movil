package com.example.taller2_movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.taller2_movil.navigation.AppNavigation
import com.example.taller2_movil.ui.theme.Taller2MovilTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Taller2MovilTheme {
                AppNavigation()
            }
        }
    }
}