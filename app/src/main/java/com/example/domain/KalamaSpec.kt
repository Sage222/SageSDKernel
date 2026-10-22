package com.example.domain

enum class KalamaCluster(
    val id: Int,
    val title: String,
    val dtbNodeName: String,
    val coreTopology: String,
    val microArch: String,
    val stockMinKhz: Long,
    val stockMaxKhz: Long
) {
    SILVER(
        id = 0,
        title = "Cluster 0 • Silver Efficiency",
        dtbNodeName = "qcom,cpufreq-hw-silver",
        coreTopology = "Cores 0–2 (3x Eff)",
        microArch = "ARM Cortex-A510",
        stockMinKhz = 307_200L,
        stockMaxKhz = 2_016_000L
    ),
    GOLD(
        id = 1,
        title = "Cluster 1 • Gold Performance",
        dtbNodeName = "qcom,cpufreq-hw-gold",
        coreTopology = "Cores 3–6 (2x A715 + 2x A710)",
        microArch = "ARM Cortex-A715 / A710",
        stockMinKhz = 499_200L,
        stockMaxKhz = 2_803_200L
    ),
    PRIME(
        id = 2,
        title = "Cluster 2 • Prime X3 (For Galaxy)",
        dtbNodeName = "qcom,cpufreq-hw-prime",
        coreTopology = "Core 7 (1x Prime AC)",
        microArch = "ARM Cortex-X3 @ 3.36 GHz",
        stockMinKhz = 595_200L,
        stockMaxKhz = 3_360_000L
    ),
    GPU_ADRENO740(
        id = 3,
        title = "Adreno 740 GPU • Kalama AC",
        dtbNodeName = "qcom,gpu-opp-table",
        coreTopology = "Ray-Tracing Shader Array",
        microArch = "Adreno 740 @ 719 MHz",
        stockMinKhz = 225_000L,
        stockMaxKhz = 719_000L
    )
}

enum class OppPointMode(val badgeLabel: String) {
    UNDERCLOCK("UNDERCLOCK"),
    STOCK("STOCK"),
    OVERCLOCK("OVERCLOCK")
}

data class OppFrequencyPoint(
    val id: String,
    val cluster: KalamaCluster,
    val freqKhz: Long,
    val baseVoltageUv: Int,
    val mode: OppPointMode,
    val enabled: Boolean,
    val isCustomUserAdded: Boolean = false
) {
    val freqMhz: Double get() = freqKhz / 1000.0
    val baseVoltageMv: Int get() = baseVoltageUv / 1000

    fun effectiveVoltageMv(globalOffsetMv: Int): Int {
        return (baseVoltageMv + globalOffsetMv).coerceIn(500, 1280)
    }
}

enum class AvbSigningMode(
    val displayName: String,
    val keyAlgorithm: String,
    val vbmetaFlagDesc: String
) {
    AVB_RSA4096_TESTKEY(
        displayName = "AVB 2.0 RSA-4096 TestKey + SHA256 Footer",
        keyAlgorithm = "SHA256_RSA4096",
        vbmetaFlagDesc = "Standard unlocked bootloader AVB 2.0 footer signing (Odin/Heimdall compatible)"
    ),
    AVB_CUSTOM_OEM_KEY(
        displayName = "Custom Root Key + Chained vendor_boot Hash",
        keyAlgorithm = "SHA512_RSA4096",
        vbmetaFlagDesc = "Signs both boot.img and vendor_boot.img with chained descriptor digest"
    ),
    SAMSUNG_VBMETA_DISABLED(
        displayName = "Samsung Knox / vbmeta Verification Disabled (--flags 2)",
        keyAlgorithm = "SHA256_RSA2048_NOP",
        vbmetaFlagDesc = "Appends AVBf footer with verification-disabled flags for Samsung unlocked bootloaders"
    )
}

data class FailSafeConfig(
    val magiskBootloopWatchdogEnabled: Boolean = true,
    val maxFailedBootsBeforeRestore: Int = 2,
    val autoRollbackOnVerifyMismatch: Boolean = true,
    val kernelPanicTimeoutSec: Int = 5,
    val msmThermalThrottleTempC: Int = 92,
    val volumeDownSafeFreqClamp: Boolean = true,
    val lastGeneratedRecoveryZipPath: String? = null,
    val watchdogScriptPath: String? = null
)

data class ExtractedKernelMetadata(
    val sourceBackupId: Long,
    val sourceLabel: String,
    val magicHeader: String,
    val headerVersion: Int,
    val pageSize: Int,
    val kernelSizeBytes: Int,
    val ramdiskSizeBytes: Int,
    val dtbMagicHex: String,
    val socBoardName: String,
    val kernelCmdline: String,
    val originalSha256: String,
    val extractedFilePath: String,
    val extractedVendorBootPath: String
)

