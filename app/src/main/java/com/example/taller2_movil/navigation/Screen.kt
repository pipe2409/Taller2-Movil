package com.example.taller2_movil.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Media : Screen("media")
    object Map : Screen("map")
}