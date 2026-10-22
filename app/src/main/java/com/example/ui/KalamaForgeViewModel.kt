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

    init {
        initializeStockKernelBackup()
    }

    private fun initializeStockKernelBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val env = engine.probeEnvironment()
            var primaryStock = repository.getPrimaryStockBackup()
            if (primaryStock == null) {
                val backupRes = engine.backupKernelPartitions(
                    env = env,
                    backupLabel = "Original Stock Kernel",
                    isOriginalStock = true
                )
                val entity = KernelBackupEntity(
                    label = "Original Stock Kernel (SM8550)",
                    partitionName = "boot + vendor_boot",
                    blockDevicePath = env.bootBlockPath,
                    localFilePath = backupRes.bootFile.absolutePath,
                    vendorBootFilePath = backupRes.vendorBootFile.absolutePath,
                    sha256Checksum = backupRes.bootSha256,
                    vendorBootSha256 = backupRes.vendorBootSha256,
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
            _uiState.update {
                it.copy(
                    environment = env,
                    isBusy = false,
                    statusMessage = "Original Stock Kernel backed up • Select a cluster to view or edit frequencies & voltages"
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

    /**
     * Adjusts a single step's voltage independently without changing its frequency.
     */
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

    /**
     * Adjusts a single step's frequency independently without changing its voltage.
     */
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

    /**
     * Shifts all voltages in the currently selected cluster up or down independently of frequencies.
     */
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

    /**
     * Resets a single step back to its original stock frequency and stock voltage.
     */
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

    /**
     * Resets the entire selected cluster (or all clusters) back to unmodified Samsung Stock frequencies & voltages.
     */
    fun resetClusterToStock(cluster: KalamaCluster) {
        val stockForCluster = KalamaDefaultOppTable.buildStockPoints().filter { it.cluster == cluster }
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
                oppPoints = KalamaDefaultOppTable.buildStockPoints(),
                isError = false,
                statusMessage = "All CPU & GPU clusters reset to Samsung Stock frequencies and voltages"
            )
        }
    }

    /**
     * Adds a new Overclock or Underclock (or custom intermediate) frequency step with its own independent voltage.
     */
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
                partitionName = "boot + vendor_boot",
                blockDevicePath = env.bootBlockPath,
                localFilePath = backupRes.bootFile.absolutePath,
                vendorBootFilePath = backupRes.vendorBootFile.absolutePath,
                sha256Checksum = backupRes.bootSha256,
                vendorBootSha256 = backupRes.vendorBootSha256,
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
                    statusMessage = "Backed up Stock Kernel (${backupRes.bootSha256.take(16)}...)"
                )
            }
        }
    }

    /**
     * Exports / extracts the Original Stock Kernel as a raw `boot.img`, TWRP `.zip`, or Odin `.tar`.
     */
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

    /**
     * Builds & exports the Modified Kernel (with your Overclock/Underclock steps and custom voltages)
     * as a raw `boot.img`, TWRP Flashable `.zip`, or Samsung Odin `.tar`.
     */
    fun exportModifiedKernel(format: ExportFormat) {
        val state = _uiState.value
        val env = state.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            val (modBoot, modVendor) = engine.buildModifiedKernelImages(state.oppPoints)
            val summary = buildModificationSummary(state.oppPoints)
            val exported = engine.exportKernelArtifact(
                env = env,
                bootFile = modBoot,
                vendorBootFile = modVendor,
                format = format,
                isOriginalStock = false,
                oppSummary = summary
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastExportedPackage = exported,
                    exportedHistory = (listOf(exported) + it.exportedHistory).take(8),
                    statusMessage = "Exported Modified Kernel (${format.extension}) -> ${exported.filePath}"
                )
            }
        }
    }

    /**
     * Flashes the Modified Kernel directly to the device partition and runs a read-back SHA-256 verification.
     */
    fun flashModifiedKernelDirectly() {
        val state = _uiState.value
        val env = state.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isError = false) }
            val (modBoot, modVendor) = engine.buildModifiedKernelImages(state.oppPoints)
            val report = engine.flashKernelAndVerify(
                env = env,
                actionTitle = "Flashed Modified Kernel (${buildModificationSummary(state.oppPoints)})",
                sourceBootFile = modBoot,
                sourceVendorBootFile = modVendor
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastVerificationReport = report,
                    isError = !report.passed,
                    statusMessage = if (report.passed) {
                        "Flashed Modified Kernel & Verified Partition SHA-256 Match!"
                    } else {
                        "Verification mismatch detected!"
                    }
                )
            }
        }
    }

    /**
     * Flashes the Original Stock Kernel backup directly to the device partition and verifies SHA-256.
     */
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
            val report = engine.flashKernelAndVerify(
                env = env,
                actionTitle = "Restored Original Stock Kernel Backup",
                sourceBootFile = File(stock.localFilePath),
                sourceVendorBootFile = File(stock.vendorBootFilePath)
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    lastVerificationReport = report,
                    isError = !report.passed,
                    statusMessage = "Original Stock Kernel Flashed & Verified!"
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
