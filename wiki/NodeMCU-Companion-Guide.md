# NodeMCU Companion Hardware: Complete Installation & Troubleshooting Guide

This guide covers the entire setup, flashing, wiring, and troubleshooting process for the NodeMCU (ESP8266) companion hardware used with the GhostH wardriving suite.

---

## 1. Overview

The NodeMCU companion acts as an external sensor node that offloads radio capture and sensor detection from the primary Android phone:
- **Continuous 802.11 Wi-Fi Monitoring**: Scans channels 1 through 13 for access points, hidden SSIDs, and signal strength (RSSI).
- **Flock ALPR Infrared Strobe Discrimination**: Hardware interrupt on GPIO 4 (`D2`) that detects pulsed 850nm / 940nm infrared camera illumination between 25 Hz and 65 Hz.
- **Dual Telemetry Output**:
  - Direct USB Serial communication (115200 baud) over USB-OTG.
  - Wireless Access Point (`GhostH`) with a TCP telemetry server on port 8888.
- **Self-Hosted Web Portal**: Web interface at `http://192.168.4.1` allowing users to change the access point name and password directly from a phone browser.

---

## 2. Hardware Requirements

| Component | Recommendation | Notes |
|---|---|---|
| **Microcontroller Board** | NodeMCU v2 or v3 (ESP8266 ESP-12E / ESP-12F) | Either CP2102 or CH340 USB chip. |
| **Micro-USB Cable** | **Must be a Data Sync Cable** | Standard charging cables do not have data lines. |
| **USB-OTG Adapter** | USB-C to USB-A (or Micro-USB) | Required for direct cable connection to Android. |
| **Optical IR Module** (Optional) | LM393 Infrared Photodiode / Receiver module | Detects pulsed optical strobes for Flock ALPR. |
| **Piezo Buzzer** (Optional) | 5V Active Buzzer | Provides audible alerts upon detection. |

---

## 3. Critical Cable Note: Data Cable vs. Charge-Only Cable

> [!WARNING]
> The single most common issue during setup is using a **charge-only micro-USB cable**.
> 
> Many cables packaged with battery banks, wireless earbuds, or toys only carry power (+5V and Ground) and **lack the D+ and D- data wires**.
> - With a charge-only cable: The NodeMCU LED turns on, but your computer or phone will not detect any USB device.
> - With a data cable: The operating system detects a serial device (`/dev/ttyUSB0` on Linux, `COMx` on Windows).

---

## 4. Flashing the Firmware

Pre-compiled binary location: `release/nodemcu_ghosth_firmware.bin`  
Source code location: `firmware/nodemcu_ghosth/nodemcu_ghosth.ino`

### Method A: Linux (Command Line via Script)

1. Connect the NodeMCU to your computer with a data cable.
2. Open terminal in the project directory and run:
   ```bash
   cd firmware/nodemcu_ghosth
   ./flash_nodemcu.sh
   ```
3. The script will automatically locate `/dev/ttyUSB0`, erase old flash memory, and write the GhostH firmware.

### Method B: Linux / macOS / Windows (Manual esptool)

If you prefer using `esptool.py` directly:
```bash
# 1. Erase existing memory
python3 -m esptool --port /dev/ttyUSB0 erase_flash

# 2. Write firmware binary
python3 -m esptool --port /dev/ttyUSB0 --baud 460800 write_flash -fm dio 0x00000000 release/nodemcu_ghosth_firmware.bin
```
*(On Windows, replace `/dev/ttyUSB0` with your COM port, e.g., `COM3`)*.

### Method C: Web Browser (Chrome / Edge / Android)

