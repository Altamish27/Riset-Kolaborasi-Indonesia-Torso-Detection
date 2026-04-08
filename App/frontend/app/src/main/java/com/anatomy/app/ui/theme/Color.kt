package com.anatomy.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Deep Black Backgrounds ───
val BackgroundPitch = Color(0xFF000000)       // Pure black (OLED)
val SurfaceDark = Color(0xFF0A0A0A)
val SurfaceMedium = Color(0xFF141414)
val SurfaceLight = Color(0xFF1E1E1E)
val SurfaceCard = Color(0xFF1A1A2E)           // Slightly blue-tinted card

// ─── Neon Accent Palette ───
val NeonCyan = Color(0xFF00E5FF)              // Primary — active scan, links
val NeonAmber = Color(0xFFFFD600)             // Warning — listening, detection prompt
val NeonGreen = Color(0xFF76FF03)             // Success — confirmed detection
val NeonMagenta = Color(0xFFFF1744)           // Error / danger
val NeonBlue = Color(0xFF2979FF)              // Info / secondary
val NeonPurple = Color(0xFFBB86FC)            // Tertiary accent

// ─── Text ───
val TextPrimary = Color(0xFFFFFFFF)           // Maximum contrast
val TextSecondary = Color(0xFFB0BEC5)         // Muted descriptions
val TextOnNeon = Color(0xFF000000)            // Black text on neon backgrounds

// ─── Scanning / Overlay ───
val ScanWaveColor = Color(0xFF00E5FF)         // Cyan scanning line
val BoundingBoxColor = Color(0xFF76FF03)      // Neon green bounding box
val BoundingBoxGlow = Color(0x6676FF03)       // Semi-transparent glow

// ─── FAB ───
val FabVoiceOn = Color(0xFF00E5FF)            // Cyan — voice guidance active
val FabTextOnly = Color(0xFFFFD600)           // Amber — text-only mode
val FabVoiceOff = Color(0xFF424242)           // Gray — disabled (not used currently)

// ─── Mic States ───
val MicActive = Color(0xFFFF1744)             // Red pulse when listening
val MicIdle = Color(0xFF37474F)               // Dark gray when idle
