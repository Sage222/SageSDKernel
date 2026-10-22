package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.FailSafeConfig
import com.example.ui.theme.KalamaAmber
import com.example.ui.theme.KalamaCrimson
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun FailSafeRecoveryScreen(
    failSafeConfig: FailSafeConfig,
    isBusy: Boolean,
    onUpdateConfig: (
        watchdogEnabled: Boolean?,
        maxFailedBoots: Int?,
        autoRollback: Boolean?,
        panicSec: Int?,
        thermalC: Int?,
        volDownClamp: Boolean?
    ) -> Unit,
    onArmWatchdogAndBuildZip: () -> Unit,
    onImmediateRestoreStock: () -> Unit,
    onTriggerReboot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Multi-Layer Fail-Safe Architecture Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.HealthAndSafety,
                        contentDescription = "Fail-Safe Recovery Engine",
                        tint = KalamaEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SM8550 FAIL-SAFE RECOVERY & WATCHDOG GUARDS",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatic stock kernel rollback on bootloop, panic, or checksum fault",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FailSafeToggleRow(
                    title = "Magisk / KernelSU post-fs-data Bootloop Watchdog",
                    subtitle = "Tracks incomplete boots before Zygote & auto-restores stock boot.img after ${failSafeConfig.maxFailedBootsBeforeRestore} failed boots",
                    checked = failSafeConfig.magiskBootloopWatchdogEnabled,
                    tag = "failsafe_watchdog_switch",
                    onCheckedChange = { onUpdateConfig(it, null, null, null, null, null) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                FailSafeToggleRow(
                    title = "Auto-Rollback on Post-Flash SHA-256 Mismatch",
                    subtitle = "Immediately reflashes Original Stock Kernel if read-back verification fails",
                    checked = failSafeConfig.autoRollbackOnVerifyMismatch,
                    tag = "failsafe_autorollback_switch",
                    onCheckedChange = { onUpdateConfig(null, null, it, null, null, null) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                FailSafeToggleRow(
                    title = "Volume-Down Safe Frequency Clamp on Boot",
                    subtitle = "Holding Vol-Down during init clamps Prime X3 to 3.36 GHz & disables OC steps",
                    checked = failSafeConfig.volumeDownSafeFreqClamp,
                    tag = "failsafe_voldown_switch",
                    onCheckedChange = { onUpdateConfig(null, null, null, null, null, it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Max Failed Boots Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bootloop Threshold Before Auto Stock Restore",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${failSafeConfig.maxFailedBootsBeforeRestore} Boots",
                        style = MaterialTheme.typography.labelLarge,
                        color = KalamaCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = failSafeConfig.maxFailedBootsBeforeRestore.toFloat(),
                    onValueChange = { onUpdateConfig(null, it.roundToInt(), null, null, null, null) },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = KalamaCyan,
                        activeTrackColor = KalamaCyan
                    ),
                    modifier = Modifier.testTag("max_failed_boots_slider")
                )

                // MSM Thermal Core Cutoff Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MSM Thermal Emergency Trip Temperature",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${failSafeConfig.msmThermalThrottleTempC} °C • Panic ${failSafeConfig.kernelPanicTimeoutSec}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = KalamaAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = failSafeConfig.msmThermalThrottleTempC.toFloat(),
                    onValueChange = { onUpdateConfig(null, null, null, null, it.roundToInt(), null) },
                    valueRange = 80f..105f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = KalamaAmber,
                        activeTrackColor = KalamaAmber
                    ),
                    modifier = Modifier.testTag("thermal_limit_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onArmWatchdogAndBuildZip,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalamaEmerald,
                        contentColor = Color(0xFF002611)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("arm_failsafe_and_zip_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Arm Fail-Safe Watchdog & Generate Recovery ZIP",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Arm Bootloop Watchdog & Build TWRP Recovery ZIP",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Generated Artifacts & Emergency TWRP Package Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BuildCircle,
                        contentDescription = "Generated Fail-Safe Artifacts",
                        tint = KalamaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEPLOYED FAIL-SAFE ARTIFACTS",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                KeyValueRow(
                    "Early-Boot Watchdog",
                    failSafeConfig.watchdogScriptPath?.substringAfterLast('/')
                        ?: "99_kalamaforge_failsafe_watchdog.sh"
                )
                KeyValueRow(
                    "Install Target",
                    "/data/adb/post-fs-data.d/99_kalamaforge_failsafe_watchdog.sh"
                )
                KeyValueRow(
                    "TWRP / OrangeFox ZIP",
                    failSafeConfig.lastGeneratedRecoveryZipPath?.substringAfterLast('/')
                        ?: "Tap 'Arm Bootloop Watchdog' above to build ZIP"
                )
                KeyValueRow(
                    "Kernel Panic sysctl",
                    "kernel.panic=${failSafeConfig.kernelPanicTimeoutSec} • panic_on_oops=1"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onImmediateRestoreStock,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalamaCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("emergency_restore_stock_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = "Emergency Reflash Stock Kernel",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency Panic Restore: Reflash Original Stock Now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTriggerReboot("recovery") },
                        border = BorderStroke(1.dp, KalamaCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("reboot_recovery_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = "Reboot Recovery",
                            tint = KalamaCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reboot Recovery",
                            style = MaterialTheme.typography.labelMedium,
                            color = KalamaCyan
                        )
                    }

                    OutlinedButton(
                        onClick = { onTriggerReboot("download") },
                        border = BorderStroke(1.dp, KalamaAmber),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("reboot_download_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = "Reboot Download / Odin",
                            tint = KalamaAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Odin / Download",
                            style = MaterialTheme.typography.labelMedium,
                            color = KalamaAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailSafeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = ObsidianSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ObsidianCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag)
            )
        }
    }
}
