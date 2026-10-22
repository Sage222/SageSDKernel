# KalamaForge — Simple Snapdragon 8 Gen 2 (`SM8550`) Frequency/Voltage Editor & Kernel Flasher

**KalamaForge** (`com.aistudio.kalamaforge.sm8550`) is a clean, easy-to-use Android app for rooted Samsung **Snapdragon 8 Gen 2 for Galaxy (`SM8550-AC`)** devices with an unlocked bootloader.

---

## 📦 Pre-Compiled APK

The compiled APK is tracked directly in this repository under [`releases/KalamaForge-sm8550-v1.0.apk`](releases/KalamaForge-sm8550-v1.0.apk):

| File | Application ID | SHA-256 Checksum |
| :--- | :--- | :--- |
| [`releases/KalamaForge-sm8550-v1.0.apk`](releases/KalamaForge-sm8550-v1.0.apk) | `com.aistudio.kalamaforge.sm8550` | `509efdcbc735a0648eef5a0691a84e6556198c84e33b1b4477261420040d74b3` |

---

## ✨ What It Does (2 Simple Tabs)

### Tab 1: `1. Frequencies & Voltages`
- **Clearly See Stock Frequencies & Voltages:**
  - Switch between **Prime X3** (`595 – 3360 MHz`), **Gold Cores** (`499 – 2803 MHz`), **Silver Cores** (`307 – 2016 MHz`), and **Adreno 740 GPU** (`225 – 719 MHz`).
  - Every stock step displays its **Stock Reference** (e.g., `Stock Reference: 3360 MHz @ 1065 mV`) alongside your active settings.
- **Adjust Voltages & Frequencies Independently:**
  - Every frequency step has independent **Voltage buttons** (`-25 mV`, `-5 mV`, `+5 mV`, `+25 mV`) so you can undervolt or overvolt any step without changing its frequency.
  - Every step also has independent **Frequency buttons** (`-50 MHz`, `-15 MHz`, `+15 MHz`, `+50 MHz`) so you can shift frequencies without changing voltage.
  - Includes one-tap **`-10 mV All`** / **`+10 mV All`** cluster voltage buttons and a **`Reset Stock`** button.
- **Easily Add Overclock & Underclock Steps:**
  - Tap **`+ Underclock Step`** or **`+ Overclock Step`**, or enter any custom **Frequency (MHz)** and **Voltage (mV)** and tap **Add**.

### Tab 2: `2. Backup, Export & Flash`
- **Modified Kernel (Overclock / Underclock):**
  - **Flash Modified Kernel Directly & Verify:** Flashes the signed modified kernel directly to the `boot` partition and reads it back to verify the SHA-256 checksum matches.
  - **Export for TWRP (`.zip`):** Saves a flashable TWRP recovery ZIP (`KalamaForge_Modified_OC_UC_TWRP_Flashable.zip`).
  - **Export for Odin (`.tar`):** Saves a standard POSIX USTAR `.tar` package (`KalamaForge_Modified_OC_UC_Odin_AP.tar`) ready for the `AP` slot in Odin.
  - **Extract Modified Kernel Backup (`boot.img`):** Saves the raw modified `boot.img` to `/sdcard/Download/KalamaForge/`.
- **Original Stock Kernel Backup:**
  - **Reflash Original Stock Kernel & Verify:** Restores your original stock kernel backup directly to the partition and verifies the SHA-256 checksum.
  - **Extract Original Kernel Backup (`boot.img`):** Extracts a clean copy of the original stock `boot.img` (or exports it as a Stock TWRP `.zip` / Stock Odin `.tar`).
