package com.anatomy.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.anatomy.app.ui.theme.NeonAmber
import com.anatomy.app.ui.theme.NeonCyan
import com.anatomy.app.ui.theme.NeonGreen
import com.anatomy.app.ui.theme.NeonMagenta
import com.anatomy.app.ui.theme.SurfaceCard
import com.anatomy.app.viewmodel.QuizStatus
import com.anatomy.app.viewmodel.QuizViewModel

@Composable
fun QuizScreen(
    isActive: Boolean = true,
    quizViewModel: QuizViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by quizViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        quizViewModel.observePendingQuiz(coroutineScope)
    }

    val stateColor = when (uiState.status) {
        QuizStatus.IDLE -> NeonCyan
        QuizStatus.PLAYING -> NeonCyan
        QuizStatus.FEEDBACK -> if (uiState.isCorrect == true) NeonGreen else NeonMagenta
        QuizStatus.FINISHED -> NeonGreen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        SurfaceCard.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { contentDescription = "Halaman Mode Quiz" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Mode Quiz",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonGreen,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.height(8.dp))

        Text(
            uiState.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = stateColor
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .background(SurfaceCard.copy(alpha = 0.55f))
                .padding(18.dp)
        ) {
            when (uiState.status) {
                QuizStatus.IDLE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.QuestionMark, null, tint = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isActive) {
                                "Menunggu kuis dari percakapan QnA."
                            } else {
                                "Kuis akan aktif saat halaman dibuka."
                            },
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                QuizStatus.PLAYING -> {
                    val question = uiState.currentQuestion
                    if (question == null) {
                        Text("Pertanyaan tidak tersedia", color = Color.White)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QuestionMark, null, tint = NeonCyan)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Soal ${uiState.currentIndex + 1}/${uiState.totalQuestions}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Text(
                                question.question_text,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                lineHeight = 26.sp
                            )

                            question.answer_options.forEachIndexed { index, option ->
                                val letter = ('A' + index)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceCard.copy(alpha = 0.45f))
                                        .border(
                                            width = 1.dp,
                                            color = NeonCyan.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { quizViewModel.selectAnswer(index) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        "$letter. $option",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                QuizStatus.FEEDBACK -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isCorrect == true) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.QuestionMark
                            },
                            contentDescription = null,
                            tint = stateColor
                        )

                        Text(
                            uiState.feedbackText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )

                        Button(
                            onClick = { quizViewModel.nextQuestion() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Lanjut")
                        }
                    }
                }

                QuizStatus.FINISHED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, tint = NeonAmber)
                        Text(
                            "Skor ${uiState.score}/${uiState.totalQuestions}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.feedbackText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                        Button(
                            onClick = { quizViewModel.resetQuiz() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Selesai")
                        }
                    }
                }
            }
        }
    }
}
