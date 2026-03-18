package com.rekenrinkel.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekenrinkel.ui.theme.AppColors

@Composable
fun ProgressBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = current.toFloat() / total.coerceAtLeast(1)
    
    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AnswerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isCorrect: Boolean? = null,
    enabled: Boolean = true
) {
    val backgroundColor = when {
        isCorrect == true -> AppColors.Correct
        isCorrect == false -> AppColors.Incorrect
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val borderColor = when {
        isCorrect == true -> AppColors.Correct
        isCorrect == false -> AppColors.Incorrect
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isCorrect != null) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NumberPad(
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    currentValue: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentValue.ifEmpty { "?" },
                style = MaterialTheme.typography.displayMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Number grid
        val numbers = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "✓")
        )
        
        numbers.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { num ->
                    NumberButton(
                        text = num,
                        onClick = {
                            when (num) {
                                "C" -> onBackspace()
                                "✓" -> onConfirm()
                                else -> onNumberClick(num)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAction = text == "C" || text == "✓"
    
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isAction) 
                MaterialTheme.colorScheme.secondary 
            else 
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = if (isAction) 
                MaterialTheme.colorScheme.onSecondary 
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VisualDots(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val rows = (count + 4) / 5
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(rows) { row ->
            Row {
                val start = row * 5
                val end = minOf(start + 5, count)
                repeat(end - start) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

/**
 * PATCH 3: VisualBlocks component voor blokjes representatie
 * Eenvoudige vierkantjes met hoog contrast voor kinderoefeningen
 */
@Composable
fun VisualBlocks(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val rows = (count + 4) / 5
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(rows) { row ->
            Row {
                val start = row * 5
                val end = minOf(start + 5, count)
                repeat(end - start) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
fun VisualGroups(
    groups: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        groups.forEach { count ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VisualDots(
                    count = count,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun StarDisplay(
    stars: Int,
    maxStars: Int = 3,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        repeat(maxStars) { index ->
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (index < stars) AppColors.Accent else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun FeedbackOverlay(
    isCorrect: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(32.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCorrect) "🎉 Goed gedaan!" else "❌ Bijna!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isCorrect) AppColors.Correct else AppColors.Incorrect
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = onDismiss) {
                        Text("Volgende")
                    }
                }
            }
        }
    }
}