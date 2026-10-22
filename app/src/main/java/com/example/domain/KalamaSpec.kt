package com.example.domain

enum class KalamaCluster(
    val id: Int,
    val shortName: String,
    val title: String,
    val coresDescription: String,
    val stockMinMhz: Int,
    val stockMaxMhz: Int,
    val suggestedUnderclockMhz: Int,
    val suggestedUnderclockMv: Int,
    val suggestedOverclockMhz: Int,
    val suggestedOverclockMv: Int
) {
    PRIME(
        id = 2,
        shortName = "Prime X3",
        title = "Prime Core (Cortex-X3)",
        coresDescription = "Core 7 • Peak Single-Core Speed",
        stockMinMhz = 595,
        stockMaxMhz = 3360,
        suggestedUnderclockMhz = 420,
        suggestedUnderclockMv = 655,
        suggestedOverclockMhz = 3550,
        suggestedOverclockMv = 1110
    ),
    GOLD(
        id = 1,
        shortName = "Gold Cores",
        title = "Performance Cores (A715 / A710)",
        coresDescription = "Cores 3–6 • Heavy Multitasking & Gaming",
        stockMinMhz = 499,
        stockMaxMhz = 2803,
        suggestedUnderclockMhz = 345,
        suggestedUnderclockMv = 635,
        suggestedOverclockMhz = 2995,
        suggestedOverclockMv = 1020
    ),
    SILVER(
        id = 0,
        shortName = "Silver Cores",
        title = "Efficiency Cores (Cortex-A510)",
        coresDescription = "Cores 0–2 • Background & Idle Battery Life",
        stockMinMhz = 307,
        stockMaxMhz = 2016,
        suggestedUnderclockMhz = 172,
        suggestedUnderclockMv = 610,
        suggestedOverclockMhz = 2188,
        suggestedOverclockMv = 900
    ),
    GPU_ADRENO740(
        id = 3,
        shortName = "Adreno 740 GPU",
        title = "Adreno 740 Graphics (For Galaxy)",
        coresDescription = "GPU • 3D Rendering & Ray Tracing",
        stockMinMhz = 225,
        stockMaxMhz = 719,
        suggestedUnderclockMhz = 150,
        suggestedUnderclockMv = 565,
        suggestedOverclockMhz = 800,
        suggestedOverclockMv = 955
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
    val freqMhz: Int,
    val stockFreqMhz: Int?,
    val voltageMv: Int,
    val stockVoltageMv: Int?,
    val mode: OppPointMode,
    val enabled: Boolean = true,
    val isCustomUserAdded: Boolean = false
) {
    val freqKhz: Long get() = freqMhz * 1000L
    val voltageDeltaFromStockMv: Int
        get() = if (stockVoltageMv != null) voltageMv - stockVoltageMv else 0
}

enum class ExportFormat(val label: String, val extension: String) {
    RAW_BOOT_IMG("Raw Kernel Image (boot.img)", ".img"),
    TWRP_ZIP("TWRP Flashable ZIP", ".zip"),
    ODIN_TAR("Samsung Odin AP Package (.tar)", ".tar")
}

data class ExportedPackageInfo(
    val title: String,
    val format: ExportFormat,
    val filePath: String,
    val sizeBytes: Long,
    val sha256Checksum: String,
    val isOriginalStock: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class VerificationReport(
    val actionTitle: String,
    val targetPartitionPath: String,
    val expectedSha256: String,
    val readBackSha256: String,
    val bytesVerified: Long,
    val passed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object KalamaDefaultOppTable {
    /**
     * Clean, easy-to-understand Samsung Snapdragon 8 Gen 2 (SM8550-AC) stock frequency & voltage table.
     * Users can immediately see the stock frequencies/voltages, adjust any step's voltage or frequency
     * independently, or add Underclock and Overclock steps.
     */
    fun buildStockPoints(): List<OppFrequencyPoint> = listOf(
        // Prime X3 Core (Stock: 595 MHz - 3360 MHz)
        stockPt("p_595", KalamaCluster.PRIME, 595, 690),
        stockPt("p_1056", KalamaCluster.PRIME, 1056, 740),
        stockPt("p_1651", KalamaCluster.PRIME, 1651, 815),
        stockPt("p_2246", KalamaCluster.PRIME, 2246, 890),
        stockPt("p_2803", KalamaCluster.PRIME, 2803, 975),
        stockPt("p_3187", KalamaCluster.PRIME, 3187, 1035),
        stockPt("p_3360", KalamaCluster.PRIME, 3360, 1065),

        // Gold Performance Cores (Stock: 499 MHz - 2803 MHz)
        stockPt("g_499", KalamaCluster.GOLD, 499, 675),
        stockPt("g_960", KalamaCluster.GOLD, 960, 725),
        stockPt("g_1497", KalamaCluster.GOLD, 1497, 790),
        stockPt("g_1996", KalamaCluster.GOLD, 1996, 855),
        stockPt("g_2400", KalamaCluster.GOLD, 2400, 915),
        stockPt("g_2803", KalamaCluster.GOLD, 2803, 980),

        // Silver Efficiency Cores (Stock: 307 MHz - 2016 MHz)
        stockPt("s_307", KalamaCluster.SILVER, 307, 645),
        stockPt("s_614", KalamaCluster.SILVER, 614, 680),
        stockPt("s_1094", KalamaCluster.SILVER, 1094, 735),
        stockPt("s_1555", KalamaCluster.SILVER, 1555, 795),
        stockPt("s_2016", KalamaCluster.SILVER, 2016, 860),

        // Adreno 740 GPU (Stock: 225 MHz - 719 MHz)
        stockPt("gpu_225", KalamaCluster.GPU_ADRENO740, 225, 600),
        stockPt("gpu_345", KalamaCluster.GPU_ADRENO740, 345, 665),
        stockPt("gpu_450", KalamaCluster.GPU_ADRENO740, 450, 720),
        stockPt("gpu_600", KalamaCluster.GPU_ADRENO740, 600, 815),
        stockPt("gpu_719", KalamaCluster.GPU_ADRENO740, 719, 895)
    )

    private fun stockPt(
        id: String,
        cluster: KalamaCluster,
        freqMhz: Int,
        voltageMv: Int
    ) = OppFrequencyPoint(
        id = id,
        cluster = cluster,
        freqMhz = freqMhz,
        stockFreqMhz = freqMhz,
        voltageMv = voltageMv,
        stockVoltageMv = voltageMv,
        mode = OppPointMode.STOCK,
        enabled = true,
        isCustomUserAdded = false
    )
}
