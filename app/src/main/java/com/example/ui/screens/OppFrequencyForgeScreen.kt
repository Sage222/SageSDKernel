package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlin.math.roundToInt

@Composable
fun OppFrequencyForgeScreen(
    selectedCluster: KalamaCluster,
    oppPoints: List<OppFrequencyPoint>,
    globalVoltageOffsetMv: Int,
    activePresetName: String,
    onSelectCluster: (KalamaCluster) -> Unit,
    onToggleOppPoint: (String) -> Unit,
    onVoltageOffsetChange: (Int) -> Unit,
    onApplyPreset: (String) -> Unit,
    onAddCustomOppPoint: (KalamaCluster, Int, Int) -> Unit,
    onProceedToSignAndFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customFreqMhzText by remember(selectedCluster) {
        val defaultNewFreq = when (selectedCluster) {
            KalamaCluster.SILVER -> "192"
            KalamaCluster.GOLD -> "3014"
            KalamaCluster.PRIME -> "3648"
            KalamaCluster.GPU_ADRENO740 -> "815"
        }
        mutableStateOf(defaultNewFreq)
    }
    var customVoltMvText by remember(selectedCluster) {
        val defaultNewVolt = when (selectedCluster) {
            KalamaCluster.SILVER -> "615"
            KalamaCluster.GOLD -> "1035"
            KalamaCluster.PRIME -> "1160"
            KalamaCluster.GPU_ADRENO740 -> "965"
        }
        mutableStateOf(defaultNewVolt)
    }

    val clusterPoints = remember(oppPoints, selectedCluster) {
        oppPoints.filter { it.cluster == selectedCluster }.sortedBy { it.freqKhz }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Hardware Profiles Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "SM8550 DTB Frequency Profiles",
                        tint = KalamaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SM8550-AC DTB OPP PROFILES & CPR3 VOLTAGE",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active: $activePresetName",
                            style = MaterialTheme.typography.labelSmall,
                            color = KalamaCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetPillButton(
                        title = "Stock 3.36GHz",
                        subtitle = "OEM Default",
                        accent = KalamaCyan,
                        tag = "preset_btn_stock",
                        onClick = { onApplyPreset("STOCK") },
                        modifier = Modifier.weight(1f)
                    )
                    PresetPillButton(
                        title = "Cryo Underclock",
                        subtitle = "153MHz • -35mV",
                        accent = KalamaEmerald,
                        tag = "preset_btn_cryo_uc",
                        onClick = { onApplyPreset("CRYO_UC") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetPillButton(
                        title = "Balanced OC+UC",
                        subtitle = "153MHz–3.61GHz",
                        accent = KalamaCyan,
                        tag = "preset_btn_balanced",
                        onClick = { onApplyPreset("BALANCED") },
                        modifier = Modifier.weight(1f)
                    )
                    PresetPillButton(
                        title = "Extreme 3.71GHz",
                        subtitle = "+850MHz Adreno",
                        accent = KalamaAmber,
                        tag = "preset_btn_ultra_oc",
                        onClick = { onApplyPreset("ULTRA_OC") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Global Core Voltage Offset Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Global CPR3 Core Voltage Offset",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${if (globalVoltageOffsetMv >= 0) "+$globalVoltageOffsetMv" else "$globalVoltageOffsetMv"} mV",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (globalVoltageOffsetMv < 0) KalamaEmerald else if (globalVoltageOffsetMv > 0) KalamaAmber else KalamaCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = globalVoltageOffsetMv.toFloat(),
                    onValueChange = { raw ->
                        val stepped = (raw / 5f).roundToInt() * 5
                        onVoltageOffsetChange(stepped)
                    },
                    valueRange = -75f..50f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = KalamaCyan,
                        activeTrackColor = KalamaCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_voltage_slider")
                )
            }
        }

        // Custom Frequency Step Injector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AddCircleOutline,
                        contentDescription = "Inject Custom OPP Frequency",
                        tint = KalamaAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "INJECT CUSTOM OVERCLOCK / UNDERCLOCK STEP",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target DTB Node: ${selectedCluster.dtbNodeName} (${selectedCluster.microArch})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
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
                        value = customFreqMhzText,
                        onValueChange = { customFreqMhzText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Freq (MHz)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_freq_mhz_input")
                    )

                    OutlinedTextField(
                        value = customVoltMvText,
                        onValueChange = { customVoltMvText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Voltage (mV)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_volt_mv_input")
                    )

                    Button(
                        onClick = {
                            val mhz = customFreqMhzText.toIntOrNull() ?: 3550
                            val mv = customVoltMvText.toIntOrNull() ?: 1120
                            onAddCustomOppPoint(selectedCluster, mhz, mv)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KalamaAmber,
                            contentColor = Color(0xFF281900)
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("inject_custom_opp_btn")
                    ) {
                        Text(
                            text = "Add OPP",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Cluster Selector Header + OPP Table
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = selectedCluster.title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = KalamaCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${selectedCluster.coreTopology} • Stock Range: ${selectedCluster.stockMinKhz / 1000} MHz – ${selectedCluster.stockMaxKhz / 1000} MHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Cluster Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KalamaCluster.entries.forEach { cluster ->
                        val active = cluster == selectedCluster
                        val label = when (cluster) {
                            KalamaCluster.SILVER -> "Silver"
                            KalamaCluster.GOLD -> "Gold"
                            KalamaCluster.PRIME -> "Prime X3"
                            KalamaCluster.GPU_ADRENO740 -> "Adreno"
                        }
                        OutlinedButton(
                            onClick = { onSelectCluster(cluster) },
                            border = BorderStroke(1.dp, if (active) KalamaCyan else ObsidianCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) Color(0xFF102A45) else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cluster_tab_${cluster.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) KalamaCyan else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                clusterPoints.forEach { point ->
                    OppPointRowItem(
                        point = point,
                        globalVoltageOffsetMv = globalVoltageOffsetMv,
                        onToggle = { onToggleOppPoint(point.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onProceedToSignAndFlash,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalamaCyan,
                        contentColor = Color(0xFF00242B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("proceed_to_avb_flash_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = "Compile, Sign & Flash",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Compile DTB Table, Sign & Proceed to Flash",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetPillButton(
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun OppPointRowItem(
    point: OppFrequencyPoint,
    globalVoltageOffsetMv: Int,
    onToggle: () -> Unit
) {
    val badgeColor = when (point.mode) {
        OppPointMode.UNDERCLOCK -> KalamaEmerald
        OppPointMode.STOCK -> KalamaCyan
        OppPointMode.OVERCLOCK -> KalamaAmber
    }
    val effectiveMv = point.effectiveVoltageMv(globalVoltageOffsetMv)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (point.enabled) ObsidianSurfaceVariant else Color(0xFF0A0F1B),
        border = BorderStroke(
            1.dp,
            if (point.enabled) badgeColor.copy(alpha = 0.45f) else ObsidianCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("opp_row_${point.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (point.mode == OppPointMode.OVERCLOCK) Icons.Filled.Bolt else Icons.Filled.Speed,
                    contentDescription = point.mode.badgeLabel,
                    tint = if (point.enabled) badgeColor else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${String.format("%.1f", point.freqMhz)} MHz",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (point.enabled) TextPrimary else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (point.isCustomUserAdded) "CUSTOM ${point.mode.badgeLabel}" else point.mode.badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "DTB: ${point.freqKhz * 1000L} Hz • VDD: $effectiveMv mV (${effectiveMv * 1000} uV)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Switch(
                checked = point.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF002127),
                    checkedTrackColor = badgeColor
                ),
                modifier = Modifier.testTag("opp_switch_${point.id}")
            )
        }
    }
}