You can flash the board directly from a web browser without installing any tools:
1. Open **[Espressif Web ESPTool](https://espressif.github.io/esptool-js/)** or **[ESP Web Tools](https://esp.adrian-alvarez.es/)**.
2. Connect your NodeMCU to your computer or Android phone (via OTG).
3. Select Baud Rate: **115200** or **460800**.
4. Click **Connect** and select the USB serial device.
5. In the file upload section, select `nodemcu_ghosth_firmware.bin` at address `0x0000`.
6. Click **Program** to complete the flash.

---

## 5. Connecting the NodeMCU to the Android Application

Once flashed, the NodeMCU can connect in two different ways:

### Mode 1: USB-OTG Direct Connection (Recommended for low latency)
1. Plug the NodeMCU into your phone using a USB-OTG adapter.
2. In the Android app, go to **Main Menu** -> **External Hardware**.
3. Tap **SCAN & CONNECT USB SERIAL (OTG)**.
4. When prompted by Android, check "Always allow" and tap **OK**.
5. The status badge will change to **USB CONNECTED** and live telemetry will appear in the log.

### Mode 2: Wi-Fi Access Point Connection
1. Power the NodeMCU from any standard USB port, battery pack, or 12V car adapter.
2. On your phone, open Wi-Fi settings and look for the network:
   - **SSID**: `GhostH`
   - **Password**: `ghosth1234`
3. Connect your phone to `GhostH`.
4. Open the Android app, navigate to **External Hardware**, and tap **CONNECT GHOSTH WI-FI (192.168.4.1:8888)**.
5. Telemetry data will begin streaming over the local TCP socket.

---

## 6. Changing Access Point SSID and Password

You can customize the Wi-Fi credentials at any time:
1. Connect your phone or PC to the `GhostH` Wi-Fi network.
2. Open a web browser and navigate to:
   ```text
   http://192.168.4.1
   ```
   *(Or tap **OPEN WEB CONFIG** in the External Hardware screen)*.
3. Enter your desired **SSID** and **Password** (minimum 8 characters).
4. Tap **SAVE & REBOOT**.
5. The new credentials are permanently stored in EEPROM and will persist across reboots.

---

## 7. Optical Flock ALPR Sensor Wiring

To detect optical infrared strobes used by fixed and mobile license plate cameras:

### Wiring Table

| Sensor Pin | NodeMCU Pin | Function |
|---|---|---|
| **VCC** | **3V3** or **Vin** | Power supply (3.3V or 5V) |
| **GND** | **G** (GND) | Ground |
| **DO** (Digital Output) | **D2** (GPIO 4) | Pulse interrupt input (triggers on falling edge) |
| **AO** (Analog Output) | **A0** (ADC0) | Raw irradiance amplitude measurement |
| **Buzzer (+)** | **D1** (GPIO 5) | Active alert output when strobe is verified |
| **Buzzer (-)** | **G** (GND) | Buzzer ground |

### Calibration
Adjust the small blue potentiometer on the LM393 sensor module:
- Turn until the onboard threshold indicator LED turns OFF in normal daylight or indoor lighting.
- When an infrared source (such as a TV remote or 850nm illuminator) flashes, the indicator LED should pulse briefly.

---

## 8. Troubleshooting Guide

### Problem 1: Computer does not detect NodeMCU (`/dev/ttyUSB0` does not exist)

- **Cause**: Almost always a power-only micro-USB cable.
- **Verification**:
  Run this command in a terminal:
  ```bash
  dmesg -w
  ```
  Unplug and reconnect the board. If no new messages appear, the cable does not have data pins.
- **Fix**: Replace the cable with a known data cable (e.g., from an external hard drive or smartphone).

### Problem 2: Wi-Fi network `GhostH` is not visible after flashing

- **Cause 1**: The board was not wiped before flashing, leaving old Wi-Fi settings in flash.
  - **Fix**: Run a full chip erase before flashing:
    ```bash
    python3 -m esptool --port /dev/ttyUSB0 erase_flash
    ```
- **Cause 2**: Using outdated firmware code where promiscuous mode interfered with SoftAP beacons.
  - **Fix**: Flash firmware version 2.1 or newer from `release/nodemcu_ghosth_firmware.bin`.
- **Cause 3**: Board power is insufficient.
  - **Fix**: Ensure the USB port supplies at least 500mA. Some unpowered USB hubs cause the ESP8266 Wi-Fi radio to reset.

### Problem 3: Linux driver questions (Do I need to install CH340 / CP2102 drivers?)

- **Answer**: **No.** Modern Linux kernels (Debian, Ubuntu, Kali, Arch, Fedora) include both `ch341` and `cp210x` drivers out-of-the-box.
- **Permissions**: Ensure your user account belongs to the `dialout` group:
  ```bash
  sudo usermod -aG dialout $USER
  ```
  *(Log out and back in for permissions to take effect)*.

### Problem 4: Android phone does not recognize the NodeMCU over USB-OTG

- **Cause 1**: Some Android devices (OnePlus, Oppo, Realme, Vivo) disable USB-OTG after 10 minutes of inactivity.
  - **Fix**: Open Android **Settings** -> search for **OTG Connection** -> toggle it **ON**.
- **Cause 2**: Defective or loose USB-OTG adapter.
  - **Fix**: Verify the adapter with a standard USB flash drive first.

### Problem 5: Phone disconnects from `GhostH` Wi-Fi automatically

- **Cause**: Android detects that `GhostH` has no internet access and switches back to cellular data or home Wi-Fi.
- **Fix**: When Android displays the notification "This network has no internet access", tap it and select **Keep Connection** / **Do not ask again**. Alternatively, temporarily disable mobile data while wardriving.

---

## 9. Telemetry Data Format

All communication between the NodeMCU and the Android application uses clean, passive metadata lines:

```text
GHOSTH,BEACON,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
GHOSTH,ALPR,FL:OC:CA:LP:01:00,<IR_LEVEL>,1,FLOCK_IR_STROBE_<FREQ>HZ
GHOSTH,HEARTBEAT,00:00:00:00:00:00,0,1,GhostH_Companion_Alive
```

No network payloads are captured or decrypted, ensuring 100% passive, metadata-only operation.
