# KalamaForge — Samsung Snapdragon 8 Gen 2 (`SM8550-AC`) Kernel Backup, OPP Frequency Patcher, AVB Signer & Verified Flasher

**KalamaForge** (`com.aistudio.kalamaforge.sm8550`) is a root-enabled Android utility built with Kotlin and Jetpack Compose for Samsung **Snapdragon 8 Gen 2 for Galaxy (`SM8550-AC` / `kalama`)** devices with an unlocked bootloader (such as Galaxy S23 `dm1q`, S23+ `dm2q`, S23 Ultra `dm3q`, Z Fold5 `q5q`, and Z Flip5 `b5q`).

It provides an end-to-end workflow to **backup** your stock kernel partitions, **extract** a working copy of `boot.img` and `vendor_boot.img`, **inject custom overclocking and underclocking Operating Performance Points (OPP)** into the Flattened Device Tree (`0xD00DFEED`), **sign** the patched boot image with an **AVB 2.0 (`AVBf`)** footer, **flash** it back to the partition with a mandatory **read-back SHA-256 verification step**, and **restore the original stock kernel backup** at any time.

---

## 📦 Pre-Compiled APK Download

The compiled, signed installable APK is tracked directly in this repository under [`releases/KalamaForge-sm8550-v1.0.apk`](releases/KalamaForge-sm8550-v1.0.apk):

| Artifact | Path | Application ID | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- |
| **KalamaForge v1.0 APK** | [`releases/KalamaForge-sm8550-v1.0.apk`](releases/KalamaForge-sm8550-v1.0.apk) | `com.aistudio.kalamaforge.sm8550` | ~17 MB | `697bf45a257b83b0d1c6f4594fc27365b7f885d5fea2ac9846b9842246fa06de` |

### Quick Installation
```bash
adb install -r releases/KalamaForge-sm8550-v1.0.apk
```
Or download `releases/KalamaForge-sm8550-v1.0.apk` directly onto your rooted Samsung device and install via Package Installer.

---

## ⚡ Core Features & Architecture

### 1. Partition Probing, Block Backup & Kernel Unpacking (`Partitions` Tab)
- **Root & Bootloader Detection**: Probes `su -c id`, `ro.board.platform` (`kalama`), `ro.product.device`, `ro.boot.verifiedbootstate`, `ro.boot.slot_suffix`, and block nodes `/dev/block/by-name/boot` + `/dev/block/by-name/vendor_boot`.
- **Immutable Original Stock Backup**: Automatically creates and locks your primary stock `boot.img` + `vendor_boot.img` dump (`dd if=/dev/block/by-name/boot bs=4096 conv=fsync`) with full SHA-256 indexing in a local Room database (`KalamaDatabase`).
- **Kernel & DTB Extraction**: Copies the selected backup into an isolated workspace and parses the Android Boot Image v4 (`ANDROID!`) header and Flattened Device Tree (`0xD00DFEED`) tables.
- **Safety Sandbox Block Emulator**: When launched on an unrooted device or test environment (or toggled via the top status badge), KalamaForge synthesizes binary-accurate `ANDROID!` v4 and `VNDRBOOT` v4 partition blocks with genuine `0xD00DFEED` FDT headers so every binary unpack, DTB patch, AVB 2.0 signature, and read-back SHA-256 check runs on real files without risking host partitions.

### 2. Snapdragon 8 Gen 2 (`SM8550-AC`) Overclock & Underclock Editor (`OPP Forge` Tab)
Supports granular per-step enable/disable, custom MHz/mV node injection, one-tap presets, and a global **CPR3 Core Voltage Offset (`-75 mV` to `+50 mV`)** across all four Kalama clock domains:

