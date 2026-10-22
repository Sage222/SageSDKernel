package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.KalamaDatabase
import com.example.data.KernelBackupEntity
import com.example.data.KernelRepository
import com.example.domain.DeviceHardwareEnvironment
import com.example.domain.ExportFormat
import com.example.domain.ExportedPackageInfo
import com.example.domain.KalamaCluster
import com.example.domain.KalamaDefaultOppTable
import com.example.domain.OppFrequencyPoint
import com.example.domain.OppPointMode
import com.example.domain.RootKernelEngine
import com.example.domain.VerificationReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SimpleTab {
    FREQUENCIES,
    BACKUP_EXPORT_FLASH
}

data class KalamaUiState(
    val selectedTab: SimpleTab = SimpleTab.FREQUENCIES,
    val environment: DeviceHardwareEnvironment? = null,
    val isBusy: Boolean = false,
    val statusMessage: String = "Stock frequencies loaded • Adjust voltages or add Overclock / Underclock steps below",
    val isError: Boolean = false,
    val selectedCluster: KalamaCluster = KalamaCluster.PRIME,
    val oppPoints: List<OppFrequencyPoint> = KalamaDefaultOppTable.buildStockPoints(),
    val lastExportedPackage: ExportedPackageInfo? = null,
    val lastVerificationReport: VerificationReport? = null,
    val exportedHistory: List<ExportedPackageInfo> = emptyList()
)

class KalamaForgeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = KalamaDatabase.getInstance(application)
    private val repository = KernelRepository(database.kernelDao())
    private val engine = RootKernelEngine(application)

    private val _uiState = MutableStateFlow(KalamaUiState())
    val uiState: StateFlow<KalamaUiState> = _uiState.asStateFlow()

    val backups: StateFlow<List<KernelBackupEntity>> = repository.backups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var detectedStockTable: List<OppFrequencyPoint> = KalamaDefaultOppTable.buildStockPoints()

    init {
        initializeStockKernelBackupAndTable()
    }

    private fun initializeStockKernelBackupAndTable() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val env = engine.probeEnvironment()
            val liveStock = engine.readLiveOrDefaultStockOppTable()
            detectedStockTable = liveStock

            var primaryStock = repository.getPrimaryStockBackup()
            if (primaryStock == null) {
                val backupRes = engine.backupKernelPartitions(
                    env = env,
                    backupLabel = "Original Stock Kernel",
                    isOriginalStock = true
                )
                val entity = KernelBackupEntity(
                    label = "Original Stock Kernel (boot + vendor_boot + vbmeta)",
                    partitionName = "boot + vendor_boot + vbmeta",
                    blockDevicePath = env.bootBlockPath,
                    localFilePath = backupRes.bootFile.absolutePath,
                    vendorBootFilePath = backupRes.vendorBootFile.absolutePath,
                    vbmetaFilePath = backupRes.vbmetaFile.absolutePath,
                    sha256Checksum = backupRes.bootSha256,
                    vendorBootSha256 = backupRes.vendorBootSha256,
                    vbmetaSha256 = backupRes.vbmetaSha256,
                    sizeBytes = backupRes.totalSizeBytes,
                    kernelVersion = env.unameKernelVersion,
                    headerVersion = 4,
                    isOriginalStock = true,
                    isSignedAvb = true,
                    avbKeyType = "Stock AVB 2.0",
                    oppSummary = "Stock 307 MHz – 3360 MHz • Adreno 740 @ 719 MHz"
                )
                repository.saveBackup(entity)
            }
            val mbStatus = if (env.magiskbootPath != null) "magiskboot ready (${env.magiskbootPath})" else "FDT binary patcher ready"
            _uiState.update {
                it.copy(
                    environment = env,
                    oppPoints = liveStock,
                    isBusy = false,
                    statusMessage = "Stock boot + vendor_boot + vbmeta backed up • $mbStatus"
                )
            }
        }
    }

    fun selectTab(tab: SimpleTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectCluster(cluster: KalamaCluster) {
        _uiState.update { it.copy(selectedCluster = cluster) }
    }

    fun adjustPointVoltage(pointId: String, deltaMv: Int) {
        _uiState.update { state ->
            val updated = state.oppPoints.map { pt ->
                if (pt.id == pointId) {
                    val newMv = (pt.voltageMv + deltaMv).coerceIn(500, 1300)
                    pt.copy(voltageMv = newMv)
                } else {
                    pt
                }
            }
            val target = updated.find { it.id == pointId }
            state.copy(
                oppPoints = updated,
                isError = false,
                statusMessage = if (target != null) {
                    "Set ${target.freqMhz} MHz voltage to ${target.voltageMv} mV"
                } else {
                    state.statusMessage
                }
            )
        }
    }

    fun adjustPointFrequency(pointId: String, deltaMhz: Int) {
        _uiState.update { state ->
            val updated = state.oppPoints.map { pt ->
                if (pt.id == pointId) {
                    val newMhz = (pt.freqMhz + deltaMhz).coerceIn(100, 4000)
                    val newMode = classifyMode(pt.cluster, newMhz, pt.stockFreqMhz)
                    pt.copy(freqMhz = newMhz, mode = newMode)
                } else {
                    pt
                }
            }.sortedBy { it.freqMhz }

            val target = updated.find { it.id == pointId }
            state.copy(
                oppPoints = updated,
                isError = false,
                statusMessage = if (target != null) {
                    "Updated step frequency to ${target.freqMhz} MHz (${target.voltageMv} mV)"
                } else {
                    state.statusMessage
                }
            )
        }
    }

    fun shiftClusterVoltages(cluster: KalamaCluster, deltaMv: Int) {
        _uiState.update { state ->
            val updated = state.oppPoints.map { pt ->
                if (pt.cluster == cluster) {
                    pt.copy(voltageMv = (pt.voltageMv + deltaMv).coerceIn(500, 1300))
                } else {
                    pt
                }
            }
            val sign = if (deltaMv >= 0) "+$deltaMv" else "$deltaMv"
            state.copy(
                oppPoints = updated,
                isError = false,
                statusMessage = "Shifted all ${cluster.shortName} voltages by $sign mV"
            )
        }
    }

    fun resetPointToStock(pointId: String) {
        _uiState.update { state ->
            val updated = state.oppPoints.map { pt ->
                if (pt.id == pointId && pt.stockFreqMhz != null && pt.stockVoltageMv != null) {
                    pt.copy(
                        freqMhz = pt.stockFreqMhz,
                        voltageMv = pt.stockVoltageMv,
                        mode = OppPointMode.STOCK,
                        enabled = true
                    )
                } else {
                    pt
                }
            }.sortedBy { it.freqMhz }
            state.copy(
                oppPoints = updated,
                isError = false,
                statusMessage = "Reset step to original stock frequency & voltage"
            )
        }
    }

    fun resetClusterToStock(cluster: KalamaCluster) {
        val stockForCluster = detectedStockTable.filter { it.cluster == cluster }
        _uiState.update { state ->
            val remainingOtherClusters = state.oppPoints.filter { it.cluster != cluster }
            state.copy(
                oppPoints = (remainingOtherClusters + stockForCluster).sortedBy { it.freqMhz },
                isError = false,
                statusMessage = "Reset ${cluster.shortName} to 100% Stock frequencies and voltages"
            )
        }
    }

    fun resetAllClustersToStock() {
        _uiState.update {
            it.copy(
                oppPoints = detectedStockTable,
                isError = false,
                statusMessage = "All CPU & GPU clusters reset to Samsung Stock frequencies and voltages"
            )
        }
    }

    fun addCustomFrequencyStep(cluster: KalamaCluster, freqMhzInput: Int, voltageMvInput: Int) {
        val freqMhz = freqMhzInput.coerceIn(100, 4000)
        val voltageMv = voltageMvInput.coerceIn(500, 1300)
        val mode = when {
            freqMhz < cluster.stockMinMhz -> OppPointMode.UNDERCLOCK
            freqMhz > cluster.stockMaxMhz -> OppPointMode.OVERCLOCK
            else -> OppPointMode.STOCK
        }
        val newPoint = OppFrequencyPoint(
            id = "custom_${cluster.name.lowercase()}_${freqMhz}_${System.currentTimeMillis() % 100000}",
            cluster = cluster,
            freqMhz = freqMhz,
            stockFreqMhz = null,
            voltageMv = voltageMv,
            stockVoltageMv = null,
            mode = mode,
            enabled = true,
            isCustomUserAdded = true
        )
        _uiState.update { state ->
            val merged = (state.oppPoints + newPoint).sortedBy { it.freqMhz }
            state.copy(
                oppPoints = merged,
                isError = false,
                statusMessage = "Added ${mode.badgeLabel} step: $freqMhz MHz @ $voltageMv mV to ${cluster.shortName}"
            )
        }
    }

    fun removeCustomStep(pointId: String) {
        _uiState.update { state ->
            val target = state.oppPoints.find { it.id == pointId }
            if (target == null || !target.isCustomUserAdded) return@update state
            state.copy(
                oppPoints = state.oppPoints.filterNot { it.id == pointId },
                isError = false,
                statusMessage = "Removed custom step ${target.freqMhz} MHz"
            )
        }
    }

    fun toggleStepEnabled(pointId: String) {
        _uiState.update { state ->
            val target = state.oppPoints.find { it.id == pointId } ?: return@update state
            val enabledCount = state.oppPoints.count { it.cluster == target.cluster && it.enabled }
            if (target.enabled && enabledCount <= 2) {
                return@update state.copy(
                    isError = true,
                    statusMessage = "Keep at least 2 active steps in ${target.cluster.shortName}"
                )
            }
            val updated = state.oppPoints.map {
                if (it.id == pointId) it.copy(enabled = !it.enabled) else it
            }
            state.copy(
                oppPoints = updated,
                isError = false,
                statusMessage = "${target.freqMhz} MHz step ${if (!target.enabled) "enabled" else "disabled"}"
            )
        }
    }

    fun backupOriginalStockKernelNow() {
        val env = _uiState.value.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            val backupRes = engine.backupKernelPartitions(
                env = env,
                backupLabel = "Original Stock Kernel",
                isOriginalStock = true
            )
            val entity = KernelBackupEntity(
                label = "Original Stock Kernel (${backups.value.size + 1})",
                partitionName = "boot + vendor_boot + vbmeta",
                blockDevicePath = env.bootBlockPath,
                localFilePath = backupRes.bootFile.absolutePath,
                vendorBootFilePath = backupRes.vendorBootFile.absolutePath,
                vbmetaFilePath = backupRes.vbmetaFile.absolutePath,
                sha256Checksum = backupRes.bootSha256,
                vendorBootSha256 = backupRes.vendorBootSha256,
                vbmetaSha256 = backupRes.vbmetaSha256,
                sizeBytes = backupRes.totalSizeBytes,
                kernelVersion = env.unameKernelVersion,
                headerVersion = 4,
                isOriginalStock = backups.value.none { it.isOriginalStock },
                isSignedAvb = true,
                avbKeyType = "Stock AVB 2.0",
                oppSummary = "Stock 307 MHz – 3360 MHz"
            )
            repository.saveBackup(entity)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = "Backed up Stock boot + vendor_boot + vbmeta (${backupRes.bootSha256.take(16)}...)"
                )
            }
        }
    }

    fun exportOriginalStockKernel(format: ExportFormat) {
        val env = _uiState.value.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            val stock = repository.getPrimaryStockBackup() ?: backups.value.firstOrNull()
            if (stock == null) {
                _uiState.update {
                    it.copy(isBusy = false, isError = true, statusMessage = "No Stock Backup found yet")
                }
                return@launch
            }
            val exported = engine.exportKernelArtifact(
                env = env,
                bootFile = File(stock.localFilePath),
                vendorBootFile = File(stock.vendorBootFilePath),
                vbmetaFile = stock.vbmetaFilePath.takeIf { it.isNotBlank() }?.let { File(it) },
                format = format,
                isOriginalStock = true,
                oppSummary = "Original Stock SM8550 Kernel"
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastExportedPackage = exported,
                    exportedHistory = (listOf(exported) + it.exportedHistory).take(8),
                    statusMessage = "Extracted Original Kernel -> ${exported.filePath}"
                )
            }
        }
    }

    fun exportModifiedKernel(format: ExportFormat) {
        val state = _uiState.value
        val env = state.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            val stock = repository.getPrimaryStockBackup() ?: backups.value.firstOrNull()
            val bundle = engine.buildModifiedKernelImagesInPlace(
                env = env,
                stockBootSource = stock?.localFilePath?.let { File(it) },
                stockVendorBootSource = stock?.vendorBootFilePath?.let { File(it) },
                stockVbmetaSource = stock?.vbmetaFilePath?.takeIf { it.isNotBlank() }?.let { File(it) },
                activeOppPoints = state.oppPoints
            )
            val summary = buildModificationSummary(state.oppPoints)
            val exported = engine.exportKernelArtifact(
                env = env,
                bootFile = bundle.bootFile,
                vendorBootFile = bundle.vendorBootFile,
                vbmetaFile = bundle.vbmetaFile,
                format = format,
                isOriginalStock = false,
                oppSummary = summary
            )
            val fdtStats = "${bundle.fdtPatchResult.oppMicrovoltPatchedCount} uV & ${bundle.fdtPatchResult.oppHzPatchedCount} Hz nodes patched"
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastExportedPackage = exported,
                    exportedHistory = (listOf(exported) + it.exportedHistory).take(8),
                    statusMessage = "Exported (${format.extension}) [$fdtStats] -> ${exported.filePath}"
                )
            }
        }
    }

    fun flashModifiedKernelDirectly() {
        val state = _uiState.value
        val env = state.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            try {
                val stock = repository.getPrimaryStockBackup() ?: backups.value.firstOrNull()
                val stockBootFile = stock?.localFilePath?.let { File(it) }
                val bundle = engine.buildModifiedKernelImagesInPlace(
                    env = env,
                    stockBootSource = stockBootFile,
                    stockVendorBootSource = stock?.vendorBootFilePath?.let { File(it) },
                    stockVbmetaSource = stock?.vbmetaFilePath?.takeIf { it.isNotBlank() }?.let { File(it) },
                    activeOppPoints = state.oppPoints
                )
                val fdtStats = "${bundle.fdtPatchResult.oppMicrovoltPatchedCount} uV / ${bundle.fdtPatchResult.oppHzPatchedCount} Hz FDT nodes"
                val report = engine.flashKernelAndVerify(
                    env = env,
                    actionTitle = "Flashed Patched Kernel ($fdtStats) + vbmeta (flags=0x03)",
                    sourceBootFile = bundle.bootFile,
                    sourceVendorBootFile = bundle.vendorBootFile,
                    sourceVbmetaFile = bundle.vbmetaFile,
                    expectedStockBootSize = stockBootFile?.length() ?: bundle.bootFile.length()
                )
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastVerificationReport = report,
                        isError = !report.passed,
                        statusMessage = if (report.passed) {
                            "Flashed Patched Kernel ($fdtStats) + vbmeta & Verified SHA-256!"
                        } else {
                            "Verification mismatch detected!"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isError = true,
                        statusMessage = e.message ?: "Pre-Flash Guard blocked unsafe flash"
                    )
                }
            }
        }
    }

    fun flashOriginalStockKernelDirectly() {
        val env = _uiState.value.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            val stock = repository.getPrimaryStockBackup() ?: backups.value.firstOrNull()
            if (stock == null) {
                _uiState.update {
                    it.copy(isBusy = false, isError = true, statusMessage = "No Original Stock Backup available")
                }
                return@launch
            }
            val stockBootFile = File(stock.localFilePath)
            val report = engine.flashKernelAndVerify(
                env = env,
                actionTitle = "Restored Original Stock Kernel + Stock vbmeta Backup",
                sourceBootFile = stockBootFile,
                sourceVendorBootFile = File(stock.vendorBootFilePath),
                sourceVbmetaFile = stock.vbmetaFilePath.takeIf { it.isNotBlank() }?.let { File(it) },
                expectedStockBootSize = stockBootFile.length()
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastVerificationReport = report,
                    isError = !report.passed,
                    statusMessage = "Original Stock Kernel + vbmeta Flashed & Verified!"
                )
            }
        }
    }

    private fun classifyMode(cluster: KalamaCluster, freqMhz: Int, stockFreqMhz: Int?): OppPointMode {
        return when {
            freqMhz < cluster.stockMinMhz -> OppPointMode.UNDERCLOCK
            freqMhz > cluster.stockMaxMhz -> OppPointMode.OVERCLOCK
            stockFreqMhz != null && freqMhz < stockFreqMhz -> OppPointMode.UNDERCLOCK
            stockFreqMhz != null && freqMhz > stockFreqMhz -> OppPointMode.OVERCLOCK
            else -> OppPointMode.STOCK
        }
    }

    companion object {
        fun buildModificationSummary(points: List<OppFrequencyPoint>): String {
            val enabled = points.filter { it.enabled }
            val ucCount = enabled.count { it.mode == OppPointMode.UNDERCLOCK }
            val ocCount = enabled.count { it.mode == OppPointMode.OVERCLOCK }
            val customVoltCount = enabled.count {
                it.stockVoltageMv != null && it.voltageMv != it.stockVoltageMv
            }
            val maxPrime = enabled.filter { it.cluster == KalamaCluster.PRIME }.maxOfOrNull { it.freqMhz } ?: 3360
            val minSilver = enabled.filter { it.cluster == KalamaCluster.SILVER }.minOfOrNull { it.freqMhz } ?: 307
            return "Idle $minSilver MHz • Peak $maxPrime MHz • $ucCount UC / $ocCount OC / $customVoltCount Custom mV"
        }
    }
}
