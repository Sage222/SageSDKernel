package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.KalamaCluster
import com.example.domain.OppFrequencyPoint
import com.example.domain.OppPointMode
import com.example.ui.theme.KalamaAmber
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SimpleFrequenciesScreen(
    selectedCluster: KalamaCluster,
    oppPoints: List<OppFrequencyPoint>,
    onSelectCluster: (KalamaCluster) -> Unit,
    onAdjustVoltage: (String, Int) -> Unit,
    onAdjustFrequency: (String, Int) -> Unit,
    onShiftClusterVoltages: (KalamaCluster, Int) -> Unit,
    onResetPointToStock: (String) -> Unit,
    onResetClusterToStock: (KalamaCluster) -> Unit,
    onAddCustomStep: (KalamaCluster, Int, Int) -> Unit,
    onRemoveCustomStep: (String) -> Unit,
    onToggleStepEnabled: (String) -> Unit,
    onGoToExportAndFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clusterPoints = remember(oppPoints, selectedCluster) {
        oppPoints.filter { it.cluster == selectedCluster }.sortedBy { it.freqMhz }
    }
    val enabledClusterPoints = remember(clusterPoints) {
        clusterPoints.filter { it.enabled }
    }
    val currentMinMhz = enabledClusterPoints.minOfOrNull { it.freqMhz } ?: selectedCluster.stockMinMhz
    val currentMaxMhz = enabledClusterPoints.maxOfOrNull { it.freqMhz } ?: selectedCluster.stockMaxMhz

    var newFreqText by remember(selectedCluster) {
        mutableStateOf(selectedCluster.suggestedOverclockMhz.toString())
    }
    var newVoltText by remember(selectedCluster) {
        mutableStateOf(selectedCluster.suggestedOverclockMv.toString())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Cluster Selector Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KalamaCluster.entries.forEach { cluster ->
                val isSelected = cluster == selectedCluster
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cluster_tab_${cluster.name.lowercase()}")
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCluster(cluster) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF102A45) else ObsidianSurface,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) KalamaCyan else ObsidianCardBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = cluster.shortName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) KalamaCyan else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${cluster.stockMaxMhz} MHz",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Stock vs. Modified Overview Card + Independent Cluster Voltage Shift
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedCluster.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedCluster.coresDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = { onResetClusterToStock(selectedCluster) },
                        border = BorderStroke(1.dp, ObsidianCardBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("reset_cluster_stock_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Cluster to Stock",
                            tint = KalamaCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Stock",
                            style = MaterialTheme.typography.labelSmall,
                            color = KalamaCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Clear Side-by-Side Stock Range vs Your Active Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceVariant,
                        border = BorderStroke(1.dp, ObsidianCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "STOCK FREQUENCIES",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedCluster.stockMinMhz} – ${selectedCluster.stockMaxMhz} MHz",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceVariant,
                        border = BorderStroke(1.dp, KalamaCyan.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "YOUR ACTIVE RANGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = KalamaCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$currentMinMhz – $currentMaxMhz MHz",
                                style = MaterialTheme.typography.labelLarge,
                                color = KalamaCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Independent Voltage Shift for All Steps in Cluster
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "All Voltages in Cluster:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onShiftClusterVoltages(selectedCluster, -10) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, KalamaEmerald),
                            modifier = Modifier.testTag("cluster_uv_minus_10_btn")
                        ) {
                            Text(
                                text = "-10 mV All",
                                style = MaterialTheme.typography.labelMedium,
                                color = KalamaEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { onShiftClusterVoltages(selectedCluster, +10) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, KalamaAmber),
                            modifier = Modifier.testTag("cluster_ov_plus_10_btn")
                        ) {
                            Text(
                                text = "+10 mV All",
                                style = MaterialTheme.typography.labelMedium,
                                color = KalamaAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. Easy "Add Underclock / Overclock Step" Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ADD NEW OVERCLOCK OR UNDERCLOCK STEP",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap a preset below or enter any Frequency (MHz) and Voltage (mV)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // One-tap pre-fill / quick add buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val nextUcMhz = (currentMinMhz - 75).coerceAtLeast(120)
                            val nextUcMv = (enabledClusterPoints.minOfOrNull { it.voltageMv } ?: selectedCluster.suggestedUnderclockMv) - 15
                            newFreqText = nextUcMhz.toString()
                            newVoltText = nextUcMv.coerceAtLeast(520).toString()
                            onAddCustomStep(selectedCluster, nextUcMhz, nextUcMv.coerceAtLeast(520))
                        },
                        border = BorderStroke(1.dp, KalamaEmerald),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quick_add_underclock_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Add Underclock Step",
                            tint = KalamaEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Underclock Step",
                            style = MaterialTheme.typography.labelMedium,
                            color = KalamaEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val nextOcMhz = (currentMaxMhz + 120).coerceAtMost(3950)
                            val nextOcMv = (enabledClusterPoints.maxOfOrNull { it.voltageMv } ?: selectedCluster.suggestedOverclockMv) + 25
                            newFreqText = nextOcMhz.toString()
                            newVoltText = nextOcMv.coerceAtMost(1280).toString()
                            onAddCustomStep(selectedCluster, nextOcMhz, nextOcMv.coerceAtMost(1280))
                        },
                        border = BorderStroke(1.dp, KalamaAmber),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quick_add_overclock_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Add Overclock Step",
                            tint = KalamaAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Overclock Step",
                            style = MaterialTheme.typography.labelMedium,
                            color = KalamaAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFreqText,
                        onValueChange = { newFreqText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Freq (MHz)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_freq_mhz_input")
                    )

                    OutlinedTextField(
                        value = newVoltText,
                        onValueChange = { newVoltText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Voltage (mV)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_volt_mv_input")
                    )

                    Button(
                        onClick = {
                            val mhz = newFreqText.toIntOrNull() ?: selectedCluster.suggestedOverclockMhz
                            val mv = newVoltText.toIntOrNull() ?: selectedCluster.suggestedOverclockMv
                            onAddCustomStep(selectedCluster, mhz, mv)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KalamaCyan,
                            contentColor = Color(0xFF00242B)
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("inject_custom_opp_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Step",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. Frequency & Independent Voltage Steps List
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FREQUENCY & VOLTAGE STEPS (${clusterPoints.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Text(
                text = "Adjust MHz & mV independently",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        clusterPoints.forEach { point ->
            FrequencyVoltageStepCard(
                point = point,
                onAdjustVoltage = { delta -> onAdjustVoltage(point.id, delta) },
                onAdjustFrequency = { delta -> onAdjustFrequency(point.id, delta) },
                onResetStock = { onResetPointToStock(point.id) },
                onRemoveCustom = { onRemoveCustomStep(point.id) },
                onToggleEnabled = { onToggleStepEnabled(point.id) }
            )
        }

        // Proceed to Export / Flash Button
        Button(
            onClick = onGoToExportAndFlash,
            colors = ButtonDefaults.buttonColors(
                containerColor = KalamaAmber,
                contentColor = Color(0xFF261900)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("go_to_export_flash_btn")
        ) {
            Icon(
                imageVector = Icons.Filled.FlashOn,
                contentDescription = "Export or Flash Changes",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Export for TWRP / Odin or Flash Kernel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FrequencyVoltageStepCard(
    point: OppFrequencyPoint,
    onAdjustVoltage: (Int) -> Unit,
    onAdjustFrequency: (Int) -> Unit,
    onResetStock: () -> Unit,
    onRemoveCustom: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val badgeColor = when (point.mode) {
        OppPointMode.UNDERCLOCK -> KalamaEmerald
        OppPointMode.STOCK -> KalamaCyan
        OppPointMode.OVERCLOCK -> KalamaAmber
    }

    val isModifiedFromStock = point.stockFreqMhz != null &&
        (point.freqMhz != point.stockFreqMhz || point.voltageMv != point.stockVoltageMv)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_card_${point.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (point.enabled) ObsidianSurface else Color(0xFF0A0F1A)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (point.enabled) badgeColor.copy(alpha = 0.5f) else ObsidianCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Frequency + Badge + Stock Reference + Enable/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${point.freqMhz} MHz",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (point.enabled) TextPrimary else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = badgeColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (point.isCustomUserAdded) {
                                    "NEW ${point.mode.badgeLabel}"
                                } else {
                                    point.mode.badgeLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Always show clear Stock Reference so user understands stock vs modified
                    val stockSubtitle = if (point.stockFreqMhz != null && point.stockVoltageMv != null) {
                        val deltaMv = point.voltageDeltaFromStockMv
                        val deltaTag = when {
                            deltaMv < 0 -> " (${deltaMv} mV undervolt)"
                            deltaMv > 0 -> " (+${deltaMv} mV overvolt)"
                            else -> " (Stock voltage)"
                        }
                        "Stock Reference: ${point.stockFreqMhz} MHz @ ${point.stockVoltageMv} mV$deltaTag"
                    } else {
                        "Custom Added Step (${point.mode.badgeLabel})"
                    }
                    Text(
                        text = stockSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isModifiedFromStock) {
                        IconButton(
                            onClick = onResetStock,
                            modifier = Modifier.testTag("reset_step_${point.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset step to stock",
                                tint = KalamaCyan
                            )
                        }
                    }
                    if (point.isCustomUserAdded) {
                        IconButton(
                            onClick = onRemoveCustom,
                            modifier = Modifier.testTag("delete_step_${point.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete custom step",
                                tint = TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = point.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF002127),
                            checkedTrackColor = badgeColor
                        ),
                        modifier = Modifier.testTag("toggle_step_${point.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Independent Voltage Adjuster Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "VOLTAGE (INDEPENDENT)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${point.voltageMv} mV",
                            style = MaterialTheme.typography.titleMedium,
                            color = KalamaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallStepButton(
                            label = "-25",
                            onClick = { onAdjustVoltage(-25) },
                            tag = "volt_minus_25_${point.id}"
                        )
                        SmallStepButton(
                            label = "-5",
                            onClick = { onAdjustVoltage(-5) },
                            tag = "volt_minus_5_${point.id}"
                        )
                        SmallStepButton(
                            label = "+5",
                            onClick = { onAdjustVoltage(+5) },
                            tag = "volt_plus_5_${point.id}"
                        )
                        SmallStepButton(
                            label = "+25",
                            onClick = { onAdjustVoltage(+25) },
                            tag = "volt_plus_25_${point.id}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Independent Frequency Fine-Tuning Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "FREQUENCY (INDEPENDENT)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${point.freqMhz} MHz",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallStepButton(
                            label = "-50 MHz",
                            onClick = { onAdjustFrequency(-50) },
                            tag = "freq_minus_50_${point.id}"
                        )
                        SmallStepButton(
                            label = "-15 MHz",
                            onClick = { onAdjustFrequency(-15) },
                            tag = "freq_minus_15_${point.id}"
                        )
                        SmallStepButton(
                            label = "+15 MHz",
                            onClick = { onAdjustFrequency(+15) },
                            tag = "freq_plus_15_${point.id}"
                        )
                        SmallStepButton(
                            label = "+50 MHz",
                            onClick = { onAdjustFrequency(+50) },
                            tag = "freq_plus_50_${point.id}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallStepButton(
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        modifier = Modifier
            .testTag(tag)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF10223A),
        border = BorderStroke(1.dp, ObsidianCardBorder)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)
        )
    }
}
