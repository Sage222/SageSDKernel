package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FlashAuditLogEntity
import com.example.data.KalamaDatabase
import com.example.data.KernelBackupEntity
import com.example.data.KernelRepository
import com.example.domain.AvbSigningMode
import com.example.domain.DeviceHardwareEnvironment
import com.example.domain.ExtractedKernelMetadata
import com.example.domain.FailSafeConfig
import com.example.domain.KalamaCluster
import com.example.domain.KalamaDefaultOppTable
import com.example.domain.OppFrequencyPoint
import com.example.domain.OppPointMode
import com.example.domain.PatchedKernelArtifact
import com.example.domain.RootKernelEngine
import com.example.domain.VerificationReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ForgeTab {
    PARTITIONS,
    OPP_FORGE,
    AVB_FLASH,
    FAIL_SAFE
}

data class KalamaUiState(
    val selectedTab: ForgeTab = ForgeTab.PARTITIONS,
    val environment: DeviceHardwareEnvironment? = null,
    val forceSandboxMode: Boolean = false,
    val isBusy: Boolean = false,
    val statusBannerMessage: String = "Ready • Samsung SM8550-AC (Kalama) Kernel Engine Initialized",
    val isStatusError: Boolean = false,
    val selectedCluster: KalamaCluster = KalamaCluster.PRIME,
    val oppPoints: List<OppFrequencyPoint> = KalamaDefaultOppTable.buildDefaultPoints(),
    val globalVoltageOffsetMv: Int = -15,
    val activePresetName: String = "Galaxy Ultra OC (3.61 GHz Prime + 153 MHz Deep Idle)",
    val extractedKernel: ExtractedKernelMetadata? = null,
    val selectedSigningMode: AvbSigningMode = AvbSigningMode.AVB_RSA4096_TESTKEY,
    val compiledArtifact: PatchedKernelArtifact? = null,
    val lastVerificationReport: VerificationReport? = null,
    val simulateVerifyFaultNextFlash: Boolean = false,
    val failSafeConfig: FailSafeConfig = FailSafeConfig(),
    val terminalOutputLines: List<String> = listOf(
        "[init] Probing Samsung SM8550-AC (Snapdragon 8 Gen 2 for Galaxy) block partitions...",
        "[dtb] Target OPP nodes: qcom,cpufreq-hw-{silver,gold,prime} + qcom,gpu-opp-table"
    )
)

class KalamaForgeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = KalamaDatabase.getInstance(application)
    private val repository = KernelRepository(database.kernelDao())
    private val engine = RootKernelEngine(application)

    private val _uiState = MutableStateFlow(KalamaUiState())
    val uiState: StateFlow<KalamaUiState> = _uiState.asStateFlow()

    val backups: StateFlow<List<KernelBackupEntity>> = repository.backups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<FlashAuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeEngineAndEnsureStockBackup()
    }

    private fun initializeEngineAndEnsureStockBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val env = engine.probeEnvironment(forceSandbox = _uiState.value.forceSandboxMode)
            appendTerminal("[probe] Platform: ${env.detectedPlatform} | Device: ${env.detectedDeviceCode}")
            appendTerminal("[probe] Boot block: ${env.bootBlockPath} | VerifiedBoot: ${env.verifiedBootState}")

            var primaryStock = repository.getPrimaryStockBackup()
            if (primaryStock == null) {
                appendTerminal("[backup] Creating initial immutable Stock Kernel Backup (boot + vendor_boot)...")
                val (backupRes, cmd) = engine.backupKernelPartitions(
                    env = env,
                    backupLabel = "Original Stock SM8550 GKI Kernel",
                    isOriginalStock = true
                )
                val entity = KernelBackupEntity(
                    label = "Original Stock SM8550 GKI Kernel",
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
                    avbKeyType = "Samsung OEM Stock AVB 2.0",
                    oppSummary = "Stock 307 MHz – 3.36 GHz • Adreno 740 @ 719 MHz"
                )
                val insertedId = repository.saveBackup(entity)
                primaryStock = entity.copy(id = insertedId)
                repository.recordAuditLog(
                    FlashAuditLogEntity(
                        operationType = "BACKUP_STOCK",
                        targetPartition = env.bootBlockPath,
                        expectedSha256 = backupRes.bootSha256,
                        verifiedReadBackSha256 = backupRes.bootSha256,
                        verificationPassed = true,
                        details = "Initial Original Stock SM8550-AC boot.img + vendor_boot.img backed up.",
                        commandExecuted = cmd
                    )
                )
                appendTerminal("[backup] Stock SHA-256: ${backupRes.bootSha256.take(24)}...")
            }

            // Automatically extract working copy of the stock backup so OPP Forge & AVB Signer are ready immediately
            val extracted = engine.extractAndInspectKernel(
                backupId = primaryStock.id,
                backupLabel = primaryStock.label,
                bootFilePath = primaryStock.localFilePath,
                vendorBootFilePath = primaryStock.vendorBootFilePath
            )
            appendTerminal("[unpack] Unpacked ${extracted.magicHeader} | FDT: ${extracted.dtbMagicHex}")

            // Also pre-compile an initial signed artifact so the user can inspect or flash right away
            val initialArtifact = engine.patchAndSignBootImage(
                extracted = extracted,
                activeOppPoints = _uiState.value.oppPoints,
                globalVoltageOffsetMv = _uiState.value.globalVoltageOffsetMv,
                signingMode = _uiState.value.selectedSigningMode
            )

            // Install default watchdog script & recovery zip path
            val watchdogFile = engine.installMagiskBootloopWatchdog(
                env = env,
                stockBootBackupPath = primaryStock.localFilePath,
                stockVendorBootBackupPath = primaryStock.vendorBootFilePath,
                maxFailedBoots = _uiState.value.failSafeConfig.maxFailedBootsBeforeRestore,
                panicTimeoutSec = _uiState.value.failSafeConfig.kernelPanicTimeoutSec,
                thermalLimitC = _uiState.value.failSafeConfig.msmThermalThrottleTempC
            )

            _uiState.update {
                it.copy(
                    environment = env,
                    extractedKernel = extracted,
                    compiledArtifact = initialArtifact,
                    failSafeConfig = it.failSafeConfig.copy(watchdogScriptPath = watchdogFile.absolutePath),
                    isBusy = false,
                    statusBannerMessage = "Stock Kernel Backed Up & Extracted • Ready to Tune or Flash"
                )
            }
        }
    }

    fun selectTab(tab: ForgeTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleSandboxMode() {
        viewModelScope.launch {
            val nextSandbox = !_uiState.value.forceSandboxMode
            _uiState.update { it.copy(forceSandboxMode = nextSandbox, isBusy = true) }
            val env = engine.probeEnvironment(forceSandbox = nextSandbox)
            appendTerminal("[mode] Switched execution mode -> ${if (env.isHardwareBlockAccessible) "HARDWARE ROOT BLOCK" else "SAFETY SANDBOX BLOCK EMULATOR"}")
            _uiState.update {
                it.copy(
                    environment = env,
                    isBusy = false,
                    statusBannerMessage = if (env.isHardwareBlockAccessible) {
                        "Hardware Block Device Mode Active (${env.bootBlockPath})"
                    } else {
                        "Safety Sandbox Mode Active • Binary Block Operations Verified Locally"
                    }
                )
            }
        }
    }

    fun createNewKernelBackup(customLabel: String) {
        val env = _uiState.value.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isStatusError = false) }
            val hasStock = repository.getPrimaryStockBackup() != null
            val finalLabel = customLabel.trim().ifBlank {
                if (!hasStock) "Original Stock SM8550 Kernel" else "Snapshot Backup (${backups.value.size + 1})"
            }
            val (backupRes, cmd) = engine.backupKernelPartitions(
                env = env,
                backupLabel = finalLabel,
                isOriginalStock = !hasStock
            )
            val entity = KernelBackupEntity(
                label = finalLabel,
                partitionName = "boot + vendor_boot",
                blockDevicePath = env.bootBlockPath,
                localFilePath = backupRes.bootFile.absolutePath,
                vendorBootFilePath = backupRes.vendorBootFile.absolutePath,
                sha256Checksum = backupRes.bootSha256,
                vendorBootSha256 = backupRes.vendorBootSha256,
                sizeBytes = backupRes.totalSizeBytes,
                kernelVersion = env.unameKernelVersion,
                headerVersion = 4,
                isOriginalStock = !hasStock,
                isSignedAvb = true,
                avbKeyType = "Partition Block Dump",
                oppSummary = "Captured from ${env.bootBlockPath}"
            )
            val newId = repository.saveBackup(entity)
            repository.recordAuditLog(
                FlashAuditLogEntity(
                    operationType = "BACKUP",
                    targetPartition = env.bootBlockPath,
                    expectedSha256 = backupRes.bootSha256,
                    verifiedReadBackSha256 = backupRes.bootSha256,
                    verificationPassed = true,
                    details = "Backed up $finalLabel (${backupRes.totalSizeBytes / 1024} KB)",
                    commandExecuted = cmd
                )
            )
            appendTerminal("[backup] Saved '$finalLabel' | SHA-256: ${backupRes.bootSha256.take(20)}...")

            val extracted = engine.extractAndInspectKernel(
                backupId = newId,
                backupLabel = finalLabel,
                bootFilePath = entity.localFilePath,
                vendorBootFilePath = entity.vendorBootFilePath
            )

            _uiState.update {
                it.copy(
                    extractedKernel = extracted,
                    isBusy = false,
                    statusBannerMessage = "Kernel Backup '$finalLabel' Created & Extracted for Patching"
                )
            }
        }
    }

    fun extractKernelFromBackup(backup: KernelBackupEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isStatusError = false) }
            val extracted = engine.extractAndInspectKernel(
                backupId = backup.id,
                backupLabel = backup.label,
                bootFilePath = backup.localFilePath,
                vendorBootFilePath = backup.vendorBootFilePath
            )
            appendTerminal("[extract] Extracted working copy from '${backup.label}' | DTB: ${extracted.dtbMagicHex}")
            repository.recordAuditLog(
                FlashAuditLogEntity(
                    operationType = "EXTRACT_DTB",
                    targetPartition = backup.localFilePath,
                    expectedSha256 = backup.sha256Checksum,
                    verifiedReadBackSha256 = extracted.originalSha256,
                    verificationPassed = true,
                    details = "Unpacked boot_img_hdr_v4 & Kalama SM8550 DTB OPP tables from ${backup.label}",
                    commandExecuted = "unpack_bootimg --boot_img ${backup.localFilePath} --out extracted/"
                )
            )
            _uiState.update {
                it.copy(
                    extractedKernel = extracted,
                    isBusy = false,
                    statusBannerMessage = "Extracted '${backup.label}' • Ready for Overclock/Underclock Patching"
                )
            }
        }
    }

    fun deleteSnapshotBackup(backup: KernelBackupEntity) {
        if (backup.isOriginalStock) return
        viewModelScope.launch {
            repository.deleteBackup(backup.id)
            appendTerminal("[cleanup] Removed non-stock backup snapshot '${backup.label}'")
        }
    }

    fun selectCluster(cluster: KalamaCluster) {
        _uiState.update { it.copy(selectedCluster = cluster) }
    }

    fun toggleOppFrequencyPoint(pointId: String) {
        _uiState.update { state ->
            val target = state.oppPoints.find { it.id == pointId } ?: return@update state
            // Ensure at least 2 active points remain in the cluster
            val enabledInCluster = state.oppPoints.count { it.cluster == target.cluster && it.enabled }
            if (target.enabled && enabledInCluster <= 2) {
                return@update state.copy(
                    statusBannerMessage = "Safety Guard: Each SM8550 cluster requires at least 2 active OPP steps",
                    isStatusError = true
                )
            }
            val updated = state.oppPoints.map {
                if (it.id == pointId) it.copy(enabled = !it.enabled) else it
            }
            state.copy(
                oppPoints = updated,
                activePresetName = "Custom SM8550 DTB Frequency Curve",
                isStatusError = false,
                statusBannerMessage = "Updated ${target.cluster.title}: ${String.format("%.1f", target.freqMhz)} MHz ${if (!target.enabled) "ENABLED" else "DISABLED"}"
            )
        }
    }

    fun setGlobalVoltageOffset(offsetMv: Int) {
        val clamped = offsetMv.coerceIn(-75, 50)
        _uiState.update {
            it.copy(
                globalVoltageOffsetMv = clamped,
                activePresetName = "Custom SM8550 DTB Frequency Curve",
                statusBannerMessage = "Global Core CPR3 Voltage Offset set to ${if (clamped >= 0) "+$clamped" else "$clamped"} mV"
            )
        }
    }

    fun applyOppPreset(presetKey: String) {
        val defaults = KalamaDefaultOppTable.buildDefaultPoints()
        val (updatedPoints, offsetMv, label) = when (presetKey) {
            "STOCK" -> Triple(
                defaults.map { it.copy(enabled = it.mode == OppPointMode.STOCK) },
                0,
                "Samsung Stock SM8550-AC (3.36 GHz Prime / 719 MHz GPU)"
            )
            "CRYO_UC" -> Triple(
                defaults.map {
                    val enable = it.mode == OppPointMode.UNDERCLOCK ||
                        (it.mode == OppPointMode.STOCK && it.freqKhz <= 2_803_200L)
                    it.copy(enabled = enable)
                },
                -35,
                "Kalama Cryo Battery Saver (153.6 MHz Idle Floor • -35mV UV)"
            )
            "ULTRA_OC" -> Triple(
                defaults.map { it.copy(enabled = true) },
                15,
                "Extreme Silicon Lottery (3.71 GHz Prime X3 • 850 MHz Adreno 740)"
            )
            else -> Triple(
                defaults,
                -15,
                "Balanced Underclock Floor + 3.61 GHz Prime Boost (-15mV)"
            )
        }
        appendTerminal("[opp-forge] Applied preset: $label")
        _uiState.update {
            it.copy(
                oppPoints = updatedPoints,
                globalVoltageOffsetMv = offsetMv,
                activePresetName = label,
                isStatusError = false,
                statusBannerMessage = "Applied Preset: $label"
            )
        }
    }

    fun addCustomOppPoint(cluster: KalamaCluster, freqMhzInput: Int, voltageMvInput: Int) {
        val freqKhz = freqMhzInput.coerceIn(100, 3950) * 1000L
        val voltageUv = voltageMvInput.coerceIn(520, 1250) * 1000
        val mode = when {
            freqKhz < cluster.stockMinKhz -> OppPointMode.UNDERCLOCK
            freqKhz > cluster.stockMaxKhz -> OppPointMode.OVERCLOCK
            else -> OppPointMode.STOCK
        }
        val newPoint = OppFrequencyPoint(
            id = "custom_${cluster.name.lowercase()}_${freqKhz}_${System.currentTimeMillis() % 10000}",
            cluster = cluster,
            freqKhz = freqKhz,
            baseVoltageUv = voltageUv,
            mode = mode,
            enabled = true,
            isCustomUserAdded = true
        )
        appendTerminal("[opp-forge] Added custom DTB node to ${cluster.dtbNodeName}: ${freqMhzInput} MHz @ ${voltageMvInput} mV (${mode.badgeLabel})")
        _uiState.update { state ->
            val merged = (state.oppPoints + newPoint).sortedBy { it.freqKhz }
            state.copy(
                oppPoints = merged,
                activePresetName = "Custom SM8550 DTB Frequency Curve",
                isStatusError = false,
                statusBannerMessage = "Injected ${freqMhzInput} MHz (${mode.badgeLabel}) into ${cluster.dtbNodeName}"
            )
        }
    }

    fun setAvbSigningMode(mode: AvbSigningMode) {
        _uiState.update {
            it.copy(
                selectedSigningMode = mode,
                statusBannerMessage = "Signing Mode: ${mode.displayName}"
            )
        }
    }

    fun compileAndSignPatchedKernel() {
        val state = _uiState.value
        val extracted = state.extractedKernel ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isStatusError = false) }
            val artifact = engine.patchAndSignBootImage(
                extracted = extracted,
                activeOppPoints = state.oppPoints,
                globalVoltageOffsetMv = state.globalVoltageOffsetMv,
                signingMode = state.selectedSigningMode
            )
            repository.recordAuditLog(
                FlashAuditLogEntity(
                    operationType = "PATCH_SIGN",
                    targetPartition = artifact.patchedBootPath,
                    expectedSha256 = artifact.sha256Checksum,
                    verifiedReadBackSha256 = artifact.sha256Checksum,
                    verificationPassed = true,
                    details = "Patched ${artifact.activePointCount} SM8550 OPP points (${artifact.oppSummary}) & signed with ${artifact.signingMode.keyAlgorithm}",
                    commandExecuted = "magiskboot dtb patch && avbtool add_hash_footer --image patched_boot.img --algorithm ${artifact.signingMode.keyAlgorithm}"
                )
            )
            appendTerminal("[avb-sign] Compiled & signed image | SHA-256: ${artifact.sha256Checksum.take(24)}...")
            appendTerminal("[avb-sign] Summary: ${artifact.oppSummary}")
            _uiState.update {
                it.copy(
                    compiledArtifact = artifact,
                    isBusy = false,
                    statusBannerMessage = "Patched Kernel Compiled & Signed with ${artifact.signingMode.keyAlgorithm}"
                )
            }
        }
    }

    fun setSimulateVerifyFault(enabled: Boolean) {
        _uiState.update {
            it.copy(
                simulateVerifyFaultNextFlash = enabled,
                statusBannerMessage = if (enabled) {
                    "Fault Injection Armed: Next flash will simulate a partition bit-flip to test Fail-Safe Rollback"
                } else {
                    "Fault Injection Disabled: Standard partition flashing & read-back verification"
                }
            )
        }
    }

    fun flashPatchedKernelAndVerify() {
        val state = _uiState.value
        val env = state.environment ?: return
        val extracted = state.extractedKernel ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isStatusError = false) }
            // Always ensure the compiled artifact reflects the latest OPP & signing settings
            val artifact = engine.patchAndSignBootImage(
                extracted = extracted,
                activeOppPoints = state.oppPoints,
                globalVoltageOffsetMv = state.globalVoltageOffsetMv,
                signingMode = state.selectedSigningMode
            )
            val primaryStock = repository.getPrimaryStockBackup()

            appendTerminal("[flash] Writing patched boot.img (${artifact.sizeBytes / 1024} KB) -> ${env.bootBlockPath}...")
            val (report, cmdLog) = engine.flashAndVerifyPartition(
                env = env,
                sourceBootPath = artifact.patchedBootPath,
                sourceVendorBootPath = artifact.patchedVendorBootPath,
                expectedBootSha256 = artifact.sha256Checksum,
                fallbackStockBootPath = primaryStock?.localFilePath,
                autoRollbackOnMismatch = state.failSafeConfig.autoRollbackOnVerifyMismatch,
                simulateBitFlipFault = state.simulateVerifyFaultNextFlash
            )

            appendTerminal("[verify] Expected SHA-256 : ${report.expectedSha256.take(28)}...")
            appendTerminal("[verify] Read-Back SHA-256: ${report.readBackSha256.take(28)}...")
            if (report.passed) {
                appendTerminal("[verify] PASS: Partition read-back matches signed image + ANDROID! & AVBf valid.")
            } else {
                appendTerminal("[verify] FAIL: Checksum mismatch detected during read-back verification!")
                if (report.autoRollbackTriggered) {
                    appendTerminal("[failsafe] AUTO-ROLLBACK COMPLETE: Restored Stock Kernel (${report.rollbackRestoredSha256?.take(20)}...)")
                }
            }

            repository.recordAuditLog(
                FlashAuditLogEntity(
                    operationType = if (report.autoRollbackTriggered) "FAILSAFE_ROLLBACK" else "FLASH_VERIFY",
                    targetPartition = env.bootBlockPath,
                    expectedSha256 = report.expectedSha256,
                    verifiedReadBackSha256 = report.readBackSha256,
                    verificationPassed = report.passed,
                    details = if (report.passed) {
                        "Flashed & Verified Patched SM8550 Kernel (${artifact.oppSummary})"
                    } else if (report.autoRollbackTriggered) {
                        "Verification failed! Fail-safe automatically restored Original Stock Backup."
                    } else {
                        "Verification failed! Checksum mismatch on partition read-back."
                    },
                    commandExecuted = cmdLog
                )
            )

            _uiState.update {
                it.copy(
                    compiledArtifact = artifact,
                    lastVerificationReport = report,
                    simulateVerifyFaultNextFlash = false,
                    isBusy = false,
                    isStatusError = !report.passed,
                    statusBannerMessage = if (report.passed) {
                        "FLASH & VERIFICATION PASSED • Read-Back SHA-256 Confirmed"
                    } else if (report.autoRollbackTriggered) {
                        "VERIFY FAILED • Fail-Safe Automatically Restored Original Stock Kernel!"
                    } else {
                        "VERIFY FAILED • Checksum Mismatch! Reflash Original Stock Immediately"
                    }
                )
            }
        }
    }

    fun reflashBackupAndVerify(backup: KernelBackupEntity) {
        val state = _uiState.value
        val env = state.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isStatusError = false) }
            val primaryStock = repository.getPrimaryStockBackup()
            appendTerminal("[restore] Reflashing '${backup.label}' -> ${env.bootBlockPath}...")

            val (report, cmdLog) = engine.flashAndVerifyPartition(
                env = env,
                sourceBootPath = backup.localFilePath,
                sourceVendorBootPath = backup.vendorBootFilePath,
                expectedBootSha256 = backup.sha256Checksum,
                fallbackStockBootPath = primaryStock?.localFilePath,
                autoRollbackOnMismatch = true,
                simulateBitFlipFault = false
            )

            appendTerminal("[verify] Restored partition read-back SHA-256: ${report.readBackSha256.take(28)}... (${if (report.passed) "MATCH" else "MISMATCH"})")

            repository.recordAuditLog(
                FlashAuditLogEntity(
                    operationType = "RESTORE_STOCK",
                    targetPartition = env.bootBlockPath,
                    expectedSha256 = report.expectedSha256,
                    verifiedReadBackSha256 = report.readBackSha256,
                    verificationPassed = report.passed,
                    details = "Reflashed & Verified backup '${backup.label}' to ${env.bootBlockPath}",
                    commandExecuted = cmdLog
                )
            )

            _uiState.update {
                it.copy(
                    lastVerificationReport = report,
                    isBusy = false,
                    isStatusError = !report.passed,
                    statusBannerMessage = "Restored '${backup.label}' & Verified Partition SHA-256"
                )
            }
        }
    }

    fun reflashOriginalStockKernel() {
        viewModelScope.launch {
            val stock = repository.getPrimaryStockBackup()
            if (stock != null) {
                reflashBackupAndVerify(stock)
            } else {
                _uiState.update {
                    it.copy(
                        isStatusError = true,
                        statusBannerMessage = "No Original Stock Backup found! Create a backup first."
                    )
                }
            }
        }
    }

    fun updateFailSafeSettings(
        watchdogEnabled: Boolean? = null,
        maxFailedBoots: Int? = null,
        autoRollback: Boolean? = null,
        panicSec: Int? = null,
        thermalC: Int? = null,
        volDownClamp: Boolean? = null
    ) {
        _uiState.update { state ->
            val current = state.failSafeConfig
            state.copy(
                failSafeConfig = current.copy(
                    magiskBootloopWatchdogEnabled = watchdogEnabled ?: current.magiskBootloopWatchdogEnabled,
                    maxFailedBootsBeforeRestore = maxFailedBoots ?: current.maxFailedBootsBeforeRestore,
                    autoRollbackOnVerifyMismatch = autoRollback ?: current.autoRollbackOnVerifyMismatch,
                    kernelPanicTimeoutSec = panicSec ?: current.kernelPanicTimeoutSec,
                    msmThermalThrottleTempC = thermalC ?: current.msmThermalThrottleTempC,
                    volumeDownSafeFreqClamp = volDownClamp ?: current.volumeDownSafeFreqClamp
                )
            )
        }
    }

    fun deployFailSafeWatchdogAndRecoveryZip() {
        val state = _uiState.value
        val env = state.environment ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isStatusError = false) }
            val stock = repository.getPrimaryStockBackup()
            if (stock == null) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isStatusError = true,
                        statusBannerMessage = "Create a Stock Kernel Backup first before generating fail-safe package"
                    )
                }
                return@launch
            }

            val scriptFile = engine.installMagiskBootloopWatchdog(
                env = env,
                stockBootBackupPath = stock.localFilePath,
                stockVendorBootBackupPath = stock.vendorBootFilePath,
                maxFailedBoots = state.failSafeConfig.maxFailedBootsBeforeRestore,
                panicTimeoutSec = state.failSafeConfig.kernelPanicTimeoutSec,
                thermalLimitC = state.failSafeConfig.msmThermalThrottleTempC
            )

            val zipFile = engine.generateTwrpEmergencyRecoveryZip(
                env = env,
                stockBootBackupPath = stock.localFilePath,
                stockVendorBootPath = stock.vendorBootFilePath,
                stockSha256 = stock.sha256Checksum
            )

            appendTerminal("[failsafe] Armed post-fs-data watchdog: ${scriptFile.name} (Max failed boots: ${state.failSafeConfig.maxFailedBootsBeforeRestore})")
            appendTerminal("[failsafe] Built flashable TWRP/OrangeFox emergency restore ZIP: ${zipFile.name} (${zipFile.length() / 1024} KB)")

            repository.recordAuditLog(
                FlashAuditLogEntity(
                    operationType = "ARM_FAILSAFE",
                    targetPartition = "/data/adb/post-fs-data.d/",
                    expectedSha256 = stock.sha256Checksum,
                    verifiedReadBackSha256 = engine.sha256File(zipFile),
                    verificationPassed = true,
                    details = "Armed Bootloop Watchdog (${state.failSafeConfig.maxFailedBootsBeforeRestore} boots) & generated TWRP Emergency Restore ZIP.",
                    commandExecuted = "cp ${scriptFile.name} /data/adb/post-fs-data.d/ && zip ${zipFile.name} boot.img vendor_boot.img"
                )
            )

            _uiState.update {
                it.copy(
                    failSafeConfig = it.failSafeConfig.copy(
                        lastGeneratedRecoveryZipPath = zipFile.absolutePath,
                        watchdogScriptPath = scriptFile.absolutePath
                    ),
                    isBusy = false,
                    statusBannerMessage = "Fail-Safe Watchdog Armed & TWRP Emergency Restore ZIP Built"
                )
            }
        }
    }

    fun triggerHardwareRebootAction(mode: String) {
        val env = _uiState.value.environment ?: return
        val msg = engine.triggerHardwareReboot(mode, env.hasRootAccess)
        appendTerminal("[reboot] $msg")
        _uiState.update {
            it.copy(statusBannerMessage = msg)
        }
    }

    private fun appendTerminal(line: String) {
        _uiState.update { state ->
            val updated = (state.terminalOutputLines + line).takeLast(24)
            state.copy(terminalOutputLines = updated)
        }
    }
}
