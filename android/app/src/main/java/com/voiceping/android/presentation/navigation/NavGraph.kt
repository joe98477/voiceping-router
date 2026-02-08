package com.voiceping.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.presentation.channels.ChannelListScreen
import com.voiceping.android.presentation.events.EventPickerScreen
import com.voiceping.android.presentation.loading.LoadingScreen
import com.voiceping.android.presentation.login.LoginScreen
import com.voiceping.android.presentation.login.LoginViewModel

// Route constants
object Routes {
    const val LOGIN = "login"
    const val LOADING = "loading"
    const val EVENTS = "events"
    const val CHANNELS = "channels/{eventId}"

    fun channelsRoute(eventId: String) = "channels/$eventId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    loginViewModel: LoginViewModel = hiltViewModel(),
    preferencesManager: PreferencesManager
) {
    // Determine start destination based on auto-login
    val startDestination = if (loginViewModel.checkAutoLogin()) {
        Routes.LOADING
    } else {
        Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.LOADING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOADING) {
            LoadingScreen(
                onConnected = { savedEventId ->
                    // Navigate based on saved event from LoadingViewModel
                    val destination = if (savedEventId != null) {
                        Routes.channelsRoute(savedEventId)
                    } else {
                        Routes.EVENTS
                    }

                    navController.navigate(destination) {
                        popUpTo(Routes.LOADING) { inclusive = true }
                    }
                },
                onLogout = {
                    // Clear tokens and navigate to login
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.EVENTS) {
            EventPickerScreen(
                onEventSelected = { eventId ->
                    navController.navigate(Routes.channelsRoute(eventId)) {
                        popUpTo(Routes.EVENTS) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CHANNELS,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType }
            )
        ) {
            ChannelListScreen(
                onSwitchEvent = {
                    navController.navigate(Routes.EVENTS) {
                        popUpTo(Routes.CHANNELS) { inclusive = true }
                    }
                },
                onSettings = {
                    // TODO: Navigate to settings screen in future phase
                },
                onLogout = {
                    // TODO: Clear tokens and navigate to login
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
