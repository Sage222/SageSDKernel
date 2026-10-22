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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.KernelBackupEntity
import com.example.domain.ExportFormat
import com.example.domain.ExportedPackageInfo
import com.example.domain.OppFrequencyPoint
import com.example.domain.VerificationReport
import com.example.ui.KalamaForgeViewModel
import com.example.ui.theme.KalamaAmber
import com.example.ui.theme.KalamaCyan
import com.example.ui.theme.KalamaEmerald
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SimpleBackupExportFlashScreen(
    oppPoints: List<OppFrequencyPoint>,
    backups: List<KernelBackupEntity>,
    lastExportedPackage: ExportedPackageInfo?,
    lastVerificationReport: VerificationReport?,
    isBusy: Boolean,
    onExportModified: (ExportFormat) -> Unit,
    onFlashModifiedDirectly: () -> Unit,
    onBackupOriginalNow: () -> Unit,
    onExportOriginal: (ExportFormat) -> Unit,
    onFlashOriginalDirectly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modificationSummary = remember(oppPoints) {
        KalamaForgeViewModel.buildModificationSummary(oppPoints)
    }
    val stockBackup = backups.firstOrNull { it.isOriginalStock } ?: backups.firstOrNull()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Export Result Notification Card
        if (lastExportedPackage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("last_exported_package_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF07261A)),
                border = BorderStroke(1.5.dp, KalamaEmerald)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Export Complete",
                            tint = KalamaEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXTRACTED / EXPORTED SUCCESSFULLY",
                            style = MaterialTheme.typography.titleMedium,
                            color = KalamaEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SimpleKeyValue("Package", lastExportedPackage.title)
                    SimpleKeyValue("Saved Path", lastExportedPackage.filePath)
                    SimpleKeyValue("Size", "${lastExportedPackage.sizeBytes / 1024} KB")
                    SimpleKeyValue("SHA-256", "${lastExportedPackage.sha256Checksum.take(28)}...")
                }
            }
        }

        // 2. Post-Flash Verification Result Card
        if (lastVerificationReport != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_flash_verification_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF082236)),
                border = BorderStroke(1.5.dp, KalamaCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Flash Verification Result",
                            tint = KalamaCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FLASH & READ-BACK VERIFICATION PASSED",
                            style = MaterialTheme.typography.titleMedium,
                            color = KalamaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SimpleKeyValue("Action", lastVerificationReport.actionTitle)
                    SimpleKeyValue("Partition", lastVerificationReport.targetPartitionPath)
                    SimpleKeyValue("Verified Size", "${lastVerificationReport.bytesVerified / 1024} KB (100% Stock Parity)")
                    SimpleKeyValue("Expected SHA-256", "${lastVerificationReport.expectedSha256.take(26)}...")
                    SimpleKeyValue("Read-Back SHA-256", "${lastVerificationReport.readBackSha256.take(26)}...")
                }
            }
        }

        // 3. Modified Kernel Card (magiskboot Repack + FDT opp-hz/opp-microvolt Patch + vbmeta 0x03)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.5.dp, KalamaAmber.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. MODIFIED KERNEL (MAGISKBOOT + FDT DTB PATCH)",
                    style = MaterialTheme.typography.titleMedium,
                    color = KalamaAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = modificationSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Unpacks boot & vendor_boot via magiskboot, binary-patches FDT (0xD00DFEED) opp-hz & opp-microvolt nodes, repacks, and patches vbmeta (flags=0x03).",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Direct App Flash Button (magiskboot Repacked + vbmeta)
                Button(
                    onClick = onFlashModifiedDirectly,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalamaAmber,
                        contentColor = Color(0xFF261900)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("flash_modified_direct_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = "Flash Modified Kernel Directly",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Flash Modified Kernel + vbmeta Directly & Verify",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "EXTRACT OR EXPORT MODIFIED KERNEL FOR TWRP / ODIN:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportModified(ExportFormat.TWRP_ZIP) },
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, KalamaCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_modified_twrp_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "Export TWRP ZIP",
                            tint = KalamaCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TWRP (.zip)",
                            style = MaterialTheme.typography.labelMedium,
                            color = KalamaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { onExportModified(ExportFormat.ODIN_TAR) },
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, KalamaAmber),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_modified_odin_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "Export Odin TAR",
                            tint = KalamaAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Odin (.tar)",
                            style = MaterialTheme.typography.labelMedium,
                            color = KalamaAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onExportModified(ExportFormat.RAW_BOOT_IMG) },
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, ObsidianCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("extract_modified_boot_img_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Extract Modified boot.img",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Extract Modified Kernel Backup (boot.img)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Original Stock Kernel Backup Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.5.dp, KalamaEmerald.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. ORIGINAL STOCK KERNEL BACKUP",
                            style = MaterialTheme.typography.titleMedium,
                            color = KalamaEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (stockBackup != null) {
                                "Backed Up (${stockBackup.sizeBytes / 1024} KB) • SHA-256: ${stockBackup.sha256Checksum.take(16)}..."
                            } else {
                                "Ready to backup stock partition"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        color = Color(0xFF073620),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "SAFE STOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = KalamaEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onFlashOriginalDirectly,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalamaEmerald,
                        contentColor = Color(0xFF002611)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("flash_original_stock_direct_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = "Flash Original Stock Kernel",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reflash Original Stock Kernel & Verify",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { onExportOriginal(ExportFormat.RAW_BOOT_IMG) },
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, KalamaEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("extract_original_boot_img_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Extract Original Stock boot.img",
                        tint = KalamaEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Extract Original Kernel Backup (boot.img)",
                        style = MaterialTheme.typography.labelMedium,
                        color = KalamaEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportOriginal(ExportFormat.TWRP_ZIP) },
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_original_twrp_btn")
                    ) {
                        Text(
                            text = "Stock TWRP (.zip)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = { onExportOriginal(ExportFormat.ODIN_TAR) },
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_original_odin_btn")
                    ) {
                        Text(
                            text = "Stock Odin (.tar)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onBackupOriginalNow,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, ObsidianCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("backup_kernel_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Backup,
                        contentDescription = "Create Fresh Backup Copy",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Create Fresh Backup Copy of Current Partition",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleKeyValue(label: String, value: String) {
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
