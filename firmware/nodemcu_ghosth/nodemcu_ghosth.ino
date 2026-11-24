/*
 * =====================================================================
 * GHOSTH // WARDRIVER HX OS - NODEMCU COMPANION FIRMWARE
 * Target: ESP8266 (NodeMCU v2 / v3)
 *
 * Capabilities:
 * 1. Promiscuous 802.11 Wi-Fi Beacon & Probe Request Sniffer (Ch 1 - 14)
 * 2. Flock ALPR Optical Infrared (850nm / 940nm) Strobe Pulse Detector
 * 3. Dual Telemetry Transport:
 *    - USB Serial @ 115200 baud (Direct USB-OTG into Android App)
 *    - Standalone TCP Socket Server (Port 8888 over AP "GhostH-NodeMCU")
 *
 * Compatible with GhostH ExternalHardwareManager protocol:
 * GHOSTH,<TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
 * =====================================================================
 */

#include <ESP8266WiFi.h>
#include <WiFiClient.h>

extern "C" {
  #include "user_interface.h"
}

// -------------------------------------------------------------
// Hardware Pin Definitions
// -------------------------------------------------------------
#define PIN_IR_DIGITAL   4   // D2 (GPIO 4): LM393 IR Comparator Output (Interrupt)
#define PIN_IR_ANALOG    A0  // A0: Raw IR Irradiance Level
#define PIN_ALERT_BUZZER 5   // D1 (GPIO 5): Local Alert Buzzer / LED
#define PIN_ONBOARD_LED  2   // D4 (GPIO 2): Built-in Blue LED (Active LOW)

// -------------------------------------------------------------
// Sniffer & Channel Hopping Configuration
// -------------------------------------------------------------
#define CHANNEL_HOP_INTERVAL_MS 250
#define MAX_CHANNEL 13

uint8_t currentChannel = 1;
unsigned long lastChannelHop = 0;

// -------------------------------------------------------------
// ALPR Optical IR Strobe Timing Variables
// -------------------------------------------------------------
volatile unsigned long lastPulseMicros = 0;
volatile unsigned long pulseIntervalSum = 0;
volatile unsigned int pulseCount = 0;

unsigned long lastIrWindowCheck = 0;
const unsigned long IR_WINDOW_MS = 400;

// -------------------------------------------------------------
// TCP Telemetry Server (For wireless app connection)
// -------------------------------------------------------------
WiFiServer tcpServer(8888);
WiFiClient activeClient;

// -------------------------------------------------------------
// 802.11 Frame Header Structures
// -------------------------------------------------------------
struct RxControl {
    signed rssi:8;
    unsigned rate:4;
    unsigned is_group:1;
    unsigned:1;
    unsigned sig_mode:2;
    unsigned legacy_length:12;
    unsigned damatch0:1;
    unsigned damatch1:1;
    unsigned bssidmatch0:1;
    unsigned bssidmatch1:1;
    unsigned MCS:7;
    unsigned CWB:1;
    unsigned HT_length:16;
    unsigned Smoothing:1;
    unsigned Not_Sounding:1;
    unsigned:1;
    unsigned Aggregation:1;
    unsigned STBC:2;
    unsigned FEC_CODING:1;
    unsigned SGI:1;
    unsigned rxend_state:8;
    unsigned ampdu_cnt:8;
    unsigned channel:4;
    unsigned:12;
};

struct SnifferPacket {
    struct RxControl rx_ctrl;
    uint8_t buf[112];
    uint16_t cnt;
    uint16_t len;
};

// Hardware Interrupt for IR Pulse Detection (Leading Falling Edge)
void ICACHE_RAM_ATTR onIrPulse() {
    unsigned long now = micros();
    unsigned long interval = now - lastPulseMicros;
    lastPulseMicros = now;

    // Filter noise: ALPR pulses typically arrive every 15,000 to 40,000 µs (25 Hz - 65 Hz)
    if (interval >= 14000 && interval <= 42000) {
        pulseIntervalSum += interval;
        pulseCount++;
    }
}

// Broadcasts formatted telemetry over USB Serial and TCP Socket
void broadcastTelemetry(const String &line) {
    Serial.println(line);
    if (activeClient && activeClient.connected()) {
        activeClient.println(line);
    }
}

