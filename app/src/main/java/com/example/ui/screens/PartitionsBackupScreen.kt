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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.KernelBackupEntity
import com.example.domain.DeviceHardwareEnvironment
import com.example.domain.ExtractedKernelMetadata
import com.example.ui.theme.KalamaAmber
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PartitionsBackupScreen(
    environment: DeviceHardwareEnvironment?,
    extractedKernel: ExtractedKernelMetadata?,
    backups: List<KernelBackupEntity>,
    isBusy: Boolean,
    onBackupKernel: (String) -> Unit,
    onExtractBackup: (KernelBackupEntity) -> Unit,
    onReflashBackup: (KernelBackupEntity) -> Unit,
    onDeleteBackup: (KernelBackupEntity) -> Unit,
    onNavigateToOppForge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var snapshotLabel by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Partition & Bootloader Telemetry Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "Partition Block Inspector",
                            tint = KalamaCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SM8550 PARTITION BLOCK INSPECTOR",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = Color(0xFF0A2E23),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, KalamaEmerald.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (environment?.bootloaderUnlocked != false) "BOOTLOADER UNLOCKED" else "LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = KalamaEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                KeyValueRow("SoC Board / Arch", environment?.detectedPlatform ?: "kalama (SM8550-AC)")
                KeyValueRow("Running Kernel", environment?.unameKernelVersion ?: "5.15.123-android13-8-kalama-gki")
                KeyValueRow("Boot Block Device", environment?.bootBlockPath ?: "/dev/block/by-name/boot")
                KeyValueRow("Vendor Boot Block", environment?.vendorBootBlockPath ?: "/dev/block/by-name/vendor_boot")
                KeyValueRow("AVB Verified State", "vbmeta=${environment?.verifiedBootState ?: "orange"} • Slot: ${environment?.activeSlotSuffix ?: "Standard"}")

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = snapshotLabel,
                    onValueChange = { snapshotLabel = it },
                    label = { Text("Backup Snapshot Tag (optional)") },
                    placeholder = { Text("e.g., Pre-3.61GHz Overclock Stock Backup") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_label_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onBackupKernel(snapshotLabel)
                        snapshotLabel = ""
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalamaCyan,
                        contentColor = Color(0xFF002229)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("backup_kernel_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Backup,
                        contentDescription = "Backup Kernel",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy & Backup Kernel Partitions (boot + vendor_boot)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Extracted Working Copy & Kalama DTB Status Card
        if (extractedKernel != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("extracted_kernel_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceVariant),
                border = BorderStroke(1.dp, KalamaCyan.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EXTRACTED WORKING KERNEL & DTB",
                                style = MaterialTheme.typography.labelSmall,
                                color = KalamaCyan
                            )
                            Text(
                                text = extractedKernel.sourceLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = Color(0xFF102A45),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = extractedKernel.magicHeader,
                                style = MaterialTheme.typography.labelSmall,
                                color = KalamaCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    KeyValueRow("DTB / FDT Header", extractedKernel.dtbMagicHex)
                    KeyValueRow("Page / Header Ver", "${extractedKernel.pageSize} B Pages • v${extractedKernel.headerVersion}")
                    KeyValueRow("Extracted SHA-256", "${extractedKernel.originalSha256.take(28)}...")
                    KeyValueRow("Kernel Cmdline", extractedKernel.kernelCmdline)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToOppForge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KalamaAmber,
                            contentColor = Color(0xFF281900)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_opp_forge_from_extracted_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Tune Frequencies",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Overclock / Underclock Frequencies in OPP Forge",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Saved Kernel Backups List (with One-Tap Stock Restore & Extract)
        Text(
            text = "SAVED KERNEL BACKUP ARCHIVES (${backups.size})",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        backups.forEach { backup ->
            KernelBackupArchiveCard(
                backup = backup,
                isCurrentlyExtracted = extractedKernel?.sourceBackupId == backup.id,
                isBusy = isBusy,
                onExtract = { onExtractBackup(backup) },
                onReflash = { onReflashBackup(backup) },
                onDelete = { onDeleteBackup(backup) }
            )
        }
    }
}

@Composable
private fun KernelBackupArchiveCard(
    backup: KernelBackupEntity,
    isCurrentlyExtracted: Boolean,
    isBusy: Boolean,
    onExtract: () -> Unit,
    onReflash: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_card_${backup.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(
            width = if (backup.isOriginalStock) 1.5.dp else 1.dp,
            color = if (backup.isOriginalStock) KalamaEmerald.copy(alpha = 0.7f) else ObsidianCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (backup.isOriginalStock) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Original Stock Protected",
                                tint = KalamaEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = backup.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${dateFormat.format(Date(backup.timestamp))} • ${backup.sizeBytes / 1024} KB • ${backup.partitionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                if (backup.isOriginalStock) {
                    Surface(
                        color = Color(0xFF073620),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ORIGINAL STOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = KalamaEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_backup_${backup.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete Snapshot Backup",
                            tint = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            KeyValueRow("boot.img SHA-256", "${backup.sha256Checksum.take(26)}...")
            KeyValueRow("vendor_boot SHA-256", "${backup.vendorBootSha256.take(26)}...")
            KeyValueRow("Profile / Version", backup.oppSummary)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExtract,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, KalamaCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("extract_backup_btn_${backup.id}")
                ) {
                    Text(
                        text = if (isCurrentlyExtracted) "Extracted (Active)" else "Extract Copy",
                        style = MaterialTheme.typography.labelMedium,
                        color = KalamaCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onReflash,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (backup.isOriginalStock) KalamaEmerald else Color(0xFF1F3456),
                        contentColor = if (backup.isOriginalStock) Color(0xFF002611) else TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(if (backup.isOriginalStock) "reflash_original_stock_btn" else "reflash_backup_btn_${backup.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = "Reflash Backup",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (backup.isOriginalStock) "Reflash Stock" else "Reflash Copy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
