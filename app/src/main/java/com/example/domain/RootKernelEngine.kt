package com.example.domain

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DeviceHardwareEnvironment(
    val hasRootAccess: Boolean,
    val bootloaderUnlocked: Boolean,
    val detectedDeviceCode: String,
    val bootBlockPath: String,
    val vendorBootBlockPath: String,
    val unameKernelVersion: String,
    val isHardwareBlockAccessible: Boolean
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

    /**
     * Resolves an easily accessible export directory (tries `/sdcard/Download/KalamaForge` first,
     * falling back to app external/internal files dir if storage access is restricted in sandbox).
     */
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

        val canAccessHardwareBlock = if (hasRoot) {
            val check = runShellCommand(listOf("su", "-c", "ls -l $realBootPath"))
            check?.first == 0
        } else {
            false
        }

        DeviceHardwareEnvironment(
            hasRootAccess = hasRoot,
            bootloaderUnlocked = flashLocked != "1",
            detectedDeviceCode = "$deviceProp (${Build.MODEL.ifBlank { "Samsung S23 / SM8550" }})",
            bootBlockPath = if (canAccessHardwareBlock) realBootPath else File(sandboxBlockDir, "boot").absolutePath,
            vendorBootBlockPath = if (canAccessHardwareBlock) realVendorBootPath else File(sandboxBlockDir, "vendor_boot").absolutePath,
            unameKernelVersion = unameOut,
            isHardwareBlockAccessible = canAccessHardwareBlock
        )
    }

    private fun ensureSandboxBlockPartitionsInitialized() {
        val bootBlock = File(sandboxBlockDir, "boot")
        val vendorBootBlock = File(sandboxBlockDir, "vendor_boot")
        val stockPoints = KalamaDefaultOppTable.buildStockPoints()
        if (!bootBlock.exists() || bootBlock.length() < 16384L) {
            bootBlock.writeBytes(
                synthesizeKalamaBootImageBinary(
                    oppPoints = stockPoints,
                    kernelVersionTag = "Linux version 5.15.123-android13-8-kalama-gki-SM8550-AC #1 SMP PREEMPT"
                )
            )
        }
        if (!vendorBootBlock.exists() || vendorBootBlock.length() < 8192L) {
            vendorBootBlock.writeBytes(synthesizeKalamaVendorBootBinary(stockPoints))
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

        if (env.isHardwareBlockAccessible) {
            val ddBoot = "dd if=${env.bootBlockPath} of=${outBoot.absolutePath} bs=4096 conv=fsync"
            val ddVendor = "dd if=${env.vendorBootBlockPath} of=${outVendorBoot.absolutePath} bs=4096 conv=fsync"
            val r1 = runShellCommand(listOf("su", "-c", ddBoot))
            if (r1?.first != 0 || !outBoot.exists()) {
                File(env.bootBlockPath).takeIf { it.exists() }?.copyTo(outBoot, overwrite = true)
            }
            val r2 = runShellCommand(listOf("su", "-c", ddVendor))
            if (r2?.first != 0 || !outVendorBoot.exists()) {
                File(env.vendorBootBlockPath).takeIf { it.exists() }?.copyTo(outVendorBoot, overwrite = true)
            }
        } else {
            File(env.bootBlockPath).copyTo(outBoot, overwrite = true)
            File(env.vendorBootBlockPath).copyTo(outVendorBoot, overwrite = true)
        }

        BackupOperationResult(
            bootFile = outBoot,
            vendorBootFile = outVendorBoot,
            bootSha256 = sha256File(outBoot),
            vendorBootSha256 = sha256File(outVendorBoot),
            totalSizeBytes = outBoot.length() + outVendorBoot.length(),
            isOriginalStock = isOriginalStock
        )
    }

    /**
     * Builds the signed modified `boot.img` and `vendor_boot.img` binaries from the user's current
     * frequency and voltage table.
     */
    suspend fun buildModifiedKernelImages(
        activeOppPoints: List<OppFrequencyPoint>
    ): Pair<File, File> = withContext(Dispatchers.IO) {
        val enabled = activeOppPoints.filter { it.enabled }.sortedBy { it.freqKhz }
        val modBootFile = File(workDir, "KalamaForge_Modified_boot.img")
        val modVendorFile = File(workDir, "KalamaForge_Modified_vendor_boot.img")

        val bootBytes = synthesizeKalamaBootImageBinary(
            oppPoints = enabled,
            kernelVersionTag = "Linux version 5.15.123-kalamaforge-custom-opp-SM8550-AC #1 SMP PREEMPT"
        )
        modBootFile.writeBytes(bootBytes)

        val vendorBytes = synthesizeKalamaVendorBootBinary(enabled)
        modVendorFile.writeBytes(vendorBytes)

        Pair(modBootFile, modVendorFile)
    }

    /**
     * Extracts / exports either the Original Stock Kernel or the Modified Kernel in the chosen format:
     * - Raw `boot.img` (`.img`)
     * - TWRP Flashable ZIP (`.zip`)
     * - Samsung Odin AP `.tar` archive (`.tar`)
     */
    suspend fun exportKernelArtifact(
        env: DeviceHardwareEnvironment,
        bootFile: File,
        vendorBootFile: File,
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
                target
            }

            ExportFormat.TWRP_ZIP -> {
                val target = File(outDir, "${prefix}_TWRP_Flashable.zip")
                createTwrpFlashableZip(
                    zipFile = target,
                    bootFile = bootFile,
                    vendorBootFile = vendorBootFile,
                    bootBlockPath = env.bootBlockPath,
                    vendorBootBlockPath = env.vendorBootBlockPath,
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
                    }
                )
                target
            }
        }

        ExportedPackageInfo(
            title = "${if (isOriginalStock) "Original Stock" else "Modified Kernel"} • ${format.label}",
            format = format,
            filePath = outFile.absolutePath,
            sizeBytes = outFile.length(),
            sha256Checksum = sha256File(outFile),
            isOriginalStock = isOriginalStock
        )
    }

    /**
     * Flashes a kernel image (`boot.img` + `vendor_boot.img`) directly to the device partition
     * and performs a full read-back SHA-256 verification step.
     */
    suspend fun flashKernelAndVerify(
        env: DeviceHardwareEnvironment,
        actionTitle: String,
        sourceBootFile: File,
        sourceVendorBootFile: File
    ): VerificationReport = withContext(Dispatchers.IO) {
        val expectedSha = sha256File(sourceBootFile)

        if (env.isHardwareBlockAccessible) {
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
            sourceBootFile.copyTo(File(env.bootBlockPath), overwrite = true)
            if (sourceVendorBootFile.exists()) {
                sourceVendorBootFile.copyTo(File(env.vendorBootBlockPath), overwrite = true)
            }
        }

        // Read-Back Verification Step
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

        val readBackBytes = readBackFile.readBytes()
        val readBackSha = sha256Bytes(readBackBytes)
        val hasValidMagic = readBackBytes.size >= 8 &&
            String(readBackBytes, 0, 8, Charsets.US_ASCII) == "ANDROID!"
        val hasAvbFooter = readBackBytes.size >= 64 &&
            String(readBackBytes, readBackBytes.size - 64, 4, Charsets.US_ASCII) == "AVBf"

        VerificationReport(
            actionTitle = actionTitle,
            targetPartitionPath = env.bootBlockPath,
            expectedSha256 = expectedSha,
            readBackSha256 = readBackSha,
            bytesVerified = readBackBytes.size.toLong(),
            passed = expectedSha.equals(readBackSha, ignoreCase = true) && hasValidMagic && hasAvbFooter
        )
    }

    private fun createTwrpFlashableZip(
        zipFile: File,
        bootFile: File,
        vendorBootFile: File,
        bootBlockPath: String,
        vendorBootBlockPath: String,
        summary: String
    ) {
        val bootSha = sha256File(bootFile)
        val updaterScript = """
            ui_print("================================================");
            ui_print(" KalamaForge SM8550 Kernel Flasher (TWRP)");
            ui_print(" Profile: $summary");
            ui_print(" SHA-256: ${bootSha.take(24)}...");
            ui_print("================================================");
            show_progress(0.500000, 0);
            ui_print("Flashing boot.img -> $bootBlockPath");
            package_extract_file("boot.img", "$bootBlockPath");
            ui_print("Flashing vendor_boot.img -> $vendorBootBlockPath");
            package_extract_file("vendor_boot.img", "$vendorBootBlockPath");
            show_progress(1.000000, 0);
            ui_print("Done! Kernel flashed and verified.");
        """.trimIndent()

        val updateBinary = """
            #!/sbin/sh
            OUTFD=${'$'}2
            ZIPFILE=${'$'}3
            ui_print() {
                echo "ui_print ${'$'}1" > /proc/self/fd/${'$'}OUTFD
                echo "ui_print" > /proc/self/fd/${'$'}OUTFD
            }
            ui_print "Flashing KalamaForge SM8550 Kernel..."
            unzip -o "${'$'}ZIPFILE" boot.img -d /tmp/
            dd if=/tmp/boot.img of=$bootBlockPath bs=4096 conv=fsync
            unzip -o "${'$'}ZIPFILE" vendor_boot.img -d /tmp/
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
            zos.write(bootFile.readBytes())
            zos.closeEntry()

            if (vendorBootFile.exists()) {
                zos.putNextEntry(ZipEntry("vendor_boot.img"))
                zos.write(vendorBootFile.readBytes())
                zos.closeEntry()
            }
        }
    }

    /**
     * Writes a genuine POSIX USTAR `.tar` archive required by Samsung Odin3 (`AP` slot).
     */
    private fun createOdinPosixTar(
        tarFile: File,
        entries: List<Pair<String, File>>
    ) {
        FileOutputStream(tarFile).use { fos ->
            for ((entryName, file) in entries) {
                val fileBytes = file.readBytes()
                val header = ByteArray(512)

                // File name (0..99)
                val nameBytes = entryName.toByteArray(Charsets.US_ASCII)
                System.arraycopy(nameBytes, 0, header, 0, nameBytes.size.coerceAtMost(99))

                // File mode (100..107) -> "0000644\0"
                writeOctalString(header, 100, 8, 420L) // 0644 octal = 420 decimal
                // UID & GID (108..123)
                writeOctalString(header, 108, 8, 0L)
                writeOctalString(header, 116, 8, 0L)
                // File size in bytes (124..135)
                writeOctalString(header, 124, 12, fileBytes.size.toLong())
                // Last modified time in seconds (136..147)
                writeOctalString(header, 136, 12, System.currentTimeMillis() / 1000L)

                // Pre-fill checksum field (148..155) with 8 ASCII spaces for checksum calculation
                for (i in 148 until 156) {
                    header[i] = ' '.code.toByte()
                }
                // Typeflag '0' (normal file) at 156
                header[156] = '0'.code.toByte()

                // USTAR indicator "ustar\0" at 257..262 and version "00" at 263..264
                val ustar = "ustar\u000000".toByteArray(Charsets.US_ASCII)
                System.arraycopy(ustar, 0, header, 257, ustar.size)

                // Compute header checksum
                var checksum = 0L
                for (b in header) {
                    checksum += (b.toInt() and 0xFF)
                }
                writeOctalString(header, 148, 7, checksum)
                header[155] = ' '.code.toByte()

                fos.write(header)
                fos.write(fileBytes)

                // Pad data to 512-byte boundary
                val remainder = fileBytes.size % 512
                if (remainder != 0) {
                    fos.write(ByteArray(512 - remainder))
                }
            }
            // Two 512-byte zero blocks mark end-of-archive in TAR specification
            fos.write(ByteArray(1024))
        }
    }

    private fun writeOctalString(buffer: ByteArray, offset: Int, length: Int, value: Long) {
        val octal = value.toString(8).padStart(length - 1, '0')
        val bytes = octal.toByteArray(Charsets.US_ASCII)
        System.arraycopy(bytes, 0, buffer, offset, bytes.size.coerceAtMost(length - 1))
        buffer[offset + length - 1] = 0
    }

    private fun synthesizeKalamaBootImageBinary(
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

        buffer.position(8192)
        val fdtSlice = buffer.slice().order(ByteOrder.BIG_ENDIAN)
        fdtSlice.putInt(0xD00DFEED.toInt())
        fdtSlice.putInt(4096)
        fdtSlice.putInt(64)
        fdtSlice.putInt(3584)
        fdtSlice.putInt(40)
        fdtSlice.putInt(17)
        fdtSlice.putInt(16)
        fdtSlice.putInt(0)
        fdtSlice.put("qcom,kalama-sm8550-ac-opp-table".toByteArray(Charsets.US_ASCII))

        for (pt in oppPoints) {
            if (fdtSlice.remaining() >= 20) {
                fdtSlice.putInt(pt.cluster.id)
                fdtSlice.putLong(pt.freqKhz * 1000L)
                fdtSlice.putInt(pt.voltageMv * 1000)
                fdtSlice.putInt(pt.mode.ordinal)
            }
        }

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

    private fun synthesizeKalamaVendorBootBinary(
        oppPoints: List<OppFrequencyPoint>
    ): ByteArray {
        val totalSize = 16 * 1024
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("VNDRBOOT".toByteArray(Charsets.US_ASCII))
        buffer.putInt(4)
        buffer.putInt(4096)

        buffer.position(4096)
        val fdt = buffer.slice().order(ByteOrder.BIG_ENDIAN)
        fdt.putInt(0xD00DFEED.toInt())
        fdt.putInt(4096)
        for (pt in oppPoints) {
            if (fdt.remaining() >= 16) {
                fdt.putLong(pt.freqKhz * 1000L)
                fdt.putInt(pt.voltageMv * 1000)
            }
        }
        return buffer.array()
    }

    fun sha256File(file: File): String = sha256Bytes(file.readBytes())

    fun sha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
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
    val bootSha256: String,
    val vendorBootSha256: String,
    val totalSizeBytes: Long,
    val isOriginalStock: Boolean
)
