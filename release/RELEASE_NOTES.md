# GhostH // Wardriver HX OS v2.0.0

A high-performance, dark-HUD wardriving intelligence suite with external companion hardware support.

---

## 📱 Android Application (`GhostH-Wardrive.apk`)
- **Wireless Density Prediction**: Real-time H3 grid binning, spatial concentration maps, and signal estimation.
- **Anomaly Detection Engine**: Statistical anomaly flagging (Z-score, EWMA, duplicate records, impossible coordinates, signal shifts).
- **Signal & Temporal Intelligence**: 2.4 GHz vs 5 GHz vs 6 GHz spectrum breakdown, channel congestion analysis, and historical variance.
- **Geospatial ML & Clustering**: DBSCAN proximity clustering and coverage heatmaps.
- **External Hardware Manager**:
  - **USB Serial OTG Direct Scanner**: Plug NodeMCU directly into your phone via USB-OTG (CH340 / CP2102 @ 115200 baud).
  - **1-Click GhostH Wi-Fi Companion**: Connect over TCP (`192.168.4.1:8888`) to the NodeMCU access point.
  - **Web Config Portal**: One-tap shortcut to `http://192.168.4.1` to change SSID and password in EEPROM.
  - **Real-time Live Telemetry Log**: Streams live frame lines and ALPR detections directly in the app.

---

## 📡 NodeMCU Companion Firmware (`nodemcu_ghosth_firmware.bin`)
- **Promiscuous 802.11 Sniffing**: Ingests raw beacon and probe request frames across channels 1–13 (hopping every 250ms).
- **Flock ALPR Optical Strobe Discriminator**: Hardware interrupt on GPIO 4 (`D2`) discriminating 25 Hz – 65 Hz infrared strobes (850nm / 940nm).
- **Default Access Point**:
  - **SSID**: `GhostH`
  - **Password**: `ghosth1234`
  - **IP**: `192.168.4.1`
- **Embedded Web Portal**: Navigate to `http://192.168.4.1` in any browser to configure custom SSID and password stored in EEPROM.
- **Dual Telemetry**:
  - USB Serial @ 115200 baud (OTG to phone or PC)
  - TCP Server on Port 8888

---

## 📦 Attached Release Assets
- `GhostH-Wardrive.apk` — Production debug APK ready to install on Android 8.0+ devices.
- `nodemcu_ghosth_firmware.bin` — Compiled binary ready to flash to NodeMCU v2 / v3 (ESP8266) via `esptool` or web flasher.
- `nodemcu_ghosth_source.zip` — Arduino source code (`nodemcu_ghosth.ino`) and automated flashing scripts.

