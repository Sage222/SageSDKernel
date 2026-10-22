package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kernel_backups")
data class KernelBackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val partitionName: String,
    val blockDevicePath: String,
    val localFilePath: String,
    val vendorBootFilePath: String,
    val sha256Checksum: String,
    val vendorBootSha256: String,
    val sizeBytes: Long,
    val kernelVersion: String,
    val headerVersion: Int,
    val isOriginalStock: Boolean,
    val isSignedAvb: Boolean,
    val avbKeyType: String,
    val oppSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "flash_audit_logs")
data class FlashAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String, // BACKUP, EXTRACT_DTB, PATCH_SIGN, FLASH_VERIFY, RESTORE_STOCK, FAILSAFE_ROLLBACK
    val targetPartition: String,
    val expectedSha256: String,
    val verifiedReadBackSha256: String,
    val verificationPassed: Boolean,
    val details: String,
    val commandExecuted: String,
    val timestamp: Long = System.currentTimeMillis()
)
