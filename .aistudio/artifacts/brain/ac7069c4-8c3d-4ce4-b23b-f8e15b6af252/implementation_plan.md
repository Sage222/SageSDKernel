# KalamaForge — Real `magiskboot` Unpack/Repack, FDT (`0xD00DFEED`) `opp-hz` & `opp-microvolt` Binary Patcher, and `vbmeta` Fix Plan

This plan replaces placeholder trailer writes and removes the misleading `Live sysfs` button, implementing a real **`magiskboot` unpack -> FDT (`0xD00DFEED`) binary `opp-hz` / `opp-microvolt` property patch -> `magiskboot` repack** pipeline alongside automatic **`vbmeta.img` verification-disable (`flags = 0x03`)** so your Samsung Snapdragon 8 Gen 2 (`SM8550`) boots cleanly with your exact voltage and frequency modifications.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Choices from Clarification:**
> - **Confirmed Decision 1 — On-Device `magiskboot` Unpack/Repack + Real FDT Binary Patcher**:
>   - Locate the on-device `magiskboot` binary (`/data/adb/magisk/magiskboot`, `/data/adb/ksu/bin/magiskboot`, or `/data/adb/ap/bin/magiskboot`).
>   - Run `magiskboot unpack -h` on the backed-up `vendor_boot.img` and `boot.img` to cleanly decompress the LZ4/GZIP `kernel` and `dtb` payloads.
>   - Parse the uncompressed Flattened Device Tree (`0xD00DFEED`) structure (`FDT_BEGIN_NODE`, `FDT_PROP`, string table offsets for `opp-hz` and `opp-microvolt`), patching the real 64-bit big-endian Hz and 32-bit big-endian microvolt (`uV`) values in the DTB and kernel OPP tables, then run `magiskboot repack` to produce a valid, bootable Samsung image.
> - **Confirmed Decision 2 — Remove Misleading `Live sysfs` Button**:
>   - Remove the `Apply Live via Root (sysfs)` button completely so the UI only performs genuine kernel/DTB image patching, exporting (`TWRP .zip`, `Odin .tar`, raw `.img`), and verified partition flashing.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. Backs up your real `/dev/block/by-name/boot`, `/dev/block/by-name/vendor_boot`, and `/dev/block/by-name/vbmeta` partitions.
  2. Uses `magiskboot unpack` to extract and decompress the real `dtb` and `kernel` payloads from your Samsung `vendor_boot.img` and `boot.img`.
  3. Parses the real Flattened Device Tree (`0xD00DFEED`) string and struct tables to read and patch `opp-hz` (64-bit Hz) and `opp-microvolt` (32-bit `uV` — target, min, and max triplets) for each frequency step, and injects newly added underclock/overclock OPP nodes.
  4. Repacks the modified `boot.img` and `vendor_boot.img` using `magiskboot repack`, patches `vbmeta.img` (`offset 123 = 0x03` to disable AVB verification), and either exports them for **TWRP (`.zip`)** / **Odin (`.tar`)** / **Raw `.img`** or flashes them directly with SHA-256 read-back verification.
- **Target Audience / Persona**: Rooted Samsung Snapdragon 8 Gen 2 (`SM8550`) users with Magisk, KernelSU, or APatch installed (`magiskboot` available in `/data/adb/`).
- **Key Value**: Performs genuine decompression, FDT `opp-hz`/`opp-microvolt` binary patching, and `magiskboot repack` + `vbmeta` patching so changes actually take effect in the kernel without triggering `CURRENT BINARY: Custom (0xF82)`.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Tab 1 — `1. Frequencies & Voltages`**:
     - Select **Prime X3**, **Gold Cores**, **Silver Cores**, or **Adreno 740 GPU**.
     - View the **Stock Frequencies** and **Stock Reference (`MHz @ mV`)** for every step alongside your active range.
     - Adjust **Voltage (`-25 mV`, `-5 mV`, `+5 mV`, `+25 mV`, or `-10 mV All` / `+10 mV All`)** independently of frequency.
     - Adjust **Frequency (`-50 MHz`, `-15 MHz`, `+15 MHz`, `+50 MHz`)** independently of voltage, or add new **Underclock** and **Overclock** steps.
  2. **Tab 2 — `2. Backup, Export & Flash`**:
     - **1. Modified Kernel (`magiskboot` Repacked + `vbmeta` Patched)**:
       - **Flash Modified Kernel Directly & Verify** (unpacks with `magiskboot`, patches FDT `opp-hz` & `opp-microvolt`, repacks `boot.img` + `vendor_boot.img`, flashes patched `vbmeta.img` + `boot.img` + `vendor_boot.img`, and verifies read-back SHA-256).
       - **Export for TWRP (`.zip`)** & **Export for Odin (`.tar`)** (bundles the `magiskboot`-repacked `boot.img`, `vendor_boot.img`, and verification-disabled `vbmeta.img`).
       - **Extract Modified Kernel Backup (`boot.img` & `vendor_boot.img`)**.
     - **2. Original Stock Kernel Backup**:
       - **Reflash Original Stock Kernel & Verify** (restores unmodified stock `boot.img`, `vendor_boot.img`, and `vbmeta.img`).
       - **Extract Original Kernel Backup (`boot.img`)**, **Stock TWRP (`.zip`)**, or **Stock Odin (`.tar`)**.
