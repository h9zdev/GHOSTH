/*
 * =====================================================================
 * GHOSTH // WARDRIVER HX OS - NODEMCU COMPANION FIRMWARE
 * Target: ESP8266 (NodeMCU v2 / v3)
 *
 * Capabilities:
 * 1. Promiscuous 802.11 Wi-Fi Beacon & Probe Request Sniffer (Ch 1 - 13)
 * 2. Flock ALPR Optical Infrared (850nm / 940nm) Strobe Pulse Detector
 * 3. Dual Telemetry Transport:
 *    - USB Serial @ 115200 baud (Direct USB-OTG into Android App)
 *    - Standalone TCP Socket Server (Port 8888 over AP "GhostH")
 * 4. Web Configuration Portal (http://192.168.4.1) to change AP SSID/Password
 *
 * Compatible with GhostH ExternalHardwareManager protocol:
 * GHOSTH,<TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
 * =====================================================================
 */

#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <EEPROM.h>

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
// Configurable AP Settings (Stored in EEPROM)
// -------------------------------------------------------------
#define EEPROM_MAGIC 0x4748 // "GH"
struct Config {
    uint16_t magic;
    char apSsid[32];
    char apPass[32];
} config;

// -------------------------------------------------------------
// Servers: TCP Telemetry (Port 8888) & Web Config (Port 80)
// -------------------------------------------------------------
WiFiServer tcpServer(8888);
WiFiClient activeClient;
ESP8266WebServer webServer(80);

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

void loadConfig() {
    EEPROM.begin(sizeof(Config));
    EEPROM.get(0, config);
    if (config.magic != EEPROM_MAGIC) {
        config.magic = EEPROM_MAGIC;
        strncpy(config.apSsid, "GhostH", sizeof(config.apSsid));
        strncpy(config.apPass, "ghosth1234", sizeof(config.apPass));
        EEPROM.put(0, config);
        EEPROM.commit();
    }
}

void saveConfig() {
    config.magic = EEPROM_MAGIC;
    EEPROM.put(0, config);
    EEPROM.commit();
}

// Hardware Interrupt for IR Pulse Detection (Leading Falling Edge)
void ICACHE_RAM_ATTR onIrPulse() {
    unsigned long now = micros();
    unsigned long interval = now - lastPulseMicros;
    lastPulseMicros = now;

    // Filter noise: ALPR pulses arrive between 14,000 and 42,000 µs (25 Hz - 65 Hz)
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

        // Format: GHOSTH,<TYPE>,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
        String packetLine = "GHOSTH," + typeStr + "," + String(bssidStr) + "," +
                            String(rssi) + "," + String(currentChannel) + "," + ssidStr;

        broadcastTelemetry(packetLine);
    }
}

// -------------------------------------------------------------
// Web Configuration Portal Handlers
// -------------------------------------------------------------
void handleWebRoot() {
    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>";
    html += "<title>GhostH NodeMCU Portal</title>";
    html += "<style>body{background:#05070e;color:#00fff2;font-family:sans-serif;padding:20px;text-align:center;}";
    html += ".card{background:#0d111a;border:1px solid #00fff2;border-radius:8px;padding:20px;max-width:360px;margin:auto;}";
    html += "input{width:90%;padding:10px;margin:10px 0;background:#05070e;border:1px solid #00fff2;color:#fff;border-radius:4px;}";
    html += "button{background:#00fff2;color:#05070e;border:none;padding:12px 20px;font-weight:bold;border-radius:4px;cursor:pointer;}";
    html += "</style></head><body><div class='card'>";
    html += "<h2>// GHOSTH NODEMCU</h2><p>Companion RF & ALPR Sniffer</p>";
    html += "<form method='POST' action='/save'>";
    html += "<label>Access Point SSID:</label><br>";
    html += "<input type='text' name='ssid' value='" + String(config.apSsid) + "' required><br>";
    html += "<label>Access Point Password:</label><br>";
    html += "<input type='text' name='pass' value='" + String(config.apPass) + "' required minlength='8'><br><br>";
    html += "<button type='submit'>SAVE & REBOOT</button>";
    html += "</form></div></body></html>";
    webServer.send(200, "text/html", html);
}

void handleWebSave() {
    if (webServer.hasArg("ssid") && webServer.hasArg("pass")) {
        String newSsid = webServer.arg("ssid");
        String newPass = webServer.arg("pass");
        if (newSsid.length() > 0 && newPass.length() >= 8) {
            strncpy(config.apSsid, newSsid.c_str(), sizeof(config.apSsid));
            strncpy(config.apPass, newPass.c_str(), sizeof(config.apPass));
            saveConfig();
            webServer.send(200, "text/html", "<html><body style='background:#05070e;color:#00fff2;text-align:center;'><h2>Saved! Rebooting...</h2></body></html>");
            delay(1000);
            ESP.restart();
            return;
        }
    }
    webServer.send(400, "text/plain", "Invalid Parameters");
}

void setup() {
    Serial.begin(115200);
    delay(200);

    loadConfig();

    // Initialize GPIOs
    pinMode(PIN_IR_DIGITAL, INPUT_PULLUP);
    pinMode(PIN_ALERT_BUZZER, OUTPUT);
    pinMode(PIN_ONBOARD_LED, OUTPUT);
    digitalWrite(PIN_ALERT_BUZZER, LOW);
    digitalWrite(PIN_ONBOARD_LED, HIGH);

    // Attach hardware interrupt for Flock ALPR IR detection
    attachInterrupt(digitalPinToInterrupt(PIN_IR_DIGITAL), onIrPulse, FALLING);

    // Start Wi-Fi Access Point with configured name (Default: "GhostH" / "ghosth1234")
    WiFi.mode(WIFI_AP_STA);
    WiFi.softAP(config.apSsid, config.apPass);

    // Start Web Server & TCP Telemetry Server
    webServer.on("/", HTTP_GET, handleWebRoot);
    webServer.on("/save", HTTP_POST, handleWebSave);
    webServer.begin();
    tcpServer.begin();

    // Enable 802.11 promiscuous packet sniffing
    wifi_set_opmode(STATION_MODE);
    wifi_promiscuous_enable(0);
    WiFi.disconnect();
    wifi_set_promiscuous_rx_cb(snifferCallback);
    wifi_set_channel(currentChannel);
    wifi_promiscuous_enable(1);

    broadcastTelemetry("$GHOSTH,BOOT,VERSION=2.0,SSID=" + String(config.apSsid) + ",BAUD=115200*7F");
    broadcastTelemetry("GHOSTH,INFO,00:00:00:00:00:00,0,1,GhostH Companion Online (AP: " + String(config.apSsid) + ")");
}

void loop() {
    unsigned long now = millis();

    // Handle Web Config Portal requests
    webServer.handleClient();

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

        if (count >= 10 && pulseFreq >= 25.0 && pulseFreq <= 65.0) {
            digitalWrite(PIN_ALERT_BUZZER, HIGH);
            digitalWrite(PIN_ONBOARD_LED, LOW);

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
