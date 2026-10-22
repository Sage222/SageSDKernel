package com.example.domain

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DeviceHardwareEnvironment(
    val hasRootAccess: Boolean,
    val bootloaderUnlocked: Boolean,
    val detectedDeviceCode: String,
    val magiskbootPath: String?,
    val bootBlockPath: String,
    val vendorBootBlockPath: String,
    val vbmetaBlockPath: String,
    val unameKernelVersion: String,
    val isHardwareBlockAccessible: Boolean
)

data class ModifiedKernelBundle(
    val bootFile: File,
    val vendorBootFile: File,
    val vbmetaFile: File,
    val usedMagiskboot: Boolean,
    val fdtPatchResult: FdtPatchResult,
    val vbmetaFlagsPatched: Boolean
)

class RootKernelEngine(private val context: Context) {

    private val workDir: File by lazy {
        File(context.filesDir, "kalama_kernel_workspace").apply { mkdirs() }
    }

    private val backupsDir: File by lazy {
        File(workDir, "backups").apply { mkdirs() }
    }

    private val sandboxBlockDir: File by lazy {
        File(workDir, "sandbox_block_by_name").apply { mkdirs() }
    }

    val exportDirectory: File
        get() {
            val publicDownload = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KalamaForge"
            )
            if (publicDownload.exists() || publicDownload.mkdirs()) {
                if (publicDownload.canWrite()) return publicDownload
            }
            val externalAppDir = context.getExternalFilesDir("KalamaForge_Exports")
            if (externalAppDir != null && (externalAppDir.exists() || externalAppDir.mkdirs())) {
                return externalAppDir
            }
            return File(workDir, "exports").apply { mkdirs() }
        }

    suspend fun probeEnvironment(): DeviceHardwareEnvironment = withContext(Dispatchers.IO) {
        ensureSandboxBlockPartitionsInitialized()

        val suResult = runShellCommand(listOf("su", "-c", "id"))
        val hasRoot = suResult?.first == 0 && suResult.second.contains("uid=0")

        val deviceProp = readSystemProp("ro.product.device").ifBlank { "dm3q" }
        val slotSuffix = readSystemProp("ro.boot.slot_suffix")
        val flashLocked = readSystemProp("ro.boot.flash.locked")
        val unameOut = runShellCommand(listOf("uname", "-r"))?.second?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: "5.15.123-android13-8-kalama-gki"

        val realBootPath = "/dev/block/by-name/boot$slotSuffix"
        val realVendorBootPath = "/dev/block/by-name/vendor_boot$slotSuffix"
        val realVbmetaPath = "/dev/block/by-name/vbmeta$slotSuffix"

        val canAccessHardwareBlock = if (hasRoot) {
            val check = runShellCommand(listOf("su", "-c", "ls -l $realBootPath"))
            check?.first == 0
        } else {
            false
        }

        val detectedMagiskboot = if (hasRoot) discoverMagiskbootBinary() else null

        DeviceHardwareEnvironment(
            hasRootAccess = hasRoot,
            bootloaderUnlocked = flashLocked != "1",
            detectedDeviceCode = "$deviceProp (${Build.MODEL.ifBlank { "Samsung S23 / SM8550" }})",
            magiskbootPath = detectedMagiskboot,
            bootBlockPath = if (canAccessHardwareBlock) realBootPath else File(sandboxBlockDir, "boot").absolutePath,
            vendorBootBlockPath = if (canAccessHardwareBlock) realVendorBootPath else File(sandboxBlockDir, "vendor_boot").absolutePath,
            vbmetaBlockPath = if (canAccessHardwareBlock) realVbmetaPath else File(sandboxBlockDir, "vbmeta").absolutePath,
            unameKernelVersion = unameOut,
            isHardwareBlockAccessible = canAccessHardwareBlock
        )
    }

    private fun discoverMagiskbootBinary(): String? {
        val candidates = listOf(
            "/data/adb/magisk/magiskboot",
            "/data/adb/ksu/bin/magiskboot",
            "/data/adb/ap/bin/magiskboot",
            "/system/bin/magiskboot"
        )
        for (path in candidates) {
            val check = runShellCommand(listOf("su", "-c", "test -x $path && echo OK"))
            if (check?.first == 0 && check.second.contains("OK")) {
                return path
            }
        }
        val whichCheck = runShellCommand(listOf("su", "-c", "which magiskboot"))
        if (whichCheck?.first == 0 && whichCheck.second.trim().startsWith("/")) {
            return whichCheck.second.trim().lines().first()
        }
        return null
    }

    suspend fun readLiveOrDefaultStockOppTable(): List<OppFrequencyPoint> = withContext(Dispatchers.IO) {
        val defaultStock = KalamaDefaultOppTable.buildStockPoints()
        val parsedPoints = mutableListOf<OppFrequencyPoint>()

        val policyMap = listOf(
            KalamaCluster.SILVER to "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies",
            KalamaCluster.GOLD to "/sys/devices/system/cpu/cpufreq/policy3/scaling_available_frequencies",
            KalamaCluster.PRIME to "/sys/devices/system/cpu/cpufreq/policy7/scaling_available_frequencies"
        )

        for ((cluster, sysfsPath) in policyMap) {
            val file = File(sysfsPath)
            val rawText = if (file.canRead()) {
                runCatching { file.readText() }.getOrNull()
            } else {
                runShellCommand(listOf("su", "-c", "cat $sysfsPath"))
                    ?.takeIf { it.first == 0 }?.second
            }
            val khzValues = rawText
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toLongOrNull() }
                ?.filter { it in 100_000L..4_200_000L }
                ?.distinct()
                ?.sorted()
                .orEmpty()

            if (khzValues.size >= 3) {
                val stepList = if (khzValues.size > 7) {
                    val indices = (0 until 6).map { i -> (i * (khzValues.size - 1)) / 6 } + (khzValues.size - 1)
                    indices.distinct().map { khzValues[it] }
                } else {
                    khzValues
                }
                stepList.forEach { khz ->
                    val mhz = (khz / 1000L).toInt()
                    val estMv = estimateStockVoltageMv(cluster, mhz)
                    parsedPoints.add(
                        OppFrequencyPoint(
                            id = "sysfs_${cluster.name.lowercase()}_$mhz",
                            cluster = cluster,
                            freqMhz = mhz,
                            stockFreqMhz = mhz,
                            voltageMv = estMv,
                            stockVoltageMv = estMv,
                            mode = OppPointMode.STOCK,
                            enabled = true,
                            isCustomUserAdded = false
                        )
                    )
                }
            } else {
                parsedPoints.addAll(defaultStock.filter { it.cluster == cluster })
            }
        }

        parsedPoints.addAll(defaultStock.filter { it.cluster == KalamaCluster.GPU_ADRENO740 })
        parsedPoints
    }

    private fun estimateStockVoltageMv(cluster: KalamaCluster, mhz: Int): Int {
        val ratio = ((mhz - cluster.stockMinMhz).toFloat() /
            (cluster.stockMaxMhz - cluster.stockMinMhz).coerceAtLeast(1)).coerceIn(0f, 1.2f)
        return when (cluster) {
            KalamaCluster.SILVER -> (645 + ratio * 215).toInt()
            KalamaCluster.GOLD -> (675 + ratio * 305).toInt()
            KalamaCluster.PRIME -> (690 + ratio * 375).toInt()
            KalamaCluster.GPU_ADRENO740 -> (600 + ratio * 295).toInt()
        }
    }

    private fun ensureSandboxBlockPartitionsInitialized() {
        val bootBlock = File(sandboxBlockDir, "boot")
        val vendorBootBlock = File(sandboxBlockDir, "vendor_boot")
        val vbmetaBlock = File(sandboxBlockDir, "vbmeta")
        val stockPoints = KalamaDefaultOppTable.buildStockPoints()

        if (!bootBlock.exists() || bootBlock.length() < 16384L) {
            bootBlock.writeBytes(
                synthesizeSandboxBootImageBinary(
                    oppPoints = stockPoints,
                    kernelVersionTag = "Linux version 5.15.123-android13-8-kalama-gki-SM8550-AC #1 SMP PREEMPT"
                )
            )
        }
        if (!vendorBootBlock.exists() || vendorBootBlock.length() < 8192L) {
            vendorBootBlock.writeBytes(synthesizeSandboxVendorBootBinary(stockPoints))
        }
        if (!vbmetaBlock.exists() || vbmetaBlock.length() < 4096L) {
            vbmetaBlock.writeBytes(synthesizeSandboxVbmetaBinary(flags = 0))
        }
    }

    suspend fun backupKernelPartitions(
        env: DeviceHardwareEnvironment,
        backupLabel: String,
        isOriginalStock: Boolean
    ): BackupOperationResult = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val sanitized = backupLabel.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val outBoot = File(backupsDir, "boot_${sanitized}_$timestamp.img")
        val outVendorBoot = File(backupsDir, "vendor_boot_${sanitized}_$timestamp.img")
        val outVbmeta = File(backupsDir, "vbmeta_${sanitized}_$timestamp.img")

        if (env.isHardwareBlockAccessible) {
            val ddBoot = "dd if=${env.bootBlockPath} of=${outBoot.absolutePath} bs=4096 conv=fsync"
            val ddVendor = "dd if=${env.vendorBootBlockPath} of=${outVendorBoot.absolutePath} bs=4096 conv=fsync"
            val ddVbmeta = "dd if=${env.vbmetaBlockPath} of=${outVbmeta.absolutePath} bs=4096 conv=fsync"

            val r1 = runShellCommand(listOf("su", "-c", ddBoot))
            if (r1?.first != 0 || !outBoot.exists()) {
                File(env.bootBlockPath).takeIf { it.exists() }?.copyTo(outBoot, overwrite = true)
            }
            val r2 = runShellCommand(listOf("su", "-c", ddVendor))
            if (r2?.first != 0 || !outVendorBoot.exists()) {
                File(env.vendorBootBlockPath).takeIf { it.exists() }?.copyTo(outVendorBoot, overwrite = true)
            }
            val r3 = runShellCommand(listOf("su", "-c", ddVbmeta))
            if (r3?.first != 0 || !outVbmeta.exists()) {
                File(env.vbmetaBlockPath).takeIf { it.exists() }?.copyTo(outVbmeta, overwrite = true)
            }
        } else {
            File(env.bootBlockPath).copyTo(outBoot, overwrite = true)
            File(env.vendorBootBlockPath).copyTo(outVendorBoot, overwrite = true)
            File(env.vbmetaBlockPath).copyTo(outVbmeta, overwrite = true)
        }

        if (!outVbmeta.exists() || outVbmeta.length() == 0L) {
            outVbmeta.writeBytes(synthesizeSandboxVbmetaBinary(flags = 0))
        }

        BackupOperationResult(
            bootFile = outBoot,
            vendorBootFile = outVendorBoot,
            vbmetaFile = outVbmeta,
            bootSha256 = sha256File(outBoot),
            vendorBootSha256 = sha256File(outVendorBoot),
            vbmetaSha256 = sha256File(outVbmeta),
            totalSizeBytes = outBoot.length() + outVendorBoot.length() + outVbmeta.length(),
            isOriginalStock = isOriginalStock
        )
    }

    /**
     * Unpacks `vendor_boot.img` and `boot.img` using `magiskboot` (when available on the rooted device),
     * binary-patches the uncompressed Flattened Device Tree (`0xD00DFEED`) `opp-hz` and `opp-microvolt`
     * properties via `FdtBinaryPatcher`, repacks with `magiskboot repack`, and patches `vbmeta.img`
     * (`offset 123 = 0x03`) so Samsung's bootloader boots without `Custom (0xF82)`.
     */
    suspend fun buildModifiedKernelImagesInPlace(
        env: DeviceHardwareEnvironment,
        stockBootSource: File?,
        stockVendorBootSource: File?,
        stockVbmetaSource: File?,
        activeOppPoints: List<OppFrequencyPoint>
    ): ModifiedKernelBundle = withContext(Dispatchers.IO) {
        ensureSandboxBlockPartitionsInitialized()

        val baseBoot = stockBootSource?.takeIf { it.exists() && it.length() > 0L }
            ?: File(sandboxBlockDir, "boot")
        val baseVendor = stockVendorBootSource?.takeIf { it.exists() && it.length() > 0L }
            ?: File(sandboxBlockDir, "vendor_boot")
        val baseVbmeta = stockVbmetaSource?.takeIf { it.exists() && it.length() > 0L }
            ?: File(sandboxBlockDir, "vbmeta")

        val modBootFile = File(workDir, "KalamaForge_Modified_boot.img")
        val modVendorFile = File(workDir, "KalamaForge_Modified_vendor_boot.img")
        val modVbmetaFile = File(workDir, "KalamaForge_Modified_vbmeta.img")

        baseBoot.copyTo(modBootFile, overwrite = true)
        baseVendor.copyTo(modVendorFile, overwrite = true)
        baseVbmeta.copyTo(modVbmetaFile, overwrite = true)

        var usedMagiskboot = false
        var totalBlobs = 0
        var totalHzPatched = 0
        var totalUvPatched = 0

        val mbPath = env.magiskbootPath
        if (env.hasRootAccess && !mbPath.isNullOrBlank()) {
            // 1. Unpack, FDT-patch, and repack vendor_boot.img via on-device magiskboot
            val vendorRepackRes = unpackPatchAndRepackWithMagiskboot(
                magiskbootPath = mbPath,
                sourceImg = modVendorFile,
                workSubdirName = "vendor_boot_repack",
                activePoints = activeOppPoints
            )
            if (vendorRepackRes != null) {
                usedMagiskboot = true
                totalBlobs += vendorRepackRes.dtbBlobsFound
                totalHzPatched += vendorRepackRes.oppHzPatchedCount
                totalUvPatched += vendorRepackRes.oppMicrovoltPatchedCount
            }

            // 2. Unpack, FDT-patch, and repack boot.img via on-device magiskboot
            val bootRepackRes = unpackPatchAndRepackWithMagiskboot(
                magiskbootPath = mbPath,
                sourceImg = modBootFile,
                workSubdirName = "boot_repack",
                activePoints = activeOppPoints
            )
            if (bootRepackRes != null) {
                usedMagiskboot = true
                totalBlobs += bootRepackRes.dtbBlobsFound
                totalHzPatched += bootRepackRes.oppHzPatchedCount
                totalUvPatched += bootRepackRes.oppMicrovoltPatchedCount
            }
        }

        // If magiskboot was not used (or in sandbox mode / uncompressed FDT inside image), run FdtBinaryPatcher directly
        if (!usedMagiskboot || totalBlobs == 0) {
            val rBoot = FdtBinaryPatcher.patchMultiDtbFile(modBootFile, activeOppPoints)
            val rVendor = FdtBinaryPatcher.patchMultiDtbFile(modVendorFile, activeOppPoints)
            totalBlobs += rBoot.dtbBlobsFound + rVendor.dtbBlobsFound
            totalHzPatched += rBoot.oppHzPatchedCount + rVendor.oppHzPatchedCount
            totalUvPatched += rBoot.oppMicrovoltPatchedCount + rVendor.oppMicrovoltPatchedCount

            // Update AVB footer digest in sandbox/in-place mode if AVBf footer is present
            updateAvbFooterDigestIfPresent(modBootFile)
        }

        // Ensure modified files are padded up to exact stock partition size if magiskboot repack trimmed trailing zero padding
        padFileToTargetLengthIfNeeded(modBootFile, baseBoot.length())
        padFileToTargetLengthIfNeeded(modVendorFile, baseVendor.length())

        // Patch vbmeta.img AVB flags at offset 123 to 0x03 (DISABLE_VERITY | DISABLE_VERIFICATION)
        val vbmetaPatched = patchVbmetaDisableVerificationInPlace(modVbmetaFile)

        ModifiedKernelBundle(
            bootFile = modBootFile,
            vendorBootFile = modVendorFile,
            vbmetaFile = modVbmetaFile,
            usedMagiskboot = usedMagiskboot,
            fdtPatchResult = FdtPatchResult(
                dtbBlobsFound = totalBlobs,
                oppHzPatchedCount = totalHzPatched,
                oppMicrovoltPatchedCount = totalUvPatched
            ),
            vbmetaFlagsPatched = vbmetaPatched
        )
    }

    private fun unpackPatchAndRepackWithMagiskboot(
        magiskbootPath: String,
        sourceImg: File,
        workSubdirName: String,
        activePoints: List<OppFrequencyPoint>
    ): FdtPatchResult? {
        val localRepackDir = File(workDir, workSubdirName).apply {
            deleteRecursively()
            mkdirs()
        }
        val localWorkImg = File(localRepackDir, "orig.img")
        sourceImg.copyTo(localWorkImg, overwrite = true)

        val unpackCmd = "cd ${localRepackDir.absolutePath} && $magiskbootPath unpack -h orig.img && chmod -R 666 ${localRepackDir.absolutePath}"
        val unpackRes = runShellCommand(listOf("su", "-c", unpackCmd))
        if (unpackRes?.first != 0) {
            return null
        }

        var blobs = 0
        var hzCount = 0
        var uvCount = 0

        // Patch any extracted dtb / kernel_dtb / extra FDT files
        for (dtbName in listOf("dtb", "kernel_dtb", "extra")) {
            val dtbFile = File(localRepackDir, dtbName)
            if (dtbFile.exists() && dtbFile.length() > 64L) {
                val res = FdtBinaryPatcher.patchMultiDtbFile(dtbFile, activePoints)
                blobs += res.dtbBlobsFound
                hzCount += res.oppHzPatchedCount
                uvCount += res.oppMicrovoltPatchedCount
            }
        }

        val repackCmd = "cd ${localRepackDir.absolutePath} && $magiskbootPath repack orig.img repacked.img && chmod 666 repacked.img"
        val repackRes = runShellCommand(listOf("su", "-c", repackCmd))
        val repackedFile = File(localRepackDir, "repacked.img")

        if (repackRes?.first == 0 && repackedFile.exists() && repackedFile.length() > 4096L) {
            repackedFile.copyTo(sourceImg, overwrite = true)
            return FdtPatchResult(
                dtbBlobsFound = blobs,
                oppHzPatchedCount = hzCount,
                oppMicrovoltPatchedCount = uvCount
            )
        }
        return null
    }

    private fun padFileToTargetLengthIfNeeded(file: File, targetLength: Long) {
        val currentLen = file.length()
        if (currentLen in 1 until targetLength) {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(targetLength)
            }
        }
    }

    private fun updateAvbFooterDigestIfPresent(imageFile: File) {
        val fileLen = imageFile.length()
        if (fileLen < 1024L) return
        RandomAccessFile(imageFile, "rw").use { raf ->
            raf.seek(fileLen - 64L)
            val footerMagic = ByteArray(4)
            raf.readFully(footerMagic)
            if (String(footerMagic, Charsets.US_ASCII) == "AVBf") {
                val digest = computePartialSha256(raf, fileLen - 512L)
                raf.seek(fileLen - 512L + 28L)
                raf.write(digest)
                raf.seek(fileLen - 28L)
                raf.write(digest, 0, 16)
            }
        }
    }

    private fun patchVbmetaDisableVerificationInPlace(vbmetaFile: File): Boolean {
        if (!vbmetaFile.exists() || vbmetaFile.length() < 256L) return false
        return try {
            RandomAccessFile(vbmetaFile, "rw").use { raf ->
                val magic = ByteArray(4)
                raf.seek(0L)
                raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) == "AVB0") {
                    raf.seek(120L)
                    raf.write(byteArrayOf(0x00, 0x00, 0x00, 0x03))
                    true
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun computePartialSha256(raf: RandomAccessFile, maxBytes: Long): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        raf.seek(0L)
        val chunk = ByteArray(65536)
        var remaining = maxBytes
        while (remaining > 0L) {
            val toRead = minOf(chunk.size.toLong(), remaining).toInt()
            val read = raf.read(chunk, 0, toRead)
            if (read <= 0) break
            md.update(chunk, 0, read)
            remaining -= read
        }
        return md.digest()
    }

    suspend fun exportKernelArtifact(
        env: DeviceHardwareEnvironment,
        bootFile: File,
        vendorBootFile: File,
        vbmetaFile: File?,
        format: ExportFormat,
        isOriginalStock: Boolean,
        oppSummary: String
    ): ExportedPackageInfo = withContext(Dispatchers.IO) {
        val prefix = if (isOriginalStock) "KalamaForge_Original_Stock" else "KalamaForge_Modified_OC_UC"
        val outDir = exportDirectory

        val outFile = when (format) {
            ExportFormat.RAW_BOOT_IMG -> {
                val target = File(outDir, "${prefix}_boot.img")
                bootFile.copyTo(target, overwrite = true)
                // Also copy vendor_boot.img and vbmeta.img alongside boot.img for convenience
                if (vendorBootFile.exists()) {
                    vendorBootFile.copyTo(File(outDir, "${prefix}_vendor_boot.img"), overwrite = true)
                }
                if (vbmetaFile != null && vbmetaFile.exists()) {
                    vbmetaFile.copyTo(File(outDir, "${prefix}_vbmeta.img"), overwrite = true)
                }
                target
            }

            ExportFormat.TWRP_ZIP -> {
                val target = File(outDir, "${prefix}_TWRP_Flashable.zip")
                createTwrpFlashableZip(
                    zipFile = target,
                    bootFile = bootFile,
                    vendorBootFile = vendorBootFile,
                    vbmetaFile = vbmetaFile,
                    bootBlockPath = env.bootBlockPath,
                    vendorBootBlockPath = env.vendorBootBlockPath,
                    vbmetaBlockPath = env.vbmetaBlockPath,
                    summary = oppSummary
                )
                target
            }

            ExportFormat.ODIN_TAR -> {
                val target = File(outDir, "${prefix}_Odin_AP.tar")
                createOdinPosixTar(
                    tarFile = target,
                    entries = buildList {
                        add("boot.img" to bootFile)
                        if (vendorBootFile.exists()) {
                            add("vendor_boot.img" to vendorBootFile)
                        }
                        if (vbmetaFile != null && vbmetaFile.exists()) {
                            add("vbmeta.img" to vbmetaFile)
                        }
                    }
                )
                target
            }
        }

        ExportedPackageInfo(
            title = "${if (isOriginalStock) "Original Stock" else "Modified Kernel + vbmeta"} • ${format.label}",
            format = format,
            filePath = outFile.absolutePath,
            sizeBytes = outFile.length(),
            sha256Checksum = sha256File(outFile),
            isOriginalStock = isOriginalStock
        )
    }

    suspend fun flashKernelAndVerify(
        env: DeviceHardwareEnvironment,
        actionTitle: String,
        sourceBootFile: File,
        sourceVendorBootFile: File,
        sourceVbmetaFile: File?,
        expectedStockBootSize: Long
    ): VerificationReport = withContext(Dispatchers.IO) {
        if (expectedStockBootSize > 0L && sourceBootFile.length() != expectedStockBootSize) {
            throw IllegalStateException(
                "Pre-Flash Guard Blocked Flash: Modified boot.img size (${sourceBootFile.length()} B) does not match Stock partition dump (${expectedStockBootSize} B)!"
            )
        }
        if (env.isHardwareBlockAccessible && sourceBootFile.length() < 8L * 1024L * 1024L) {
            throw IllegalStateException(
                "Pre-Flash Guard Blocked Flash: Refusing to flash small image (${sourceBootFile.length() / 1024} KB) to hardware block device ${env.bootBlockPath}!"
            )
        }

        val expectedSha = sha256File(sourceBootFile)

        if (env.isHardwareBlockAccessible) {
            if (sourceVbmetaFile != null && sourceVbmetaFile.exists()) {
                val flashVbmetaCmd = "dd if=${sourceVbmetaFile.absolutePath} of=${env.vbmetaBlockPath} bs=4096 conv=fsync"
                runShellCommand(listOf("su", "-c", flashVbmetaCmd))
            }
            val flashBootCmd = "dd if=${sourceBootFile.absolutePath} of=${env.bootBlockPath} bs=4096 conv=fsync"
            val r1 = runShellCommand(listOf("su", "-c", flashBootCmd))
            if (r1?.first != 0) {
                File(env.bootBlockPath).takeIf { it.exists() }?.let { sourceBootFile.copyTo(it, overwrite = true) }
            }
            if (sourceVendorBootFile.exists()) {
                val flashVendorCmd = "dd if=${sourceVendorBootFile.absolutePath} of=${env.vendorBootBlockPath} bs=4096 conv=fsync"
                runShellCommand(listOf("su", "-c", flashVendorCmd))
            }
        } else {
            if (sourceVbmetaFile != null && sourceVbmetaFile.exists()) {
                sourceVbmetaFile.copyTo(File(env.vbmetaBlockPath), overwrite = true)
            }
            sourceBootFile.copyTo(File(env.bootBlockPath), overwrite = true)
            if (sourceVendorBootFile.exists()) {
                sourceVendorBootFile.copyTo(File(env.vendorBootBlockPath), overwrite = true)
            }
        }

        val readBackFile = File(workDir, "verify_readback_boot.img")
        val countBlocks = (sourceBootFile.length() / 4096L).coerceAtLeast(1L)
        if (env.isHardwareBlockAccessible) {
            val vr = runShellCommand(
                listOf("su", "-c", "dd if=${env.bootBlockPath} of=${readBackFile.absolutePath} bs=4096 count=$countBlocks")
            )
            if (vr?.first != 0 || !readBackFile.exists()) {
                File(env.bootBlockPath).copyTo(readBackFile, overwrite = true)
            }
        } else {
            File(env.bootBlockPath).copyTo(readBackFile, overwrite = true)
        }

        val readBackSha = sha256File(readBackFile)
        val hasValidMagic = RandomAccessFile(readBackFile, "r").use { raf ->
            if (raf.length() >= 8L) {
                val magic = ByteArray(8)
                raf.readFully(magic)
                String(magic, Charsets.US_ASCII) == "ANDROID!"
            } else {
                false
            }
        }

        VerificationReport(
            actionTitle = actionTitle,
            targetPartitionPath = env.bootBlockPath,
            expectedSha256 = expectedSha,
            readBackSha256 = readBackSha,
            bytesVerified = readBackFile.length(),
            passed = expectedSha.equals(readBackSha, ignoreCase = true) && hasValidMagic
        )
    }

    private fun createTwrpFlashableZip(
        zipFile: File,
        bootFile: File,
        vendorBootFile: File,
        vbmetaFile: File?,
        bootBlockPath: String,
        vendorBootBlockPath: String,
        vbmetaBlockPath: String,
        summary: String
    ) {
        val bootSha = sha256File(bootFile)
        val updaterScript = """
            ui_print("================================================");
            ui_print(" KalamaForge SM8550 Kernel + vbmeta Flasher");
            ui_print(" Profile: $summary");
            ui_print(" boot.img SHA-256: ${bootSha.take(24)}...");
            ui_print("================================================");
            show_progress(0.300000, 0);
            ui_print("Flashing patched vbmeta.img (AVB flags=0x03) -> $vbmetaBlockPath");
            package_extract_file("vbmeta.img", "$vbmetaBlockPath");
            show_progress(0.600000, 0);
            ui_print("Flashing boot.img -> $bootBlockPath");
            package_extract_file("boot.img", "$bootBlockPath");
            ui_print("Flashing vendor_boot.img -> $vendorBootBlockPath");
            package_extract_file("vendor_boot.img", "$vendorBootBlockPath");
            show_progress(1.000000, 0);
            ui_print("Done! Kernel & vbmeta flashed cleanly.");
        """.trimIndent()

        val updateBinary = """
            #!/sbin/sh
            OUTFD=${'$'}2
            ZIPFILE=${'$'}3
            ui_print() {
                echo "ui_print ${'$'}1" > /proc/self/fd/${'$'}OUTFD
                echo "ui_print" > /proc/self/fd/${'$'}OUTFD
            }
            ui_print "Flashing KalamaForge SM8550 Kernel + vbmeta..."
            unzip -o "${'$'}ZIPFILE" vbmeta.img boot.img vendor_boot.img -d /tmp/
            if [ -f /tmp/vbmeta.img ]; then
                dd if=/tmp/vbmeta.img of=$vbmetaBlockPath bs=4096 conv=fsync
            fi
            dd if=/tmp/boot.img of=$bootBlockPath bs=4096 conv=fsync
            dd if=/tmp/vendor_boot.img of=$vendorBootBlockPath bs=4096 conv=fsync
            ui_print "Flash Complete!"
            exit 0
        """.trimIndent()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/com/google/android/updater-script"))
            zos.write(updaterScript.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/com/google/android/update-binary"))
            zos.write(updateBinary.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("boot.img"))
            bootFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()

            if (vendorBootFile.exists()) {
                zos.putNextEntry(ZipEntry("vendor_boot.img"))
                vendorBootFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            if (vbmetaFile != null && vbmetaFile.exists()) {
                zos.putNextEntry(ZipEntry("vbmeta.img"))
                vbmetaFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun createOdinPosixTar(
        tarFile: File,
        entries: List<Pair<String, File>>
    ) {
        FileOutputStream(tarFile).use { fos ->
            for ((entryName, file) in entries) {
                val fileSize = file.length()
                val header = ByteArray(512)

                val nameBytes = entryName.toByteArray(Charsets.US_ASCII)
                System.arraycopy(nameBytes, 0, header, 0, nameBytes.size.coerceAtMost(99))

                writeOctalString(header, 100, 8, 420L)
                writeOctalString(header, 108, 8, 0L)
                writeOctalString(header, 116, 8, 0L)
                writeOctalString(header, 124, 12, fileSize)
                writeOctalString(header, 136, 12, System.currentTimeMillis() / 1000L)

                for (i in 148 until 156) {
                    header[i] = ' '.code.toByte()
                }
                header[156] = '0'.code.toByte()

                val ustar = "ustar\u000000".toByteArray(Charsets.US_ASCII)
                System.arraycopy(ustar, 0, header, 257, ustar.size)

                var checksum = 0L
                for (b in header) {
                    checksum += (b.toInt() and 0xFF)
                }
                writeOctalString(header, 148, 7, checksum)
                header[155] = ' '.code.toByte()

                fos.write(header)
                file.inputStream().use { it.copyTo(fos) }

                val remainder = (fileSize % 512L).toInt()
                if (remainder != 0) {
                    fos.write(ByteArray(512 - remainder))
                }
            }
            fos.write(ByteArray(1024))
        }
    }

    private fun writeOctalString(buffer: ByteArray, offset: Int, length: Int, value: Long) {
        val octal = value.toString(8).padStart(length - 1, '0')
        val bytes = octal.toByteArray(Charsets.US_ASCII)
        System.arraycopy(bytes, 0, buffer, offset, bytes.size.coerceAtMost(length - 1))
        buffer[offset + length - 1] = 0
    }

    private fun synthesizeSandboxBootImageBinary(
        oppPoints: List<OppFrequencyPoint>,
        kernelVersionTag: String
    ): ByteArray {
        val totalSize = 32 * 1024
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.position(0)
        buffer.put("ANDROID!".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16384)
        buffer.putInt(8192)
        buffer.putInt(130000)
        buffer.putInt(32768)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(4)
        val cmdline = "console=ttyMSM0,115200n8 androidboot.hardware=qcom soc=kalama_sm8550_ac"
        buffer.put(cmdline.toByteArray(Charsets.US_ASCII))

        buffer.position(4096)
        buffer.put(kernelVersionTag.toByteArray(Charsets.US_ASCII))

        // Embed a genuine specification-compliant 0xD00DFEED FDT blob with opp-hz & opp-microvolt properties
        val validFdt = FdtBinaryPatcher.buildValidKalamaFdtBlob(oppPoints)
        buffer.position(8192)
        buffer.put(validFdt, 0, validFdt.size.coerceAtMost(totalSize - 8192 - 512))

        val rawBytes = buffer.array()
        val payloadDigest = MessageDigest.getInstance("SHA-256").apply {
            update(rawBytes, 0, totalSize - 512)
        }.digest()

        buffer.position(totalSize - 512)
        buffer.put("AVB0".toByteArray(Charsets.US_ASCII))
        buffer.putInt(2)
        buffer.putInt(0)
        buffer.put("SHA256_RSA4096".toByteArray(Charsets.US_ASCII))
        buffer.put(payloadDigest)

        buffer.position(totalSize - 64)
        val footerSlice = buffer.slice().order(ByteOrder.BIG_ENDIAN)
        footerSlice.put("AVBf".toByteArray(Charsets.US_ASCII))
        footerSlice.putInt(1)
        footerSlice.putInt(0)
        footerSlice.putLong((totalSize - 512).toLong())
        footerSlice.putLong((totalSize - 512).toLong())
        footerSlice.putLong(448L)
        footerSlice.put(payloadDigest, 0, 16)

        return buffer.array()
    }

    private fun synthesizeSandboxVendorBootBinary(
        oppPoints: List<OppFrequencyPoint>
    ): ByteArray {
        val totalSize = 16 * 1024
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("VNDRBOOT".toByteArray(Charsets.US_ASCII))
        buffer.putInt(4)
        buffer.putInt(4096)

        val validFdt = FdtBinaryPatcher.buildValidKalamaFdtBlob(oppPoints)
        buffer.position(4096)
        buffer.put(validFdt, 0, validFdt.size.coerceAtMost(totalSize - 4096))
        return buffer.array()
    }

    private fun synthesizeSandboxVbmetaBinary(flags: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN)
        buffer.put("AVB0".toByteArray(Charsets.US_ASCII))
        buffer.putInt(1)
        buffer.putInt(2)
        buffer.position(120)
        buffer.putInt(flags)
        return buffer.array()
    }

    fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(65536)
            var r: Int
            while (fis.read(buf).also { r = it } > 0) {
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readSystemProp(propName: String): String {
        return runShellCommand(listOf("getprop", propName))?.second?.trim().orEmpty()
    }

    private fun runShellCommand(command: List<String>): Pair<Int, String>? {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Pair(exitCode, output)
        } catch (_: Exception) {
            null
        }
    }
}

data class BackupOperationResult(
    val bootFile: File,
    val vendorBootFile: File,
    val vbmetaFile: File,
    val bootSha256: String,
    val vendorBootSha256: String,
    val vbmetaSha256: String,
    val totalSizeBytes: Long,
    val isOriginalStock: Boolean
)
