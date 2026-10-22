package com.example.domain

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class FdtPatchResult(
    val dtbBlobsFound: Int,
    val oppHzPatchedCount: Int,
    val oppMicrovoltPatchedCount: Int
)

/**
 * Specification-compliant Flattened Device Tree (`0xD00DFEED`) Binary Parser & In-Place Property Patcher.
 *
 * Parses concatenated or standalone Device Tree Blobs (`dtb` / `kernel_dtb` unpacked by `magiskboot`),
 * walks the `dt_struct` token stream (`FDT_BEGIN_NODE`, `FDT_PROP`, `FDT_END_NODE`), resolves property
 * names via `off_dt_strings`, and patches `opp-hz` (64-bit big-endian Hz) and `opp-microvolt`
 * (32-bit big-endian uV target/min/max) in-place.
 */
object FdtBinaryPatcher {

    private const val FDT_MAGIC = 0xD00DFEED.toInt()
    private const val FDT_BEGIN_NODE = 0x00000001
    private const val FDT_END_NODE = 0x00000002
    private const val FDT_PROP = 0x00000003
    private const val FDT_NOP = 0x00000004
    private const val FDT_END = 0x00000009

    /**
     * Scans a file (such as `dtb` unpacked by `magiskboot unpack` or an image containing one or more
     * `0xD00DFEED` FDT blobs) and patches all matching `opp-hz` and `opp-microvolt` properties in-place.
     */
    fun patchMultiDtbFile(
        dtbFile: File,
        activePoints: List<OppFrequencyPoint>
    ): FdtPatchResult {
        if (!dtbFile.exists() || dtbFile.length() < 64L) {
            return FdtPatchResult(0, 0, 0)
        }
        val bytes = dtbFile.readBytes()
        val result = patchDtbByteArrayInPlace(bytes, activePoints)
        if (result.dtbBlobsFound > 0) {
            dtbFile.writeBytes(bytes)
        }
        return result
    }

    fun patchDtbByteArrayInPlace(
        bytes: ByteArray,
        activePoints: List<OppFrequencyPoint>
    ): FdtPatchResult {
        val enabledPoints = activePoints.filter { it.enabled }
        var blobsFound = 0
        var hzPatched = 0
        var uvPatched = 0

        var offset = 0
        while (offset <= bytes.size - 40) {
            if (isFdtMagicAt(bytes, offset)) {
                val hdr = ByteBuffer.wrap(bytes, offset, 40).order(ByteOrder.BIG_ENDIAN)
                val magic = hdr.int
                val totalSize = hdr.int
                val offDtStruct = hdr.int
                val offDtStrings = hdr.int

                if (magic == FDT_MAGIC &&
                    totalSize in 64..(bytes.size - offset) &&
                    offDtStruct in 40 until totalSize &&
                    offDtStrings in 40 until totalSize
                ) {
                    blobsFound++
                    val (patchedHzInBlob, patchedUvInBlob) = patchSingleFdtBlob(
                        bytes = bytes,
                        blobBaseOffset = offset,
                        totalSize = totalSize,
                        offDtStruct = offDtStruct,
                        offDtStrings = offDtStrings,
                        enabledPoints = enabledPoints
                    )
                    hzPatched += patchedHzInBlob
                    uvPatched += patchedUvInBlob
                    offset += totalSize.coerceAtLeast(4)
                    continue
                }
            }
            offset += 4
        }

        return FdtPatchResult(
            dtbBlobsFound = blobsFound,
            oppHzPatchedCount = hzPatched,
            oppMicrovoltPatchedCount = uvPatched
        )
    }

