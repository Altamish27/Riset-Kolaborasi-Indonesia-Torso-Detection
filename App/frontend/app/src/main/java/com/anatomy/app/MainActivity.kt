package com.anatomy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
            AudioAssistant.speak(
                "Beberapa izin belum diberikan. " +
                        "Aplikasi membutuhkan izin kamera dan mikrofon untuk berfungsi dengan baik."
            )
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
                    // Check if user is logged in
                    val isLoggedIn = TokenManager.isLoggedIn(this@MainActivity)
                    val loginState = remember { mutableStateOf(!isLoggedIn) }
                    
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
    }
}
