package com.example.data

import kotlinx.coroutines.flow.Flow

class KernelRepository(private val dao: KernelDao) {
    val backups: Flow<List<KernelBackupEntity>> = dao.observeBackups()
    val auditLogs: Flow<List<FlashAuditLogEntity>> = dao.observeAuditLogs()

    suspend fun getPrimaryStockBackup(): KernelBackupEntity? = dao.getPrimaryStockBackup()

    suspend fun getBackupById(id: Long): KernelBackupEntity? = dao.getBackupById(id)

    suspend fun saveBackup(backup: KernelBackupEntity): Long = dao.insertBackup(backup)

    suspend fun deleteBackup(id: Long) = dao.deleteNonStockBackup(id)

    suspend fun recordAuditLog(log: FlashAuditLogEntity): Long = dao.insertAuditLog(log)
}
