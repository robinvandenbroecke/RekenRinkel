package com.rekenrinkel.ui.components

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
            .height(48.dp)
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
        // Display — compact
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentValue.ifEmpty { "?" },
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Number grid — compact rows
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
            Spacer(modifier = Modifier.height(2.dp))
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
            .padding(2.dp)
            .height(48.dp),
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
fun MissingVisualFallback(
    message: String = "Visuele inhoud ontbreekt",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun VisualGroups(
    groups: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    // For many or large groups, use a compact representation
    val totalDots = groups.sumOf { it }
    if (totalDots > 20 || groups.size > 4) {
        // Compact: show "N groepjes van M" with a small sample
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show up to 3 sample groups as small dot clusters
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                val sampleSize = minOf(3, groups.size)
                groups.take(sampleSize).forEach { count ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Show max 5 dots per group sample
                        VisualDots(count = minOf(count, 5), color = color)
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                if (groups.size > 3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("…", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${groups.size} groepjes van ${groups.firstOrNull() ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    } else {
        // Normal: show all groups
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

/**
 * DotGrid — uses dice-like patterns for 1-6, grid for larger numbers.
 * Max height constrained to 200dp.
 */
@Composable
fun DotGrid(
    count: Int,
    maxPerRow: Int = 5,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(modifier = modifier.heightIn(max = 200.dp)) {
        if (count in 1..6) {
            // Dice-like patterns
            val positions = when (count) {
                1 -> listOf(listOf(1))
                2 -> listOf(listOf(1, 0, 1))
                3 -> listOf(listOf(1, 0, 0), listOf(0, 1, 0), listOf(0, 0, 1))
                4 -> listOf(listOf(1, 0, 1), listOf(0, 0, 0), listOf(1, 0, 1))
                5 -> listOf(listOf(1, 0, 1), listOf(0, 1, 0), listOf(1, 0, 1))
                6 -> listOf(listOf(1, 0, 1), listOf(1, 0, 1), listOf(1, 0, 1))
                else -> emptyList()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                positions.forEach { row ->
                    Row {
                        row.forEach { filled ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (filled == 1) color else Color.Transparent)
                            )
                        }
                    }
                }
            }
        } else {
            // Grid layout for larger numbers
            val rows = (count + maxPerRow - 1) / maxPerRow
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(rows) { row ->
                    Row {
                        val start = row * maxPerRow
                        val end = minOf(start + maxPerRow, count)
                        repeat(end - start) {
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * BlockBar — colored blocks (filled/empty), max width constrained.
 */
@Composable
fun BlockBar(
    count: Int,
    total: Int,
    modifier: Modifier = Modifier,
    filledColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
) {
    Row(
        modifier = modifier.heightIn(max = 200.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        val blockSize = if (total <= 10) 24.dp else 16.dp
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(blockSize)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index < count) filledColor else emptyColor)
            )
        }
    }
}

/**
 * BondModel — part-part-whole circle diagram.
 */
@Composable
fun BondModel(
    whole: Int,
    part1: Int,
    part2: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier.heightIn(max = 200.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Whole circle at top
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = whole.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Two part circles below
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            listOf(part1, part2).forEach { part ->
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = part.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * GroupsDisplay — shows groups with count label. Max 4 visual groups,
 * then falls back to "N groepjes van M" text.
 */
@Composable
fun GroupsDisplay(
    groupCount: Int,
    perGroup: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier.heightIn(max = 200.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (groupCount <= 4 && perGroup <= 6) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(groupCount) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DotGrid(count = perGroup, color = color)
                        Text(
                            text = perGroup.toString(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        } else {
            // Text fallback for large numbers
            Text(
                text = "$groupCount groepjes van $perGroup",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$groupCount × $perGroup = ${groupCount * perGroup}",
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = 0.7f)
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
    // Kept for API compat — feedback is now inline in ExerciseScreen.
    // No button, no overlay blocking. Auto-advance handled by ViewModel.
}