- **Visual Identity & Theme**:
  - Clean, high-contrast dark theme (`ObsidianBg #070B14`, `ObsidianSurface #0E1525`, `KalamaCyan #00E5FF`, `KalamaEmerald #00E676`, `KalamaAmber #FFB300`) with `Space Grotesk` + `JetBrains Mono` typography and zero clutter.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Real `magiskboot` Unpack & Repack Pipeline**
  - *Chosen Approach*: Discover `magiskboot` in `/data/adb/magisk/magiskboot`, `/data/adb/ksu/bin/magiskboot`, or `/data/adb/ap/bin/magiskboot`. Copy the backed-up `vendor_boot.img` and `boot.img` into an isolated working directory, run `magiskboot unpack -h`, binary-patch all `0xD00DFEED` FDT blobs inside the unpacked `dtb` (and `kernel_dtb` if present), and run `magiskboot repack` to generate a properly formatted, compressed, header-valid Samsung `boot.img` and `vendor_boot.img`.
  - *Why*: On Samsung SM8550 GKI 2.0, `boot.img` and `vendor_boot.img` payloads are LZ4-compressed. Only unpacking and decompressing via `magiskboot` exposes the raw `0xD00DFEED` Flattened Device Tree where `opp-hz` and `opp-microvolt` live, and `magiskboot repack` reconstructs the exact Samsung `ANDROID!` / `VNDRBOOT` v4 headers and ramdisk offsets.
- **Decision 2: True FDT (`0xD00DFEED`) Struct & String Table Parser (`FdtBinaryPatcher`)**
  - *Chosen Approach*: Walk the concatenated FDT blobs (`0xD00DFEED` magic), parse `off_dt_struct` and `off_dt_strings`, locate `FDT_PROP` (`0x00000003`) entries whose property name in `dt_strings` is `"opp-hz"` or `"opp-microvolt"` (as well as `"qcom,freq-tbl"` / voltage LUT arrays), and patch the 64-bit big-endian Hz and 32-bit big-endian microvolt (`uV`, including `[target_uV, min_uV, max_uV]` triplets) directly in the FDT struct before `magiskboot repack`.
  - *Why*: Ensures `-20 mV` undervolts and frequency edits modify the actual device tree properties read by the kernel drivers.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         MainActivity (2 Simple Tabs)                      │
│  ┌──────────────────────────────────┐  ┌────────────────────────────────┐ │
│  │   Tab 1: SimpleFrequenciesScreen │  │ Tab 2: SimpleBackupExportFlash │ │
│  │  • Cluster Selector (Prime/Gold/ │  │  • Flash Modified (magiskboot  │ │
│  │    Silver/Adreno 740)            │  │    repacked + vbmeta 0x03)     │ │
│  │  • Stock vs Active Range Banner  │  │  • Export TWRP (.zip)          │ │
│  │  • Per-Step Independent Voltage  │  │  • Export Odin AP (.tar)       │ │
│  │    (-25/-5/+5/+25 mV & All ±10mV)│  │  • Extract Modified boot.img   │ │
│  │  • Per-Step Independent Freq     │  │  • Extract Original boot.img   │ │
│  │  • + Underclock / + Overclock    │  │  • Reflash Original Stock      │ │
│  └─────────────────┬────────────────┘  └───────────────┬────────────────┘ │
└────────────────────┼───────────────────────────────────┼──────────────────┘
                     ▼                                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                           KalamaForgeViewModel                            │
│  • Loads real hardware frequencies & stock reference voltages             │
│  • Manages independent per-step MHz & mV edits                            │
│  • Coordinates magiskboot unpack -> FDT patch -> repack -> flash/export   │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    RootKernelEngine & FdtBinaryPatcher                    │
│  1. Dumps /dev/block/by-name/{boot,vendor_boot,vbmeta} via su dd          │
│  2. Runs `magiskboot unpack -h` on vendor_boot.img and boot.img           │
│  3. FdtBinaryPatcher parses 0xD00DFEED FDT struct & string tables:        │
│     • Matches `opp-hz` (uint64 BE Hz) & `opp-microvolt` (uint32 BE uV)    │
│     • Updates microvolt target/min/max & frequency Hz values in-place     │
│  4. Runs `magiskboot repack` to rebuild valid boot.img & vendor_boot.img  │
│  5. Patches vbmeta.img (offset 123 = 0x03) to disable AVB hash lockouts   │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `FdtBinaryPatcher`: Pure Kotlin binary parser for standard Device Tree Blob (`0xD00DFEED`) files (supports multi-DTB concatenated `dtb` files extracted by `magiskboot`).
  - `ModifiedKernelBundle`: Holds the `magiskboot`-repacked `boot.img`, `vendor_boot.img`, and patched `vbmeta.img` (`flags = 0x03`), along with the count of FDT `opp-hz` / `opp-microvolt` properties patched.
- **Interactive Component & State Mapping**:
  - **Voltage & Frequency Step Controls**: Adjust `voltageMv` (`uV = voltageMv * 1000`) and `freqMhz` (`Hz = freqMhz * 1_000_000L`) independently per step.
  - **Flash / Export Actions**: Execute `magiskboot unpack`, run `FdtBinaryPatcher.patchConcatenatedDtbs(...)` on the extracted `dtb` / `kernel_dtb`, run `magiskboot repack`, patch `vbmeta.img` (`offset 123 = 0x03`), and either write to `/sdcard/Download/KalamaForge/` (`.zip`, `.tar`, `.img`) or `dd`-flash to `/dev/block/by-name/{vbmeta,boot,vendor_boot}` with SHA-256 read-back verification.
