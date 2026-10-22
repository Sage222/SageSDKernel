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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FlashAuditLogEntity
import com.example.domain.AvbSigningMode
import com.example.domain.DeviceHardwareEnvironment
import com.example.domain.PatchedKernelArtifact
import com.example.domain.VerificationReport
import com.example.ui.theme.KalamaAmber
import com.example.ui.theme.KalamaCrimson
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AvbSignerAndFlashScreen(
    environment: DeviceHardwareEnvironment?,
    selectedSigningMode: AvbSigningMode,
    compiledArtifact: PatchedKernelArtifact?,
    lastVerificationReport: VerificationReport?,
    simulateVerifyFault: Boolean,
    auditLogs: List<FlashAuditLogEntity>,
    isBusy: Boolean,
    onSelectSigningMode: (AvbSigningMode) -> Unit,
    onCompileAndSign: () -> Unit,
    onToggleSimulateFault: (Boolean) -> Unit,
    onFlashAndVerifyPatched: () -> Unit,
    onReflashOriginalStock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // AVB 2.0 Boot Image Signing Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = "AVB 2.0 Boot Image Signer",
                        tint = KalamaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "AVB 2.0 BOOT & VENDOR_BOOT IMAGE SIGNER",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Appends cryptographic AVBf footer & vbmeta hash descriptors",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AvbSigningMode.entries.forEach { mode ->
                    val selected = mode == selectedSigningMode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFF102A45) else ObsidianSurfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) KalamaCyan else ObsidianCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("avb_mode_${mode.name.lowercase()}")
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectSigningMode(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectSigningMode(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = KalamaCyan)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) KalamaCyan else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.vbmetaFlagDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onCompileAndSign,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, KalamaCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("compile_and_sign_btn")
                ) {
                    Text(
                        text = "Re-Compile DTB & Sign Boot Image (${selectedSigningMode.keyAlgorithm})",
                        style = MaterialTheme.typography.labelLarge,
                        color = KalamaCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Signed Artifact Inspector & Partition Flasher Card
        if (compiledArtifact != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signed_artifact_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, KalamaAmber.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SIGNED PATCHED KERNEL READY TO FLASH",
                                style = MaterialTheme.typography.labelSmall,
                                color = KalamaAmber
                            )
                            Text(
                                text = compiledArtifact.oppSummary,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = Color(0xFF332400),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${compiledArtifact.activePointCount} OPP PTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = KalamaAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    KeyValueRow("Target Partition", environment?.bootBlockPath ?: "/dev/block/by-name/boot")
                    KeyValueRow("Signed boot.img SHA-256", "${compiledArtifact.sha256Checksum.take(28)}...")
                    KeyValueRow("vendor_boot SHA-256", "${compiledArtifact.vendorBootSha256.take(28)}...")
                    KeyValueRow("AVB 2.0 Footer Digest", "AVBf:${compiledArtifact.avbFooterDigestHex.take(20)}...")
                    KeyValueRow("Underclock / Overclock", "${compiledArtifact.underclockFloorMhz} -> ${compiledArtifact.overclockPeakMhz}")

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fault injection switch for testing Fail-Safe Auto-Rollback
                    Surface(
                        color = ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Simulate NAND Bit-Flip Fault on Next Flash",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Tests post-flash SHA-256 mismatch detection & Fail-Safe Stock Auto-Rollback",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = simulateVerifyFault,
                                onCheckedChange = onToggleSimulateFault,
                                modifier = Modifier.testTag("simulate_fault_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onFlashAndVerifyPatched,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KalamaAmber,
                            contentColor = Color(0xFF261900)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("flash_and_verify_patched_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FlashOn,
                            contentDescription = "Flash & Verify Patched Kernel",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Flash Patched Kernel & Run Read-Back Verification",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onReflashOriginalStock,
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, KalamaEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reflash_stock_from_flash_tab_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "Restore Original Stock Backup",
                            tint = KalamaEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reflash Original Backed-Up Stock Kernel",
                            style = MaterialTheme.typography.labelLarge,
                            color = KalamaEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Post-Flash Read-Back Verification Report Card
        if (lastVerificationReport != null) {
            VerificationReportCard(report = lastVerificationReport)
        }

        // Flash & Verification Audit Trail
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Flash Verification History",
                        tint = KalamaCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PARTITION FLASH & VERIFICATION AUDIT LOG",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                auditLogs.take(6).forEach { log ->
                    Surface(
                        color = ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.operationType,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (log.verificationPassed) KalamaEmerald else KalamaCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (log.verificationPassed) "SHA-256 VERIFIED" else "ROLLBACK GUARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (log.verificationPassed) KalamaEmerald else KalamaAmber
                                )
                            }
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Read-back: ${log.verifiedReadBackSha256.take(32)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationReportCard(report: VerificationReport) {
    val borderColor = if (report.passed) KalamaEmerald else KalamaCrimson
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_flash_verification_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (report.passed) Color(0xFF062317) else Color(0xFF2B0B11)
        ),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (report.passed) Icons.Filled.Verified else Icons.Filled.ErrorOutline,
                    contentDescription = "Verification Status",
                    tint = borderColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (report.passed) {
                            "POST-FLASH READ-BACK VERIFICATION PASSED"
                        } else {
                            "VERIFICATION MISMATCH • FAIL-SAFE ROLLBACK EXECUTED"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = borderColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Verified ${report.bytesVerified / 1024} KB directly from ${report.targetPartitionPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            KeyValueRow("Expected Image SHA-256", "${report.expectedSha256.take(28)}...")
            KeyValueRow("Partition Read-Back SHA-256", "${report.readBackSha256.take(28)}...")
            KeyValueRow(
                "Boot Header & AVB Footer",
                "ANDROID!=${if (report.headerMagicValid) "OK" else "ERR"} • AVBf=${if (report.avbFooterValid) "OK" else "ERR"}"
            )
            if (report.autoRollbackTriggered) {
                KeyValueRow(
                    "Fail-Safe Stock Restored",
                    "Restored SHA-256: ${report.rollbackRestoredSha256?.take(22)}..."
                )
            }
        }
    }
}
