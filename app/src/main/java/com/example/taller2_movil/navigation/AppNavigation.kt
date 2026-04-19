package com.example.taller2_movil.navigation



import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taller2_movil.ui.screens.HomeScreen
import com.example.taller2_movil.ui.screens.MediaScreen
import com.example.taller2_movil.ui.screens.MapScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenCamera = { navController.navigate(Screen.Media.route) },
                onOpenMap = { navController.navigate(Screen.Map.route) }
            )
        }
        composable(Screen.Media.route) {
            MediaScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Map.route) {
            MapScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}