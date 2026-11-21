/*
 * GhostH Promiscuous Mode Wi-Fi & BLE Companion Firmware for ESP32
 * Compatible with Arduino IDE & ESP-IDF.
 * 
 * Functions:
 * 1. Puts Wi-Fi into 802.11 Promiscuous Sniffer mode.
 * 2. Continuously hops across 2.4 GHz channels 1 - 14.
 * 3. Captures Beacon, Probe Request, Probe Response, and Deauth frames.
 * 4. Transmits parsed telemetry over USB-Serial (115200 baud) and Bluetooth Serial
 *    to the GhostH Android app in real time.
 */

#include <WiFi.h>
#include <esp_wifi.h>
#include <BluetoothSerial.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run make menuconfig to enable it
#endif

BluetoothSerial SerialBT;

#define HOP_INTERVAL_MS 250
uint8_t current_channel = 1;
unsigned long last_hop_time = 0;

// 802.11 Frame Control Types
#define FRAME_TYPE_BEACON      0x80
#define FRAME_TYPE_PROBE_REQ   0x40
#define FRAME_TYPE_PROBE_RESP  0x50
#define FRAME_TYPE_DEAUTH      0xC0

// Packet sniffer callback
void wifi_promiscuous_rx(void* buf, wifi_promiscuous_pkt_type_t type) {
    if (type != WIFI_PKT_MGMT) return;

    const wifi_promiscuous_pkt_t* pkt = (wifi_promiscuous_pkt_t*)buf;
    const uint8_t* payload = pkt->payload;
    int len = pkt->rx_ctrl.sig_len;
    int rssi = pkt->rx_ctrl.rssi;
    int channel = pkt->rx_ctrl.channel;

    if (len < 24) return;

    uint8_t frame_control = payload[0];
    char bssid_str[18];
    char ssid_str[33] = "";

    // Extract BSSID (Address 3 in management frames: offset 16..21)
    snprintf(bssid_str, sizeof(bssid_str), "%02X:%02X:%02X:%02X:%02X:%02X",
             payload[16], payload[17], payload[18], payload[19], payload[20], payload[21]);

    const char* frame_type_str = "MGMT";
    if (frame_control == FRAME_TYPE_BEACON) {
        frame_type_str = "BEACON";
        // SSID IE is at offset 36 in Beacon frames
        if (len > 38 && payload[36] == 0) { // Tag 0 = SSID
            uint8_t ssid_len = payload[37];
            if (ssid_len > 32) ssid_len = 32;
            if (38 + ssid_len <= len) {
                memcpy(ssid_str, &payload[38], ssid_len);
                ssid_str[ssid_len] = '\0';
            }
        }
    } else if (frame_control == FRAME_TYPE_PROBE_REQ) {
        frame_type_str = "PROBEREQ";
        // SSID IE is at offset 24 in Probe Requests
        if (len > 26 && payload[24] == 0) {
            uint8_t ssid_len = payload[25];
            if (ssid_len > 32) ssid_len = 32;
            if (26 + ssid_len <= len) {
                memcpy(ssid_str, &payload[26], ssid_len);
                ssid_str[ssid_len] = '\0';
            }
        }
    } else if (frame_control == FRAME_TYPE_DEAUTH) {
        frame_type_str = "DEAUTH";
    }

    // Output formatted ASCII telemetry line:
    // GHOSTH,<TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
    char msg[128];
    snprintf(msg, sizeof(msg), "GHOSTH,%s,%s,%d,%d,%s\n",
             frame_type_str, bssid_str, rssi, channel, ssid_str);

    Serial.print(msg);
    if (SerialBT.hasClient()) {
        SerialBT.print(msg);
    }
}

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n[GhostH ESP32 Promiscuous Sniffer Initializing...]");

    // Initialize Bluetooth Serial
    SerialBT.begin("GhostH-ESP32-Sniffer");
    Serial.println("[Bluetooth Serial SPP Ready: GhostH-ESP32-Sniffer]");

    // Put Wi-Fi into promiscuous mode
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    esp_wifi_set_promiscuous(true);
    esp_wifi_set_promiscuous_rx_cb(&wifi_promiscuous_rx);
    esp_wifi_set_channel(current_channel, WIFI_SECOND_CHAN_NONE);

    Serial.println("[Promiscuous Wi-Fi Monitor Active. Hopping channels...]");
}

void loop() {
    unsigned long now = millis();
    if (now - last_hop_time >= HOP_INTERVAL_MS) {
        last_hop_time = now;
        current_channel++;
        if (current_channel > 13) {
            current_channel = 1;
        }
        esp_wifi_set_channel(current_channel, WIFI_SECOND_CHAN_NONE);
    }
    delay(10);
}

