# GhostH ESP32 Promiscuous Sniffer Companion

This firmware turns any standard ESP32 development board (NodeMCU-32S, ESP32-WROOM, ESP32-CAM, M5Stack, LilyGO, Marauder) into a wireless promiscuous packet sniffer that feeds 802.11 management frames (Beacons, Probe Requests, Probe Responses, and Deauths) directly into the GhostH Android app.

## Capabilities
- **Promiscuous Monitor Mode**: Passively captures raw Wi-Fi frames without associating or transmitting.
- **Channel Hopping**: Automatically rotates across channels 1–13 every 250ms.
- **Dual Connectivity**:
  - **USB-Serial**: 115200 baud over USB OTG directly to your Android device.
  - **Bluetooth Serial (SPP)**: Wirelessly pairs as `GhostH-ESP32-Sniffer`.
- **GhostH Stream Protocol**: Outputs frames in the format:
  `GHOSTH,<FRAME_TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>`

## Flashing Instructions
1. Open `ghosth_promiscuous.ino` in Arduino IDE or VS Code with PlatformIO.
2. Select your ESP32 board and COM port.
3. In `Tools`:
   - Flash Frequency: 80MHz
   - Partition Scheme: Default 4MB with spiffs
4. Click **Upload**.
5. Pair with your phone over Bluetooth or connect via USB OTG cable. In the GhostH app, navigate to **Hardware & ML** and tap **Connect ESP32**.

