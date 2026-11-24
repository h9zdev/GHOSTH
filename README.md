# GHOSTH - Every signal leaves a trace.


<p align="center">
  <img src="https://github.com/h9zdev/GHOSTH/blob/main/GHOSTH.png" alt="GHOSTH" />
</p>
<div align="center">

<br>

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen.svg?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Desktop](https://img.shields.io/badge/Desktop-Linux%20%7C%20Windows-blue.svg?style=flat-square&logo=linux&logoColor=white)](https://github.com/h9zdev/GHOSTH/releases)
[![Firmware](https://img.shields.io/badge/Firmware-ESP8266%20NodeMCU%20v2.2-orange.svg?style=flat-square&logo=espressif&logoColor=white)](firmware/nodemcu_ghosth/)
[![Release](https://img.shields.io/badge/Release-v2.0.0-green.svg?style=flat-square)](https://github.com/h9zdev/GHOSTH/releases/tag/v2.0.0)

<br>

</div>

**GhostH** (`droid.wardriver.hx.os`) is an advanced Android platform for wardriving, RF signal intelligence, and wireless spatial analytics. Built on the proven WiGLE Android core, it adds a passive machine learning and spatial analytics engine, offline OpenStreetMap vector mapping, and a purpose-built dark interface for field use. An external NodeMCU companion subsystem provides multi-channel RF harvesting and optical infrared strobe detection (Flock ALPR), and a cross-platform desktop suite supports Windows and Linux.

<div align="center">

<br>

[![Download APK](https://img.shields.io/badge/Download-Android%20APK-brightgreen.svg?style=flat-square&logo=android&logoColor=white)](https://github.com/h9zdev/GHOSTH/releases/download/v2.0.0/GhostH-Wardrive.apk)
[![Download Linux](https://img.shields.io/badge/Download-Desktop%20Linux-blue.svg?style=flat-square&logo=linux&logoColor=white)](https://github.com/h9zdev/GHOSTH/releases/download/v2.0.0/GhostH-Desktop-Linux.tar.gz)
[![Download Windows](https://img.shields.io/badge/Download-Desktop%20Windows-0078D6.svg?style=flat-square&logo=windows&logoColor=white)](https://github.com/h9zdev/GHOSTH/releases/download/v2.0.0/GhostH-Desktop-Windows.zip)
[![Download Firmware](https://img.shields.io/badge/Download-NodeMCU%20Firmware-orange.svg?style=flat-square&logo=espressif&logoColor=white)](https://github.com/h9zdev/GHOSTH/releases/download/v2.0.0/nodemcu_ghosth_firmware.bin)

<br>

📖 **[GhostH Official GitHub Wiki](https://github.com/h9zdev/GHOSTH/wiki)**
🌐 **[Developer Website](https://haybnz.web.app/)**


</div>


## 📱 Android App Installation & Setup

### Prerequisites
* Android device running **Android 8.0 (Oreo / API 26) or higher** (tested up to Android 14/15).
* GPS / Location hardware with high-accuracy mode enabled.
* Wi-Fi and Bluetooth / BLE hardware enabled.

### Option A: Install Pre-built APK
1. Download `GhostH-Wardrive.apk` from [GitHub Releases](https://github.com/h9zdev/GHOSTH/releases/tag/v2.0.0).
2. Transfer the file to your phone, or download directly in your mobile browser.
3. Open your file manager, tap `GhostH-Wardrive.apk`, and select **Install**. (If prompted, allow installation from unknown sources).
4. **Via ADB (Command Line)**:
   ```bash
   adb install -r GhostH-Wardrive.apk
   ```

### Option B: Build APK from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/h9zdev/GHOSTH.git
   cd GHOSTH
   ```
2. Build the debug APK using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install onto your connected Android device:
   ```bash
   adb install -r wiglewifiwardriving/build/outputs/apk/debug/wiglewifiwardriving-debug.apk
   ```
4. Launch the application:
   ```bash
   adb shell am start -n droid.wardriver.hx.os/net.wigle.wigleandroid.MainActivity
   ```

### Initial Android App Permissions & Settings
1. **Location Permission**: Grant **"Allow all the time"** (Precise Location). Background location is required so wardriving scans continue when your screen is locked.
2. **Nearby Devices / Bluetooth**: Allow access to scan for BLE beacons and companion sensors.
3. **Battery Optimization**: In Android Settings $\rightarrow$ Apps $\rightarrow$ GhostH $\rightarrow$ Battery, select **"Unrestricted"** to prevent Android from sleeping the scanning background service.
4. **Wi-Fi Scan Throttling (Developer Options)**:
   * Go to Android Settings $\rightarrow$ System $\rightarrow$ Developer options.
   * Under Networking, toggle **Wi-Fi scan throttling** to **OFF** for real-time 4-second scan intervals instead of Android's throttled 30-minute intervals.

### WiGLE API Credentials Configuration
1. Register an account at [WiGLE.net](https://wigle.net) and navigate to **Account $\rightarrow$ API**.
2. Click **Generate API Token** to receive your **API Name** (e.g., `AID...`) and **API Token** (e.g., `32-character secret`).
3. In GhostH on your phone:
   * Open the left navigation drawer and tap **Settings**.
   * Under **WiGLE Account / API**, enter your **API Name** and **API Token**.
   * Tap **Validate Credentials** to test connectivity.
4. *Privacy Guard*: GhostH automatically excludes any network with an SSID containing `GHOSTH` (case-insensitive) from WiGLE CSV generation and API uploads, preventing your companion hardware from leaking to public databases.

---

## 📡 NodeMCU Companion Firmware (ESP8266) Setup

The NodeMCU companion node augments your Android device with continuous, hardware-accelerated 2.4 GHz channel hopping and optional optical infrared strobe detection for automated Flock Safety ALPR identification.

### Hardware Requirements
* **NodeMCU Board**: ESP8266 (ESP-12E / ESP-12F module) with micro-USB.
* **USB-to-UART Chip**: Silicon Labs **CP2102** or WCH **CH340G**.
* **Micro-USB Data Cable** (ensure cable has data lines, not charge-only).
* *(Optional)* **Optical IR Sensor**: LM393 IR receiver module connected to **D2 (GPIO4)** for Flock ALPR optical pulse recognition.

### USB Drivers (Linux & Windows)
* **Linux**:
  * The Linux kernel includes native drivers for both `cp210x` and `ch341` out of the box.
  * Ensure your user has access to serial ports by adding yourself to the `dialout` group:
    ```bash
    sudo usermod -a -G dialout $USER
    ```
  * Log out and log back in, or grant temporary access:
    ```bash
    sudo chmod 666 /dev/ttyUSB0
    ```
* **Windows**:
  * CP2102: Download and install the [Silicon Labs CP210x VCP Driver](https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers).
  * CH340: Download and install the [WCH CH341SER Driver](http://www.wch-ic.com/downloads/CH341SER_EXE.html).

### Option A: Fast Flash via esptool (Binary)
1. Download `nodemcu_ghosth_firmware.bin` from [GitHub Releases](https://github.com/h9zdev/GHOSTH/releases/tag/v2.0.0).
2. Install `esptool`:
   ```bash
   pip install esptool
   ```
3. Connect your NodeMCU via USB and flash:
   * **Linux**:
     ```bash
     python3 -m esptool --chip esp8266 --port /dev/ttyUSB0 --baud 460800 write-flash --flash-mode dio 0x00000 nodemcu_ghosth_firmware.bin
     ```
   * **Windows** (check Device Manager for COM port, e.g., `COM3`):
     ```cmd
     python -m esptool --chip esp8266 --port COM3 --baud 460800 write-flash --flash-mode dio 0x00000 nodemcu_ghosth_firmware.bin
     ```
   * *Alternative Script*: Run `bash firmware/nodemcu_ghosth/flash_nodemcu.sh` from the repository.

### Option B: Build and Flash from Source
1. Install **Arduino IDE** or **arduino-cli**.
2. Add ESP8266 board manager URL in Preferences:
   ```text
   http://arduino.esp8266.com/stable/package_esp8266com_index.json
   ```
3. Install board package `esp8266:esp8266` (version 3.1.2 or newer).
4. Open `firmware/nodemcu_ghosth/nodemcu_ghosth.ino`.
5. Select Board: **NodeMCU 1.0 (ESP-12E Module)**, Upload Speed: **460800**, CPU Frequency: **160 MHz**.
6. Click **Upload** (or use `arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 ...`).

### SoftAP & HayOS Tactical Web Portal
Once powered, the NodeMCU broadcasts an access point:
* **SSID**: `GhostH`
* **Password**: `ghosth1234`
* **Static IP**: `192.168.4.1`
* **Web Portal**: Open `http://192.168.4.1` in your browser.
  * **Tactical HayOS UI**: Pure black background (`#050505`) and Cyber Cyan styling (`#00FFD1`).
  * **Live Wi-Fi Table**: Shows live nearby SSIDs, BSSIDs, RSSI signal levels, and channels harvested by the chip, auto-refreshing via `/api/scan`.
  * **Configuration Card**: Modify AP SSID and passphrase safely stored in EEPROM.
  * **Developer Links**: Quick access to [haybnz.web.app](https://haybnz.web.app/) and [GitHub Wiki](https://github.com/h9zdev/GHOSTH/wiki).

### Connecting NodeMCU to the Android App
There are two connection modes:
1. **USB-OTG Serial Mode**:
   * Connect the NodeMCU to your Android phone via a USB-OTG adapter.
   * Open GhostH $\rightarrow$ drawer $\rightarrow$ **External Hardware**.
   * Set baud rate to `115200` and tap **Connect USB-OTG**. Telemetry packets will stream directly into the radar map and database.
2. **Wi-Fi Companion Mode**:
   * On your phone, connect to the Wi-Fi network `GhostH` (password: `ghosth1234`).
   * In GhostH $\rightarrow$ **External Hardware**, under NodeMCU Network Stream, enter IP `192.168.4.1` and Port `8888`.
   * Tap **Connect TCP Stream**.

---

## 💻 Desktop Suite (Windows & Linux) Setup

The GhostH Desktop Suite is a standalone graphical workstation that pairs with your Android phone and NodeMCU hardware.

### Key Capabilities
* **Hardware Ingestion**: Ingests serial telemetry from NodeMCU over USB (auto-detecting baud 115200) or over Wi-Fi socket (`192.168.4.1:8888`).
* **Two-Way Mobile Sync**: Runs a local TCP sync server on port `8889` that receives observation dumps from your Android device over LAN.
* **WiGLE API Uploader**: Validates WiGLE API tokens and directly uploads harvested wardriving sessions to WiGLE servers.
* **WiGLE CSV Export**: Generates compliant WiGLE CSV v1.4 files with automatic filtering of `GHOSTH` SSIDs.

### Running on Linux
1. Download `GhostH-Desktop-Linux.tar.gz` from [GitHub Releases](https://github.com/h9zdev/GHOSTH/releases/tag/v2.0.0).
2. Extract and run:
   ```bash
   tar -xzf GhostH-Desktop-Linux.tar.gz
   cd GhostH-Desktop-Linux
   chmod +x ghosth_desktop
   ./ghosth_desktop
   ```

### Running on Windows
1. Download `GhostH-Desktop-Windows.zip` from [GitHub Releases](https://github.com/h9zdev/GHOSTH/releases/tag/v2.0.0).
2. Extract the archive.
3. Double-click `GhostH-Desktop.exe` to launch the GUI.

### Running from Source Code
1. Download `GhostH-Desktop-Source.zip` from [GitHub Releases](https://github.com/h9zdev/GHOSTH/releases/tag/v2.0.0).
2. Extract the files and install dependencies:
   ```bash
   pip install pyserial requests
   ```
3. Run the application:
   ```bash
   python3 ghosth_desktop.py
   ```

### Two-Way Mobile Sync (App to PC)
1. Ensure your PC and Android phone are on the same local network (or phone is connected to a PC Wi-Fi hotspot).
2. On your PC, open GhostH Desktop. The bottom status bar shows: `Sync Server: Listening on port 8889 (IP: 192.168.x.x)`.
3. On your phone:
   * Open GhostH $\rightarrow$ drawer $\rightarrow$ **External Hardware**.
   * Scroll to **GHOSTH DESKTOP SUITE (PC / LINUX)**.
   * Enter your PC's IP address and Port `8889`.
   * Tap **Connect Desktop App**, then tap **Sync Observations to Desktop**.
4. All observations cached on the mobile device are transferred to the desktop database.

### Desktop Direct WiGLE Uploader
1. In GhostH Desktop, enter your **WiGLE API Name** and **API Token** in the right-hand panel.
2. Click **Test API Credentials**.
3. Once validated, click **Upload to WiGLE API**. GhostH formats the dataset into WiGLE CSV v1.4, strips internal companion networks, and uploads directly via HTTPS multipart POST.

---

## 🧠 Wireless Intelligence & ML Analytics Engine

GhostH features 7 passive on-device intelligence analyzers accessible from the **Intelligence Dashboard** in the app:

| Analyzer Module | Algorithmic Implementation | Tactical Purpose |
| :--- | :--- | :--- |
| **1. Wireless Density Predictor** | 2D Spatial Binning ($0.001^\circ$ grid), Inverse Distance Weighting (IDW), Polynomial Trend Fitting | Forecasts wireless congestion zones and signal coverage holes. |
| **2. Anomaly Detection Engine** | Rolling Exponentially Weighted Moving Average (EWMA), Dynamic Z-Score Thresholding | Discovers rogue APs, suspicious RSSI spikes, beacon anomalies, and MAC changes. |
| **3. Signal Intelligence (SIGINT)** | Tri-Band Channel Congestion (2.4 GHz, 5 GHz, 6 GHz Wi-Fi 6E/7), Shannon Entropy, Signal-to-Noise Ratio (SNR) | Evaluates spectral pollution and channel utilization. |
| **4. Temporal Analytics** | Sliding Observation Windows, Jaccard Similarity Coefficient, First-Seen / Last-Seen Tracking | Detects transient mobile APs versus permanent infrastructure. |
| **5. Environmental Clustering** | K-Means / Centroid Heuristics across SSID/RSSI/Auth feature vectors | Classifies RF profiles into Enterprise, Commercial, Residential, or IoT environments. |
| **6. Geospatial Clustering** | Density-Based Spatial Clustering of Applications with Noise (DBSCAN) | Computes spatial centroids and flags high-velocity moving transmitters. |
| **7. Predictive Analytics** | Multi-Interval Trend Velocity & Linear Regression Trajectory Extrapolation | Estimates real-time network discovery rates and predicts density frontiers. |

---

## 🔧 Troubleshooting & FAQ

### 1. I flashed the NodeMCU, but the `GhostH` Wi-Fi network does not appear.
* **Firmware v2.2 Update**: Ensure you have flashed firmware v2.2 or newer. Older versions hopped channels without delay, suppressing the SoftAP beacon. Firmware v2.2 includes an automatic 1.8-second dwell on Channel 1.
* **Power Supply**: Ensure your NodeMCU is plugged into a USB port capable of providing at least $500\,\text{mA}$. Inadequate power causes the ESP8266 radio to brown out.
* **Serial Verification**: Connect to serial at 115200 baud via terminal or Arduino Serial Monitor. You should see `// GHOSTH NODEMCU COMPANION v2.2 ONLINE`.

### 2. Android shows "Wi-Fi has no internet access" and disconnects from `GhostH`.
* When connected to `GhostH`, Android detects that there is no WAN internet.
* In the Android notification shade, tap the Wi-Fi alert and check **"Stay connected"** or **"Yes, keep connection"**.
* Firmware v2.2 responds to `/generate_204` requests to prevent aggressive captive portal drops.

### 3. Permission denied accessing `/dev/ttyUSB0` on Linux.
* Run:
  ```bash
  sudo usermod -a -G dialout $USER
  sudo chmod 666 /dev/ttyUSB0
  ```
* Unplug and reconnect the USB cable.

### 4. Why is Wi-Fi scanning slow on my Android phone?
* Android enables **Wi-Fi scan throttling** by default (limiting scans to 4 times every 2 minutes for background apps).
* Enable Developer Options: Go to Settings $\rightarrow$ About Phone $\rightarrow$ tap **Build Number** 7 times.
* Go to Settings $\rightarrow$ System $\rightarrow$ Developer options $\rightarrow$ turn **Wi-Fi scan throttling** **OFF**.

### 5. Are my companion hardware packets uploaded to WiGLE?
* **No**. GhostH strictly filters out any observation where the SSID matches or contains `GHOSTH` (case-insensitive) prior to generating WiGLE CSVs and before dispatching API uploads.

---

## 🛡 Passive Operational Principles & Disclaimer

1. **Passive Operation**: GhostH operates in **100% passive listening mode**. It does not perform packet injection, does not authenticate with non-consenting networks, and does not capture packet payload data.
2. **Metadata Compliance**: All telemetry is restricted to standard IEEE 802.11 beacon/probe-response frames, Bluetooth LE advertisements, and optical pulses broadcast across public RF airwaves.
3. **Authorized Auditing**: This tool is designed strictly for authorized wireless site surveys, network engineering, spatial RF research, and personal network defense.

---

## 📜 Attributions & License

* Built upon the open-source **WiGLE Wi-Fi Wardriving** Android client, maintained by [WiGLE.net](https://wigle.net).
* Tactical UI styled with **HayOS** interface architecture by [haybnz.web.app](https://haybnz.web.app/).
* Licensed under the [BSD 3-Clause License](LICENSE).