    private fun patchSingleFdtBlob(
        bytes: ByteArray,
        blobBaseOffset: Int,
        totalSize: Int,
        offDtStruct: Int,
        offDtStrings: Int,
        enabledPoints: List<OppFrequencyPoint>
    ): Pair<Int, Int> {
        var hzPatched = 0
        var uvPatched = 0

        val structStart = blobBaseOffset + offDtStruct
        val structEnd = (blobBaseOffset + offDtStrings).coerceAtMost(blobBaseOffset + totalSize)
        val stringsStart = blobBaseOffset + offDtStrings
        val blobEnd = blobBaseOffset + totalSize

        var cursor = structStart
        var currentNodeName = ""
        var currentNodeOppHzOffset = -1
        var currentNodeOppHzValue = -1L
        var currentNodeMicrovoltOffset = -1
        var currentNodeMicrovoltLen = 0

        fun flushAndPatchCurrentNode() {
            if (currentNodeOppHzOffset < 0 && currentNodeMicrovoltOffset < 0) return

            // Match by existing stock frequency (in Hz -> MHz) or node name "opp-<hz>"
            val nodeStockMhz = when {
                currentNodeOppHzValue > 0L -> (currentNodeOppHzValue / 1_000_000L).toInt()
                currentNodeName.startsWith("opp-") -> {
                    currentNodeName.removePrefix("opp-")
                        .takeWhile { it.isDigit() }
                        .toLongOrNull()
                        ?.let { (it / 1_000_000L).toInt() } ?: -1
                }
                else -> -1
            }

            if (nodeStockMhz > 0) {
                val matchedPoint = enabledPoints.minByOrNull { pt ->
                    val refMhz = pt.stockFreqMhz ?: pt.freqMhz
                    abs(refMhz - nodeStockMhz)
                }?.takeIf { pt ->
                    val refMhz = pt.stockFreqMhz ?: pt.freqMhz
                    abs(refMhz - nodeStockMhz) <= 25
                }

                if (matchedPoint != null) {
                    if (currentNodeOppHzOffset >= 0 && currentNodeOppHzOffset + 8 <= blobEnd) {
                        val targetHz = matchedPoint.freqKhz * 1000L
                        ByteBuffer.wrap(bytes, currentNodeOppHzOffset, 8)
                            .order(ByteOrder.BIG_ENDIAN)
                            .putLong(targetHz)
                        hzPatched++
                    }
                    if (currentNodeMicrovoltOffset >= 0 &&
                        currentNodeMicrovoltLen >= 4 &&
                        currentNodeMicrovoltOffset + currentNodeMicrovoltLen <= blobEnd
                    ) {
                        val targetUv = matchedPoint.voltageMv * 1000
                        val uvBuf = ByteBuffer.wrap(
                            bytes,
                            currentNodeMicrovoltOffset,
                            currentNodeMicrovoltLen
                        ).order(ByteOrder.BIG_ENDIAN)
                        // opp-microvolt is either <target_uV> (4 bytes) or <target_uV min_uV max_uV> (12 bytes)
                        val cells = (currentNodeMicrovoltLen / 4).coerceAtMost(3)
                        for (c in 0 until cells) {
                            uvBuf.putInt(targetUv)
                        }
                        uvPatched++
                    }
                }
            }

            currentNodeOppHzOffset = -1
            currentNodeOppHzValue = -1L
            currentNodeMicrovoltOffset = -1
            currentNodeMicrovoltLen = 0
        }

        while (cursor + 4 <= structEnd) {
            val token = ByteBuffer.wrap(bytes, cursor, 4).order(ByteOrder.BIG_ENDIAN).int
            cursor += 4

            when (token) {
                FDT_BEGIN_NODE -> {
                    flushAndPatchCurrentNode()
                    val nameStart = cursor
                    while (cursor < structEnd && bytes[cursor] != 0.toByte()) {
                        cursor++
                    }
                    currentNodeName = String(bytes, nameStart, cursor - nameStart, Charsets.US_ASCII)
                    cursor++ // skip null terminator
                    cursor = align4(cursor)
                }

                FDT_END_NODE -> {
                    flushAndPatchCurrentNode()
                    currentNodeName = ""
                }

                FDT_PROP -> {
                    if (cursor + 8 > structEnd) break
                    val propHdr = ByteBuffer.wrap(bytes, cursor, 8).order(ByteOrder.BIG_ENDIAN)
                    val propLen = propHdr.int
                    val nameOff = propHdr.int
                    cursor += 8

                    if (propLen < 0 || cursor + propLen > structEnd) break
                    val propName = readNullTerminatedString(bytes, stringsStart + nameOff, blobEnd)

                    if (propName == "opp-hz" && propLen >= 8) {
                        currentNodeOppHzOffset = cursor
                        currentNodeOppHzValue = ByteBuffer.wrap(bytes, cursor, 8)
                            .order(ByteOrder.BIG_ENDIAN)
                            .long
                    } else if (propName.startsWith("opp-microvolt") && propLen >= 4) {
                        currentNodeMicrovoltOffset = cursor
                        currentNodeMicrovoltLen = propLen
                    }

                    cursor += propLen
                    cursor = align4(cursor)
                }

                FDT_NOP -> {
                    // Continue
                }

                FDT_END -> {
                    flushAndPatchCurrentNode()
                    break
                }

                else -> {
                    // Unknown token; stop parsing this struct safely
                    break
                }
            }
        }

        return Pair(hzPatched, uvPatched)
    }

