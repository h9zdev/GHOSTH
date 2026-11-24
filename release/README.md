# GhostH Wardrive & NodeMCU Companion — Release Package

This folder contains pre-built binaries for the **GhostH / Wardriver HX OS** ecosystem:
- **`GhostH-Wardrive.apk`**: The upgraded Android Wardriving application featuring the full Intelligence engine (Density prediction, Anomaly detection, Signal Intelligence, Clustering) and External Radio Hardware control.
- **`nodemcu_ghosth_firmware.bin`**: Ready-to-flash binary for ESP8266 / NodeMCU v2 / v3 sniffer & optical Flock ALPR pulse discriminator.

---

## 1. NodeMCU Firmware Specifications

- **Default Wi-Fi AP**: `GhostH`
- **Default Password**: `ghosth1234`
- **Telemetry Server**: TCP port `8888` @ `192.168.4.1`
- **Web Configuration Portal**: `http://192.168.4.1` (Change SSID & Password directly from any phone browser; persisted to EEPROM)
- **USB Serial Baud Rate**: `115200 baud` (8N1)
- **Promiscuous Sniffing**: 802.11 Beacons & Probe Requests (Channels 1–13 hopping every 250ms)
- **Flock ALPR Optical Discriminator**: GPIO 4 (`D2`) interrupt timing for $25\text{--}65\,\text{Hz}$ pulsed IR strobes.

---

## 2. Flashing NodeMCU Firmware

> [!IMPORTANT]
> **Use a Micro-USB Cable with DATA Lines:**
> Many micro-USB cables are power-only / charging cables that lack D+/D- wires. If your computer does not show `/dev/ttyUSB0` or `COMx` when plugged in, the board cannot be flashed and will keep running its old firmware. Use a known USB data sync cable.

### Option A: Using the Auto-Flash Script (Linux / Mac)
Plug your NodeMCU into your computer with a USB data cable and run:
```bash
cd firmware/nodemcu_ghosth
./flash_nodemcu.sh
```

### Option B: Using `esptool.py` (Command Line)
```bash
# Erase old flash
esptool.py --port /dev/ttyUSB0 erase_flash

# Flash GhostH firmware
esptool.py --port /dev/ttyUSB0 --baud 460800 write_flash 0x0 release/nodemcu_ghosth_firmware.bin
```
*(On Windows, replace `/dev/ttyUSB0` with your COM port, e.g., `COM3`)*.

### Option C: Using NodeMCU Flasher or Web Flasher
Open [ESP Web Flasher](https://esp.adrian-alvarez.es/) or [Web ESPTool](https://espressif.github.io/esptool-js/) in Chrome/Edge, connect at 115200 baud, and write `nodemcu_ghosth_firmware.bin` to address `0x00000`.

---

## 3. Connecting NodeMCU to Android App

Once flashed, the board operates in dual mode (USB + Wi-Fi):

### Method 1: USB-OTG Direct Connection (Recommended for in-vehicle wardriving)
1. Connect the NodeMCU to your Android phone via a USB-OTG adapter.
2. In the app, navigate to **Main Menu -> External Hardware**.
3. Tap **SCAN & CONNECT USB SERIAL (OTG)**.
4. When prompted by Android, grant USB permission.
5. Ingested 802.11 frames and Flock ALPR alerts will stream directly into the app at 115200 baud with zero latency.

### Method 2: Wi-Fi Access Point Connection
1. Power the NodeMCU from any USB battery pack, car 12V charger, or phone.
2. On your phone, connect to the Wi-Fi network **`GhostH`** using password **`ghosth1234`**.
3. In the app, navigate to **Main Menu -> External Hardware**.
4. Tap **CONNECT GHOSTH WI-FI (192.168.4.1:8888)**.
5. Real-time telemetry will stream into the app over TCP.

### Changing Wi-Fi SSID and Password
1. Connect your phone or laptop to `GhostH`.
2. Open browser and go to: `http://192.168.4.1` (or tap **OPEN WEB CONFIG** in the app's External Hardware screen).
3. Enter your custom SSID and password.
4. Tap **SAVE & REBOOT**. The new credentials will be saved to EEPROM.

