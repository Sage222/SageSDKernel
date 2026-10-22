package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KernelDao {
    @Query("SELECT * FROM kernel_backups ORDER BY isOriginalStock DESC, timestamp DESC")
    fun observeBackups(): Flow<List<KernelBackupEntity>>

    @Query("SELECT * FROM kernel_backups WHERE isOriginalStock = 1 ORDER BY timestamp ASC LIMIT 1")
    suspend fun getPrimaryStockBackup(): KernelBackupEntity?

    @Query("SELECT * FROM kernel_backups WHERE id = :id LIMIT 1")
    suspend fun getBackupById(id: Long): KernelBackupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: KernelBackupEntity): Long

    @Query("DELETE FROM kernel_backups WHERE id = :id AND isOriginalStock = 0")
    suspend fun deleteNonStockBackup(id: Long)

    @Query("SELECT * FROM flash_audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeAuditLogs(): Flow<List<FlashAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: FlashAuditLogEntity): Long
}
