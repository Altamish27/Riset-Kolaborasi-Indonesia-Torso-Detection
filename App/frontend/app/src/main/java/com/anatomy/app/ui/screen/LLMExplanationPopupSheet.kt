package com.anatomy.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.SurfaceCard
import com.anatomy.app.ui.theme.SurfaceDark

/**
 * LLM Explanation Popup Sheet - Displays AI-generated organ explanations
 *
 * Features:
 *   - Dark translucent neon background with rounded top corners
 *   - Organ name as headline
 *   - Scrollable LLM-generated explanation text
 *   - Slide-up animation (built into ModalBottomSheet)
 *
 * @param organName The name of the detected organ
 * @param explanation The AI-generated explanation text from LLM backend
 * @param onDismiss Called when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMExplanationPopupSheet(
    organName: String,
    explanation: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceCard.copy(alpha = 0.98f),
                            SurfaceDark.copy(alpha = 0.99f)
                        )
                    )
                )
                .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 40.dp)
                .semantics { contentDescription = "Popup penjelasan organ dari AI" },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Organ name - centered, large, neon color
            Text(
                text = organName,
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(2.dp)
                    .background(color = NeonCyan.copy(alpha = 0.4f))
                    .clip(RoundedCornerShape(1.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Scrollable explanation text
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Justify,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .semantics { 
                        contentDescription = "Penjelasan organ: $explanation"
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // AI-generated badge
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color = NeonCyan.copy(alpha = 0.15f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✨ Penjelasan dari AI Backend",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