// 802.11 Promiscuous RX Callback
void ICACHE_RAM_ATTR snifferCallback(uint8_t *buf, uint16_t len) {
    if (len < 36) return;

    struct SnifferPacket *sniffer = (struct SnifferPacket*) buf;
    int rssi = sniffer->rx_ctrl.rssi;
    uint8_t *frame = sniffer->buf;

    uint8_t frameType = frame[0];

    // Beacon Frame (0x80) or Probe Request (0x40) / Probe Response (0x50)
    if (frameType == 0x80 || frameType == 0x40 || frameType == 0x50) {
        char bssidStr[18];
        snprintf(bssidStr, sizeof(bssidStr), "%02X:%02X:%02X:%02X:%02X:%02X",
                 frame[16], frame[17], frame[18], frame[19], frame[20], frame[21]);

        char ssid[33];
        memset(ssid, 0, sizeof(ssid));

        // Extract SSID from Information Elements (offset 36 in Beacon frames)
        if (frameType == 0x80 && len >= 38) {
            uint8_t ssidLen = frame[37];
            if (ssidLen > 0 && ssidLen <= 32 && (38 + ssidLen <= len)) {
                memcpy(ssid, &frame[38], ssidLen);
                ssid[ssidLen] = '\0';
            }
        }

        String typeStr = (frameType == 0x80) ? "BEACON" : "PROBE";
        String ssidStr = String(ssid);
        if (ssidStr.length() == 0) ssidStr = "<Hidden>";

        // Format compliant with GhostH ExternalHardwareManager:
        // GHOSTH,<TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
        String packetLine = "GHOSTH," + typeStr + "," + String(bssidStr) + "," +
                            String(rssi) + "," + String(currentChannel) + "," + ssidStr;

        broadcastTelemetry(packetLine);
    }
}

void setup() {
    Serial.begin(115200);
    delay(200);

    // Initialize GPIOs
    pinMode(PIN_IR_DIGITAL, INPUT_PULLUP);
    pinMode(PIN_ALERT_BUZZER, OUTPUT);
    pinMode(PIN_ONBOARD_LED, OUTPUT);
    digitalWrite(PIN_ALERT_BUZZER, LOW);
    digitalWrite(PIN_ONBOARD_LED, HIGH); // LED off

    // Attach hardware interrupt on D2 for Flock ALPR IR detection
    attachInterrupt(digitalPinToInterrupt(PIN_IR_DIGITAL), onIrPulse, FALLING);

    // Configure Standalone Wi-Fi Access Point for wireless companion mode
    WiFi.mode(WIFI_AP_STA);
    WiFi.softAP("GhostH-NodeMCU", "ghosth1234");
    tcpServer.begin();

    // Enable 802.11 promiscuous packet sniffing
    wifi_set_opmode(STATION_MODE);
    wifi_promiscuous_enable(0);
    WiFi.disconnect();
    wifi_set_promiscuous_rx_cb(snifferCallback);
    wifi_set_channel(currentChannel);
    wifi_promiscuous_enable(1);

    broadcastTelemetry("$GHOSTH,BOOT,VERSION=2.0,HARDWARE=NODEMCU_ESP8266,BAUD=115200*7F");
    broadcastTelemetry("GHOSTH,INFO,00:00:00:00:00:00,0,1,GhostH Companion Sniffer Online");
}

void loop() {
    unsigned long now = millis();

    // 1. Channel Hopping (Channels 1 to 13)
    if (now - lastChannelHop >= CHANNEL_HOP_INTERVAL_MS) {
        lastChannelHop = now;
        currentChannel++;
        if (currentChannel > MAX_CHANNEL) {
            currentChannel = 1;
        }
        wifi_set_channel(currentChannel);
    }

    // 2. Optical ALPR Strobe Evaluation Window (400 ms)
    if (now - lastIrWindowCheck >= IR_WINDOW_MS) {
        noInterrupts();
        unsigned int count = pulseCount;
        unsigned long intervalSum = pulseIntervalSum;
        pulseCount = 0;
        pulseIntervalSum = 0;
        interrupts();

        float avgInterval = (count > 0) ? ((float)intervalSum / count) : 0.0;
        float pulseFreq = (avgInterval > 0) ? (1000000.0 / avgInterval) : 0.0;
        int rawIr = analogRead(PIN_IR_ANALOG);

        // Flock ALPR Strobe Trigger Criteria:
        // >= 10 pulses in 400ms window within 25 Hz - 65 Hz camera sync frequency
        if (count >= 10 && pulseFreq >= 25.0 && pulseFreq <= 65.0) {
            digitalWrite(PIN_ALERT_BUZZER, HIGH);
            digitalWrite(PIN_ONBOARD_LED, LOW); // Flash LED

            // Format ALPR finding sentence for GhostH App
            String alprLine = "GHOSTH,ALPR,FL:OC:CA:LP:01:00," + String(rawIr) + "," +
                              String(currentChannel) + ",FLOCK_IR_STROBE_" + String((int)pulseFreq) + "HZ";
            broadcastTelemetry(alprLine);

            String nmeaSentence = "$ALPR_STRIKE,TYPE=PULSED_IR,FREQ=" + String(pulseFreq, 1) +
                                  ",PULSES=" + String(count) + ",RAW=" + String(rawIr) + "*FF";
            broadcastTelemetry(nmeaSentence);
        } else {
            digitalWrite(PIN_ALERT_BUZZER, LOW);
            digitalWrite(PIN_ONBOARD_LED, HIGH);
        }

        lastIrWindowCheck = now;
    }

    // 3. Handle incoming TCP client (Wi-Fi Companion connection)
    if (!activeClient || !activeClient.connected()) {
        activeClient = tcpServer.available();
        if (activeClient) {
            activeClient.println("GHOSTH,STATUS,00:00:00:00:00:00,0,1,Client Connected to GhostH Sniffer");
        }
    }

    yield();
}

