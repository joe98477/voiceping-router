package com.voiceping.android.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.presentation.loading.LoadingScreen
import com.voiceping.android.presentation.login.LoginScreen
import com.voiceping.android.presentation.login.LoginViewModel

// Route constants
object Routes {
    const val LOGIN = "login"
    const val LOADING = "loading"
    const val EVENTS = "events"
    const val CHANNELS = "channels"
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
                onConnected = {
                    // Check if saved event exists - navigate accordingly
                    val savedEventId = preferencesManager.getLastEventId()
                    val destination = if (savedEventId != null) {
                        Routes.CHANNELS
                    } else {
                        Routes.EVENTS
                    }

                    navController.navigate(destination) {
                        popUpTo(Routes.LOADING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.EVENTS) {
            EventPickerScreen()
        }

        composable(Routes.CHANNELS) {
            ChannelListScreen()
        }
    }
}

// Placeholder screens - will be implemented in Plans 04-05

@Composable
fun EventPickerScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Event Picker (Placeholder)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ChannelListScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Channel List (Placeholder)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
