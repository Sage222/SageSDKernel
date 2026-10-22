package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DeviceHardwareEnvironment
import com.example.domain.KalamaCluster
import com.example.domain.OppFrequencyPoint
import com.example.domain.OppPointMode
import com.example.ui.theme.KalamaAmber
import com.example.ui.theme.KalamaCrimson
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun KalamaTopTelemetryBar(
    environment: DeviceHardwareEnvironment?,
    statusMessage: String,
    isError: Boolean,
    onToggleSandboxMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHardwareRoot = environment?.isHardwareBlockAccessible == true
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D172A), ObsidianBg)
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10243E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeveloperBoard,
                        contentDescription = "Kalama SoC",
                        tint = KalamaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "KALAMA FORGE • SM8550-AC",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = environment?.detectedDeviceCode ?: "Samsung Snapdragon 8 Gen 2",
                        style = MaterialTheme.typography.labelMedium,
                        color = KalamaCyan
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isHardwareRoot) Color(0xFF063420) else Color(0xFF2B2108),
                border = BorderStroke(
                    1.dp,
                    if (isHardwareRoot) KalamaEmerald else KalamaAmber
                ),
                modifier = Modifier
                    .testTag("toggle_execution_mode_badge")
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onToggleSandboxMode() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isHardwareRoot) KalamaEmerald else KalamaAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHardwareRoot) "ROOT UID 0" else "SANDBOX SIM",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHardwareRoot) KalamaEmerald else KalamaAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live status strip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isError) Color(0xFF360B11) else ObsidianSurfaceVariant,
            border = BorderStroke(
                1.dp,
                if (isError) KalamaCrimson.copy(alpha = 0.7f) else ObsidianCardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_banner_surface")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = "Status Indicator",
                    tint = if (isError) KalamaCrimson else KalamaEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isError) Color(0xFFFFB4B4) else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun KalamaSiliconDieHeroCard(
    oppPoints: List<OppFrequencyPoint>,
    globalVoltageOffsetMv: Int,
    selectedCluster: KalamaCluster,
    onSelectCluster: (KalamaCluster) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledPoints = remember(oppPoints) { oppPoints.filter { it.enabled } }
    val minSilverMhz = remember(enabledPoints) {
        (enabledPoints.filter { it.cluster == KalamaCluster.SILVER }.minOfOrNull { it.freqKhz } ?: 307_200L) / 1000.0
    }
    val maxPrimeGhz = remember(enabledPoints) {
        (enabledPoints.filter { it.cluster == KalamaCluster.PRIME }.maxOfOrNull { it.freqKhz } ?: 3_360_000L) / 1_000_000.0
    }
    val maxGpuMhz = remember(enabledPoints) {
        (enabledPoints.filter { it.cluster == KalamaCluster.GPU_ADRENO740 }.maxOfOrNull { it.freqKhz } ?: 719_000L) / 1000L
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SM8550-AC SILICON TOPOLOGY & OPP TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = KalamaCyan
                    )
                    Text(
                        text = "1x Cortex-X3 + 4x Gold + 3x Silver + Adreno 740",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = Color(0xFF132642),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, KalamaCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "dV ${if (globalVoltageOffsetMv >= 0) "+$globalVoltageOffsetMv" else "$globalVoltageOffsetMv"} mV",
                        style = MaterialTheme.typography.labelMedium,
                        color = KalamaCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Canvas Frequency-Voltage Curve & Die Layout
            val clusterPoints = remember(oppPoints, selectedCluster) {
                oppPoints.filter { it.cluster == selectedCluster }.sortedBy { it.freqKhz }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF080E1A))
                    .padding(10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(98.dp)) {
                    val w = size.width
                    val h = size.height

                    // Subtle technical grid
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = h * (i.toFloat() / gridLines)
                        drawLine(
                            color = Color(0xFF1A2742),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    if (clusterPoints.isNotEmpty()) {
                        val minFreq = clusterPoints.minOf { it.freqKhz }.toFloat()
                        val maxFreq = clusterPoints.maxOf { it.freqKhz }.toFloat().coerceAtLeast(minFreq + 1f)
                        val minVolt = 500f
                        val maxVolt = 1250f

                        val path = Path()
                        var firstActive = true

                        clusterPoints.forEach { pt ->
                            val xRatio = (pt.freqKhz - minFreq) / (maxFreq - minFreq)
                            val effMv = pt.effectiveVoltageMv(globalVoltageOffsetMv).toFloat()
                            val yRatio = 1f - ((effMv - minVolt) / (maxVolt - minVolt)).coerceIn(0f, 1f)

                            val cx = 16f + xRatio * (w - 32f)
                            val cy = 12f + yRatio * (h - 24f)

                            if (pt.enabled) {
                                if (firstActive) {
                                    path.moveTo(cx, cy)
                                    firstActive = false
                                } else {
                                    path.lineTo(cx, cy)
                                }
                            }
                        }

                        drawPath(
                            path = path,
                            color = KalamaCyan,
                            style = Stroke(width = 3.5f)
                        )

                        clusterPoints.forEach { pt ->
                            val xRatio = (pt.freqKhz - minFreq) / (maxFreq - minFreq)
                            val effMv = pt.effectiveVoltageMv(globalVoltageOffsetMv).toFloat()
                            val yRatio = 1f - ((effMv - minVolt) / (maxVolt - minVolt)).coerceIn(0f, 1f)
                            val cx = 16f + xRatio * (w - 32f)
                            val cy = 12f + yRatio * (h - 24f)

                            val dotColor = when {
                                !pt.enabled -> Color(0xFF334155)
                                pt.mode == OppPointMode.UNDERCLOCK -> KalamaEmerald
                                pt.mode == OppPointMode.OVERCLOCK -> KalamaAmber
                                else -> KalamaCyan
                            }

                            drawCircle(
                                color = dotColor,
                                radius = if (pt.enabled) 6.5f else 4f,
                                center = Offset(cx, cy)
                            )
                            if (pt.enabled && pt.mode != OppPointMode.STOCK) {
                                drawCircle(
                                    color = dotColor.copy(alpha = 0.28f),
                                    radius = 13f,
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }

                    // Draw miniature SM8550 die watermark in top-left
                    drawRoundRect(
                        color = Color(0xFF162544),
                        topLeft = Offset(6f, 4f),
                        size = Size(54f, 34f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 1.5f)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendDot(color = KalamaEmerald, label = "Underclock Floor: ${String.format("%.1f", minSilverMhz)} MHz")
                    LegendDot(color = KalamaCyan, label = "Stock Curve")
                    LegendDot(color = KalamaAmber, label = "Overclock Peak: ${String.format("%.2f", maxPrimeGhz)} GHz / ${maxGpuMhz} MHz")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Cluster Interactive Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KalamaCluster.entries.forEach { cluster ->
                    val isSelected = cluster == selectedCluster
                    val shortName = when (cluster) {
                        KalamaCluster.SILVER -> "Silver A510"
                        KalamaCluster.GOLD -> "Gold A715"
                        KalamaCluster.PRIME -> "Prime X3"
                        KalamaCluster.GPU_ADRENO740 -> "Adreno 740"
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_cluster_chip_${cluster.name.lowercase()}")
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectCluster(cluster) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF12314C) else ObsidianSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) KalamaCyan else ObsidianCardBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = shortName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) KalamaCyan else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
fun KernelShellConsoleDrawer(
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF060911)),
        border = BorderStroke(1.dp, ObsidianCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("root_terminal_drawer")
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = "Root Shell Output",
                        tint = KalamaCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lines.lastOrNull() ?: "[root@kalama]# ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = KalamaEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse console" else "Expand console",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    lines.takeLast(10).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (line.contains("FAIL") || line.contains("FAULT")) {
                                KalamaCrimson
                            } else if (line.contains("PASS") || line.contains("COMPLETE")) {
                                KalamaEmerald
                            } else {
                                TextSecondary
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
