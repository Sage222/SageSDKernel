# KalamaForge — Simple Snapdragon 8 Gen 2 (`SM8550`) Frequency/Voltage Editor & Kernel Flasher

**KalamaForge** (`com.aistudio.kalamaforge.sm8550`) is a clean, easy-to-use Android app for rooted Samsung **Snapdragon 8 Gen 2 for Galaxy (`SM8550-AC`)** devices with an unlocked bootloader.

---

## 📦 Pre-Compiled APK

The compiled APK is tracked directly in this repository under [`releases/KalamaForge-sm8550-v1.0.apk`](releases/KalamaForge-sm8550-v1.0.apk):

| File | Application ID | SHA-256 Checksum |
| :--- | :--- | :--- |
| [`releases/KalamaForge-sm8550-v1.0.apk`](releases/KalamaForge-sm8550-v1.0.apk) | `com.aistudio.kalamaforge.sm8550` | `d542a129c9aa6e4d61ffe00ff5466e2ac44efcbdd0303c49e2b4f23314fe9f26` |

---

## 🔧 How Kernel & DTB Patching Works (`magiskboot` + `FdtBinaryPatcher`)

1. **On-Device `magiskboot` Unpack & Repack:**
   - Automatically detects `magiskboot` (`/data/adb/magisk/magiskboot`, `/data/adb/ksu/bin/magiskboot`, or `/data/adb/ap/bin/magiskboot`).
   - Runs `magiskboot unpack -h` on your backed-up **`vendor_boot.img`** and **`boot.img`** to cleanly decompress the LZ4/GZIP `dtb` and `kernel` payloads.
2. **Real FDT (`0xD00DFEED`) Binary `opp-hz` & `opp-microvolt` Patching (`FdtBinaryPatcher`):**
   - Walks the uncompressed Flattened Device Tree (`0xD00DFEED`) `dt_struct` token stream (`FDT_BEGIN_NODE`, `FDT_PROP`) and `dt_strings` table.
   - Patches `opp-hz` (`uint64` big-endian Hz) and `opp-microvolt` (`uint32` big-endian `uV` `<target min max>`) directly inside the device tree blobs before running `magiskboot repack` and padding to 100% stock partition byte length.
3. **Automatic `vbmeta.img` Verification-Disable Patch (`flags = 0x03`):**
   - Backs up `/dev/block/by-name/vbmeta` and patches byte offset `123` (`0x03`: `DISABLE_VERITY | DISABLE_VERIFICATION`) when flashing or exporting **Odin `.tar`** and **TWRP `.zip`** packages so Samsung's bootloader (`RP SWREV B5 / K6 S5`) boots cleanly without `CURRENT BINARY: Custom (0xF82)`.

---

## ✨ What It Does (2 Simple Tabs)

### Tab 1: `1. Frequencies & Voltages`
- **Clearly See Stock Frequencies & Voltages:**
  - Automatically reads available hardware frequencies from `sysfs` on startup across **Prime X3** (`595 – 3360 MHz`), **Gold Cores** (`499 – 2803 MHz`), **Silver Cores** (`307 – 2016 MHz`), and **Adreno 740 GPU** (`225 – 719 MHz`).
  - Every stock step displays its **Stock Reference** (e.g., `Stock Reference: 3360 MHz @ 1065 mV`) alongside your active settings.
- **Adjust Voltages & Frequencies Independently:**
  - Every frequency step has independent **Voltage buttons** (`-25 mV`, `-5 mV`, `+5 mV`, `+25 mV`) so you can undervolt or overvolt any step without changing its frequency.
  - Every step also has independent **Frequency buttons** (`-50 MHz`, `-15 MHz`, `+15 MHz`, `+50 MHz`) so you can shift frequencies without changing voltage.
  - Includes one-tap **`-10 mV All`** / **`+10 mV All`** cluster voltage buttons and a **`Reset Stock`** button.
- **Easily Add Overclock & Underclock Steps:**
  - Tap **`+ Underclock Step`** or **`+ Overclock Step`**, or enter any custom **Frequency (MHz)** and **Voltage (mV)** and tap **Add**.

### Tab 2: `2. Backup, Export & Flash`
- **Modified Kernel Actions:**
  - **Flash Modified Kernel + `vbmeta` Directly & Verify:** Unpacks via `magiskboot`, patches FDT `opp-hz` & `opp-microvolt` properties, repacks and pads to 100% stock size, flashes patched `vbmeta.img` (`flags=0x03`), `boot.img`, and `vendor_boot.img`, and verifies the partition via SHA-256 read-back.
  - **Export for TWRP (`.zip`):** Saves `KalamaForge_Modified_OC_UC_TWRP_Flashable.zip` containing `boot.img`, `vendor_boot.img`, and `vbmeta.img`.
  - **Export for Odin (`.tar`):** Saves a standard POSIX USTAR `.tar` package (`KalamaForge_Modified_OC_UC_Odin_AP.tar`) bundling `boot.img`, `vendor_boot.img`, and `vbmeta.img` for Odin's `AP` slot.
  - **Extract Modified Kernel Backup (`boot.img`):** Saves the raw repacked `boot.img` (plus `vendor_boot.img` and `vbmeta.img`) to `/sdcard/Download/KalamaForge/`.
- **Original Stock Kernel Backup Actions:**
  - **Reflash Original Stock Kernel & Verify:** Restores your original stock `boot.img`, `vendor_boot.img`, and `vbmeta.img` directly to the device partitions.
  - **Extract Original Kernel Backup (`boot.img`):** Extracts a clean copy of the original stock `boot.img` (or exports it as a Stock TWRP `.zip` / Stock Odin `.tar`).
