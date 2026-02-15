package com.voiceping.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.voiceping.android.data.location.LocationManager
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.data.storage.SettingsRepository
import com.voiceping.android.presentation.channels.ChannelListScreen
import com.voiceping.android.presentation.events.EventPickerScreen
import com.voiceping.android.presentation.loading.LoadingScreen
import com.voiceping.android.presentation.login.LoginScreen
import com.voiceping.android.presentation.login.LoginViewModel
import com.voiceping.android.presentation.permissions.PermissionEducationScreen
import com.voiceping.android.presentation.settings.DevStatsScreen
import com.voiceping.android.presentation.settings.SettingsScreen
import com.voiceping.android.data.network.SignalingClient
import kotlinx.coroutines.launch
import javax.inject.Inject

// Route constants
object Routes {
    const val PERMISSION_EDUCATION = "permission_education"
    const val LOGIN = "login"
    const val LOADING = "loading"
    const val EVENTS = "events"
    const val CHANNELS = "channels/{eventId}"
    const val SETTINGS = "settings"
    const val DEV_STATS = "dev-stats"

    fun channelsRoute(eventId: String) = "channels/$eventId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    loginViewModel: LoginViewModel = hiltViewModel(),
    preferencesManager: PreferencesManager,
    settingsRepository: SettingsRepository,
    signalingClient: SignalingClient,
    locationManager: LocationManager
) {
    val scope = rememberCoroutineScope()

    // Check if permission education has been shown
    val hasShownEducation by settingsRepository.hasShownPermissionEducation()
        .collectAsState(initial = true) // Default true to prevent flash

    // Determine start destination based on permission education, then auto-login
    val startDestination = if (!hasShownEducation) {
        Routes.PERMISSION_EDUCATION
    } else if (loginViewModel.checkAutoLogin()) {
        Routes.LOADING
    } else {
        Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.PERMISSION_EDUCATION) {
            PermissionEducationScreen(
                onComplete = {
                    scope.launch { settingsRepository.setPermissionEducationShown() }
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.PERMISSION_EDUCATION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    // After login, go to Loading which will:
                    // - re-establish session (already done by login)
                    // - check saved eventId → route to events or channels
                    navController.navigate(Routes.LOADING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOADING) {
            LoadingScreen(
                onConnected = { savedEventId ->
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
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.EVENTS) {
            EventPickerScreen(
                onEventSelected = { eventId ->
                    // Event selected and saved to prefs by EventPickerViewModel.
                    // Navigate to Loading which will get router token + connect WS.
                    navController.navigate(Routes.LOADING) {
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
                    // Clear saved event so Loading goes to event picker
                    preferencesManager.clearLastEventId()
                    navController.navigate(Routes.EVENTS) {
                        popUpTo(Routes.CHANNELS) { inclusive = true }
                    }
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.DEV_STATS) {
            DevStatsScreen(
                signalingClient = signalingClient,
                locationManager = locationManager,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