    /**
     * Synthesizes a valid specification-compliant `0xD00DFEED` Flattened Device Tree (FDT) blob
     * containing real `opp-table` child nodes (`opp-<hz>`) with `opp-hz` (uint64 BE) and
     * `opp-microvolt` (uint32 BE triplet `<target_uV min_uV max_uV>`) so that both `magiskboot`
     * and `FdtBinaryPatcher` operate on real FDT structures.
     */
    fun buildValidKalamaFdtBlob(points: List<OppFrequencyPoint>): ByteArray {
        val stringsBytes = "opp-hz\u0000opp-microvolt\u0000".toByteArray(Charsets.US_ASCII)
        val offNameOppHz = 0
        val offNameOppMicrovolt = 7

        val structBuf = ByteBuffer.allocate(6144).order(ByteOrder.BIG_ENDIAN)

        // Root node: FDT_BEGIN_NODE ""
        structBuf.putInt(FDT_BEGIN_NODE)
        structBuf.putInt(0) // empty root name + 4-byte padding

        // opp-table container node
        structBuf.putInt(FDT_BEGIN_NODE)
        putAlignedNullString(structBuf, "kalama-opp-table")

        for (pt in points) {
            if (structBuf.remaining() < 96) break
            val hz = pt.freqKhz * 1000L
            val uv = pt.voltageMv * 1000

            structBuf.putInt(FDT_BEGIN_NODE)
            putAlignedNullString(structBuf, "opp-$hz")

            // Property 1: opp-hz (8 bytes uint64 BE)
            structBuf.putInt(FDT_PROP)
            structBuf.putInt(8)
            structBuf.putInt(offNameOppHz)
            structBuf.putLong(hz)

            // Property 2: opp-microvolt (12 bytes: <target min max> uint32 BE)
            structBuf.putInt(FDT_PROP)
            structBuf.putInt(12)
            structBuf.putInt(offNameOppMicrovolt)
            structBuf.putInt(uv)
            structBuf.putInt(uv)
            structBuf.putInt(uv)

            structBuf.putInt(FDT_END_NODE)
        }

        // Close opp-table and root node
        structBuf.putInt(FDT_END_NODE)
        structBuf.putInt(FDT_END_NODE)
        structBuf.putInt(FDT_END)

        val sizeDtStruct = structBuf.position()
        val offMemRsvmap = 40
        val offDtStruct = 56 // after 16-byte empty mem_rsvmap
        val offDtStrings = offDtStruct + sizeDtStruct
        val totalSize = align4(offDtStrings + stringsBytes.size)

        val out = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        out.putInt(FDT_MAGIC)            // 0: magic 0xD00DFEED
        out.putInt(totalSize)            // 4: totalsize
        out.putInt(offDtStruct)          // 8: off_dt_struct
        out.putInt(offDtStrings)         // 12: off_dt_strings
        out.putInt(offMemRsvmap)         // 16: off_mem_rsvmap
        out.putInt(17)                   // 20: version
        out.putInt(16)                   // 24: last_comp_version
        out.putInt(0)                    // 28: boot_cpuid_phys
        out.putInt(stringsBytes.size)    // 32: size_dt_strings
        out.putInt(sizeDtStruct)         // 36: size_dt_struct

        // 40..55: 16-byte zero terminator for mem_rsvmap
        out.position(offDtStruct)
        out.put(structBuf.array(), 0, sizeDtStruct)

        out.position(offDtStrings)
        out.put(stringsBytes)

        return out.array()
    }

    private fun putAlignedNullString(buf: ByteBuffer, str: String) {
        val raw = str.toByteArray(Charsets.US_ASCII)
        buf.put(raw)
        buf.put(0.toByte())
        while (buf.position() % 4 != 0) {
            buf.put(0.toByte())
        }
    }

    private fun isFdtMagicAt(bytes: ByteArray, offset: Int): Boolean {
        return bytes[offset] == 0xD0.toByte() &&
            bytes[offset + 1] == 0x0D.toByte() &&
            bytes[offset + 2] == 0xFE.toByte() &&
            bytes[offset + 3] == 0xED.toByte()
    }

    private fun align4(pos: Int): Int = (pos + 3) and -4

    private fun readNullTerminatedString(bytes: ByteArray, start: Int, maxEnd: Int): String {
        if (start < 0 || start >= maxEnd) return ""
        var end = start
        while (end < maxEnd && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, start, end - start, Charsets.US_ASCII)
    }
}
