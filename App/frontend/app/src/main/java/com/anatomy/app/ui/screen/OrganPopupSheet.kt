package com.anatomy.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anatomy.app.data.OrganEntity
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.NeonPurple
import com.anatomy.app.ui.theme.SurfaceCard
import com.anatomy.app.ui.theme.SurfaceDark

/**
 * OrganPopupSheet — IG Reels-style ModalBottomSheet for organ details.
 *
 * Features:
 *   - Dark translucent neon background with rounded top corners
 *   - Centered placeholder organ icon/image with gradient overlay
 *   - Organ name headline + short description subtitle
 *   - Scrollable long_description body
 *   - Slide-up animation (built into ModalBottomSheet)
 *
 * @param organ The OrganEntity to display.
 * @param detectedLabel The original detected object label (e.g., "Bottle").
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganPopupSheet(
    organ: OrganEntity,
    detectedLabel: String = "",
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Map organ names to representative emoji/icon colors for visual distinction
    val organColor = when (organ.name) {
        "Jantung" -> Color(0xFFFF1744)
        "Paru-paru" -> NeonCyan
        "Hati" -> NeonAmber
        "Lambung" -> NeonGreen
        "Usus" -> NeonPurple
        "Ginjal" -> Color(0xFFFF6D00)
        "Sistem Syaraf" -> Color(0xFF00E5FF)
        else -> NeonCyan
    }

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
                .padding(bottom = 32.dp)
                .semantics { contentDescription = "Detail organ ${organ.name}" },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Drag handle ───
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Placeholder organ image ───
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                organColor.copy(alpha = 0.3f),
                                organColor.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .semantics {
                        contentDescription = "Gambar organ ${organ.name}"
                    },
                contentAlignment = Alignment.Center
            ) {
                // Inner icon container
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(organColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (organ.name == "Jantung") Icons.Default.Favorite
                        else Icons.Default.LocalHospital,
                        contentDescription = null,
                        tint = organColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Detection badge ───
            if (detectedLabel.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(organColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(organColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Terdeteksi: $detectedLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = organColor,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ─── Organ name ───
            Text(
                text = organ.name,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .semantics { heading() }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ─── Short description ───
            Text(
                text = organ.short_description,
                style = MaterialTheme.typography.bodyLarge,
                color = organColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Divider ───
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                organColor.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Long description (scrollable) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .semantics {
                        contentDescription = "Penjelasan lengkap: ${organ.long_description}"
                    }
            ) {
                Text(
                    text = organ.long_description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Start,
                    lineHeight = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Swipe hint ───
            Text(
                text = "Geser ke bawah untuk menutup",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp
            )
        }
    }
}
