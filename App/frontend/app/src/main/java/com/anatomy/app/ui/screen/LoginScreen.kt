package com.anatomy.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.network.HttpClientFactory
import com.anatomy.app.repository.AuthRepository
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { HttpClientFactory.createApiService(context) }
    val authRepository = remember { AuthRepository(apiService, context) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF1a1a2e)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            if (isRegistering) "Create Account" else "Login",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonCyan,
            fontSize = 32.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        
        // Email (only for registration)
        if (isRegistering) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
        
        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(icon, "Toggle password visibility")
                }
            },
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        
        // Error message
        if (errorMessage.isNotEmpty()) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // Login/Register button
        Button(
            onClick = {
                if (isRegistering) {
                    // Handle Register
                    if (username.isBlank() || email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all fields"
                        return@Button
                    }
                    
                    if (!email.contains("@")) {
                        errorMessage = "Invalid email format"
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = ""
                    
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            authRepository.register(username, email, password)
                        }
                        
                        result.onSuccess {
                            isLoading = false
                            errorMessage = "Registration successful! Please login."
                            isRegistering = false
                            username = ""
                            email = ""
                            password = ""
                        }.onFailure { error ->
                            isLoading = false
                            errorMessage = error.message ?: "Registration failed"
                        }
                    }
                } else {
                    // Handle Login
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all fields"
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = ""
                    
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            authRepository.login(username, password)
                        }
                        
                        result.onSuccess {
                            isLoading = false
                            onLoginSuccess()
                        }.onFailure { error ->
                            isLoading = false
                            errorMessage = error.message ?: "Login failed"
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 16.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen,
                disabledContainerColor = NeonGreen.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (isRegistering) "Create Account" else "Login",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        // Toggle between login and register
        TextButton(
            onClick = { 
                isRegistering = !isRegistering
                errorMessage = ""
            },
            enabled = !isLoading
        ) {
            Text(
                if (isRegistering) "Already have an account? Login" else "Create a new account",
                color = NeonCyan
            )
        }
    }
}
