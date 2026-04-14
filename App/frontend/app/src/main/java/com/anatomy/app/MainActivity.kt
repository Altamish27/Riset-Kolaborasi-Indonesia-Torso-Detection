package com.anatomy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.anatomy.app.helper.AudioAssistant
import com.anatomy.app.ui.screen.LoginScreen
import com.anatomy.app.ui.screen.MainPagerScreen
import com.anatomy.app.ui.theme.AnatomyAppTheme
import com.anatomy.app.utils.TokenManager
import com.anatomy.app.utils.UnifiedWebSocketManager
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect

/**
 * MainActivity — Single activity entry point.
 *
 * Handles runtime permission requests for CAMERA and RECORD_AUDIO,
 * then renders the Compose UI with AnatomyAppTheme.
 */
class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            try {
                if (AudioAssistant.isEnabled) {
                    AudioAssistant.speak(
                        "Beberapa izin belum diberikan. " +
                                "Aplikasi membutuhkan izin kamera dan mikrofon untuk berfungsi dengan baik."
                    )
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error speaking permission message", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions if not already granted
        requestPermissionsIfNeeded()

        setContent {
            AnatomyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Prepare API/auth helpers
                    val apiService = remember { HttpClientFactory.createApiService(this@MainActivity) }
                    val authRepository = remember { AuthRepository(apiService, this@MainActivity) }

                    // Check if user is logged in (has stored access token)
                    val isLoggedInStored = TokenManager.isLoggedIn(this@MainActivity)
                    val loginState = remember { mutableStateOf(!isLoggedInStored) }

                    // If there is a stored token, validate/refresh it before showing main UI
                    LaunchedEffect(isLoggedInStored) {
                        if (isLoggedInStored) {
                            val refreshResult = withContext(Dispatchers.IO) {
                                authRepository.refresh()
                            }
                            if (refreshResult.isSuccess) {
                                loginState.value = false
                            } else {
                                TokenManager.clearTokens(this@MainActivity)
                                loginState.value = true
                            }
                        }
                    }

                    // Connect WebSocket when loginState becomes authenticated
                    LaunchedEffect(loginState.value) {
                        if (!loginState.value) {
                            val token = TokenManager.getAccessToken(this@MainActivity)
                            if (!token.isNullOrBlank()) {
                                UnifiedWebSocketManager.connect(this@MainActivity, token)
                            }
                        }
                    }

                    if (loginState.value) {
                        LoginScreen(onLoginSuccess = {
                            loginState.value = false
                        })
                    } else {
                        MainPagerScreen()
                    }
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioAssistant.shutdown()
        UnifiedWebSocketManager.disconnect()
    }
}