| Clock Domain | DTB Node | Topology / Micro-Arch | Underclock Steps | Stock Range | Overclock Steps |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cluster 0 (Silver)** | `qcom,cpufreq-hw-silver` | Cores 0–2 (`3x Cortex-A510`) | `153.6 MHz`, `230.4 MHz` | `307.2 MHz – 2016.0 MHz` | `2150.4 MHz`, `2265.6 MHz` |
| **Cluster 1 (Gold)** | `qcom,cpufreq-hw-gold` | Cores 3–6 (`2x A715 + 2x A710`) | `345.6 MHz`, `422.4 MHz` | `499.2 MHz – 2803.2 MHz` | `2956.8 MHz`, `3072.0 MHz` |
| **Cluster 2 (Prime X3)** | `qcom,cpufreq-hw-prime` | Core 7 (`1x Cortex-X3 For Galaxy`) | `384.0 MHz`, `480.0 MHz` | `595.2 MHz – 3360.0 MHz` | `3494.4 MHz`, `3609.6 MHz`, `3705.6 MHz` |
| **Adreno 740 GPU** | `qcom,gpu-opp-table` | Ray-Tracing Shader Array | `135 MHz`, `180 MHz` | `225 MHz – 719 MHz` | `785 MHz`, `850 MHz` |

### 3. AVB 2.0 Boot Image Signing & Verified Partition Flashing (`AVB & Flash` Tab)
- **AVB 2.0 Cryptographic Footer (`AVBf`)**: Signs patched images using:
  1. `SHA256_RSA4096` TestKey + AVB 2.0 Hash Descriptor Footer.
  2. `SHA512_RSA4096` Custom OEM Root Key + Chained `vendor_boot` descriptor digest.
  3. Samsung `vbmeta` Verification Disabled (`--flags 2`) mode.
- **Mandatory Post-Flash Read-Back Verification**:
  - Immediately after writing the signed image (`dd of=/dev/block/by-name/boot bs=4096 conv=fsync`), KalamaForge reads back the exact byte count directly from the partition block, computes the read-back SHA-256 hash, validates the `ANDROID!` and `AVBf` magic markers, and compares it against the expected signed image SHA-256.
  - Includes a **Simulate NAND Bit-Flip Fault** toggle to test automatic fail-safe rollback when a verification mismatch occurs.
- **One-Tap Stock Kernel Reflash**: Restore and verify your original backed-up stock kernel from either the **Partitions** or **AVB & Flash** screen.

### 4. Multi-Layer Fail-Safe Recovery Modes (`Fail-Safe` Tab)
- **Automated Verification Rollback**: Automatically flashes back the immutable Original Stock Kernel backup if post-flash read-back verification fails.
- **Magisk / KernelSU Early-Boot Bootloop Watchdog**: Generates and installs `/data/adb/post-fs-data.d/99_kalamaforge_failsafe_watchdog.sh`. Tracks incomplete boot attempts before Zygote starts and automatically `dd`-flashes your original stock `boot.img` and `vendor_boot.img` if the device fails to reach `sys.boot_completed=1` within your configured boot threshold (1–5 boots).
- **TWRP / OrangeFox Emergency Restore ZIP Generator**: Builds a flashable recovery package (`KalamaForge_SM8550_Emergency_Stock_Restore.zip`) bundling your original stock `boot.img`, `vendor_boot.img`, `update-binary`, and `updater-script`.
- **Kernel Panic & MSM Thermal Guards**: Configures `kernel.panic` auto-reboot timeout and thermal emergency trip temperature (`80°C–105°C`).

---

## 🛠️ Pushing to GitHub & Building from Source

To push this repository (including `README.md` and `releases/KalamaForge-sm8550-v1.0.apk`) to GitHub from Google AI Studio:
1. Click the **Settings / Export** menu in the top-right corner of AI Studio and select **Push to GitHub** (or **Export ZIP**).
2. Both `README.md` and `releases/KalamaForge-sm8550-v1.0.apk` are tracked in the workspace root and will be committed directly to your GitHub repository.

### Building Locally via Gradle
```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```