data class PatchedKernelArtifact(
    val sourceBackupId: Long,
    val patchedBootPath: String,
    val patchedVendorBootPath: String,
    val sha256Checksum: String,
    val vendorBootSha256: String,
    val sizeBytes: Long,
    val signingMode: AvbSigningMode,
    val avbFooterDigestHex: String,
    val oppSummary: String,
    val activePointCount: Int,
    val underclockFloorMhz: String,
    val overclockPeakMhz: String,
    val globalVoltageOffsetMv: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class VerificationReport(
    val targetPartitionPath: String,
    val flashedImagePath: String,
    val expectedSha256: String,
    val readBackSha256: String,
    val bytesVerified: Long,
    val headerMagicValid: Boolean,
    val avbFooterValid: Boolean,
    val passed: Boolean,
    val autoRollbackTriggered: Boolean,
    val rollbackRestoredSha256: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object KalamaDefaultOppTable {
    fun buildDefaultPoints(): List<OppFrequencyPoint> = listOf(
        // Cluster 0: Silver (Cortex-A510)
        OppFrequencyPoint("s_uc_153", KalamaCluster.SILVER, 153_600L, 605_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("s_uc_230", KalamaCluster.SILVER, 230_400L, 625_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("s_st_307", KalamaCluster.SILVER, 307_200L, 645_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("s_st_614", KalamaCluster.SILVER, 614_400L, 680_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("s_st_1094", KalamaCluster.SILVER, 1_094_400L, 735_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("s_st_1555", KalamaCluster.SILVER, 1_555_200L, 795_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("s_st_2016", KalamaCluster.SILVER, 2_016_000L, 860_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("s_oc_2150", KalamaCluster.SILVER, 2_150_400L, 895_000, OppPointMode.OVERCLOCK, enabled = true),
        OppFrequencyPoint("s_oc_2265", KalamaCluster.SILVER, 2_265_600L, 930_000, OppPointMode.OVERCLOCK, enabled = false),

        // Cluster 1: Gold (Cortex-A715 / A710)
        OppFrequencyPoint("g_uc_345", KalamaCluster.GOLD, 345_600L, 635_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("g_uc_422", KalamaCluster.GOLD, 422_400L, 655_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("g_st_499", KalamaCluster.GOLD, 499_200L, 675_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("g_st_1152", KalamaCluster.GOLD, 1_152_000L, 745_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("g_st_1881", KalamaCluster.GOLD, 1_881_600L, 835_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("g_st_2400", KalamaCluster.GOLD, 2_400_000L, 915_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("g_st_2803", KalamaCluster.GOLD, 2_803_200L, 980_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("g_oc_2956", KalamaCluster.GOLD, 2_956_800L, 1_015_000, OppPointMode.OVERCLOCK, enabled = true),
        OppFrequencyPoint("g_oc_3072", KalamaCluster.GOLD, 3_072_000L, 1_055_000, OppPointMode.OVERCLOCK, enabled = false),

        // Cluster 2: Prime X3 (Cortex-X3 "For Galaxy" 3.36 GHz)
        OppFrequencyPoint("p_uc_384", KalamaCluster.PRIME, 384_000L, 645_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("p_uc_480", KalamaCluster.PRIME, 480_000L, 665_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("p_st_595", KalamaCluster.PRIME, 595_200L, 690_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("p_st_1401", KalamaCluster.PRIME, 1_401_600L, 780_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("p_st_2246", KalamaCluster.PRIME, 2_246_400L, 890_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("p_st_2995", KalamaCluster.PRIME, 2_995_200L, 1_005_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("p_st_3360", KalamaCluster.PRIME, 3_360_000L, 1_065_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("p_oc_3494", KalamaCluster.PRIME, 3_494_400L, 1_100_000, OppPointMode.OVERCLOCK, enabled = true),
        OppFrequencyPoint("p_oc_3609", KalamaCluster.PRIME, 3_609_600L, 1_145_000, OppPointMode.OVERCLOCK, enabled = true),
        OppFrequencyPoint("p_oc_3705", KalamaCluster.PRIME, 3_705_600L, 1_190_000, OppPointMode.OVERCLOCK, enabled = false),

        // Cluster 3: Adreno 740 GPU (SM8550-AC 719 MHz Stock)
        OppFrequencyPoint("gpu_uc_135", KalamaCluster.GPU_ADRENO740, 135_000L, 555_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("gpu_uc_180", KalamaCluster.GPU_ADRENO740, 180_000L, 575_000, OppPointMode.UNDERCLOCK, enabled = true),
        OppFrequencyPoint("gpu_st_225", KalamaCluster.GPU_ADRENO740, 225_000L, 600_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("gpu_st_450", KalamaCluster.GPU_ADRENO740, 450_000L, 720_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("gpu_st_600", KalamaCluster.GPU_ADRENO740, 600_000L, 815_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("gpu_st_719", KalamaCluster.GPU_ADRENO740, 719_000L, 895_000, OppPointMode.STOCK, enabled = true),
        OppFrequencyPoint("gpu_oc_785", KalamaCluster.GPU_ADRENO740, 785_000L, 945_000, OppPointMode.OVERCLOCK, enabled = true),
        OppFrequencyPoint("gpu_oc_850", KalamaCluster.GPU_ADRENO740, 850_000L, 995_000, OppPointMode.OVERCLOCK, enabled = false)
    )
}
