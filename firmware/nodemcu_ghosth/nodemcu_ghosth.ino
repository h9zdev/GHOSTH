/*
 * =====================================================================
 * GHOSTH // WARDRIVER HX OS - NODEMCU COMPANION FIRMWARE v2.1
 * Target: ESP8266 (NodeMCU v2 / v3 / CP2102 / CH340)
 *
 * Features:
 * 1. Dedicated, Always-On SoftAP ("GhostH" / "ghosth1234")
 * 2. Web Configuration Portal (http://192.168.4.1) to change AP SSID/Password in EEPROM
 * 3. Continuous Asynchronous 802.11 RF Ingest (Channels 1 - 13, all SSIDs & BSSIDs)
 * 4. Flock ALPR Optical Infrared (850nm / 940nm) Pulse Strobe Discriminator (25-65Hz)
 * 5. Dual Telemetry Stream:
 *    - USB Serial @ 115200 baud (Direct USB-OTG connection to phone)
 *    - TCP Socket Server on Port 8888 (Wi-Fi Companion stream to app)
 * =====================================================================
 */

#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <EEPROM.h>

// -------------------------------------------------------------
// Hardware Pin Definitions
// -------------------------------------------------------------
#define PIN_IR_DIGITAL   4   // D2 (GPIO 4): LM393 IR Comparator Output (Interrupt)
#define PIN_IR_ANALOG    A0  // A0: Raw IR Irradiance Level
#define PIN_ALERT_BUZZER 5   // D1 (GPIO 5): Local Alert Buzzer / LED
#define PIN_ONBOARD_LED  2   // D4 (GPIO 2): Built-in Blue LED (Active LOW)

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

unsigned long lastTelemetryHeartbeat = 0;
unsigned long lastScanStart = 0;

void loadConfig() {
    EEPROM.begin(sizeof(Config));
    EEPROM.get(0, config);
    if (config.magic != EEPROM_MAGIC || strlen(config.apSsid) == 0 || strlen(config.apPass) < 8) {
        config.magic = EEPROM_MAGIC;
        strncpy(config.apSsid, "GhostH", sizeof(config.apSsid) - 1);
        config.apSsid[sizeof(config.apSsid) - 1] = '\0';
        strncpy(config.apPass, "ghosth1234", sizeof(config.apPass) - 1);
        config.apPass[sizeof(config.apPass) - 1] = '\0';
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

// -------------------------------------------------------------
// Web Configuration Portal Handlers
// -------------------------------------------------------------
void handleWebRoot() {
    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>";
    html += "<title>GhostH NodeMCU Portal</title>";
    html += "<style>body{background:#05070e;color:#00fff2;font-family:monospace,sans-serif;padding:20px;text-align:center;}";
    html += ".card{background:#0d111a;border:1px solid #00fff2;border-radius:8px;padding:20px;max-width:380px;margin:auto;}";
    html += "input{width:90%;padding:10px;margin:10px 0;background:#05070e;border:1px solid #00fff2;color:#fff;border-radius:4px;font-size:14px;}";
    html += "button{background:#00fff2;color:#05070e;border:none;padding:12px 24px;font-weight:bold;border-radius:4px;cursor:pointer;font-size:14px;}";
    html += ".badge{display:inline-block;padding:4px 8px;background:#00fff2;color:#05070e;font-weight:bold;border-radius:3px;margin-bottom:12px;}";
    html += "</style></head><body><div class='card'>";
    html += "<h2>// GHOSTH COMPANION</h2>";
    html += "<div class='badge'>STATUS: ONLINE</div>";
    html += "<p>RF Sniffer &amp; Flock ALPR Detector</p>";
    html += "<form method='POST' action='/save'>";
    html += "<label>Access Point SSID:</label><br>";
    html += "<input type='text' name='ssid' value='" + String(config.apSsid) + "' required><br>";
    html += "<label>Access Point Password:</label><br>";
    html += "<input type='text' name='pass' value='" + String(config.apPass) + "' required minlength='8'><br><br>";
    html += "<button type='submit'>SAVE &amp; REBOOT</button>";
    html += "</form>";
    html += "<hr style='border-color:#1a2333;margin:20px 0;'>";
    html += "<p style='font-size:12px;color:#6b829d;'>Telemetry Stream: 192.168.4.1:8888<br>USB Serial: 115200 baud</p>";
    html += "</div></body></html>";
    webServer.send(200, "text/html", html);
}

void handleWebSave() {
    if (webServer.hasArg("ssid") && webServer.hasArg("pass")) {
        String newSsid = webServer.arg("ssid");
        String newPass = webServer.arg("pass");
        if (newSsid.length() > 0 && newPass.length() >= 8) {
            strncpy(config.apSsid, newSsid.c_str(), sizeof(config.apSsid) - 1);
            config.apSsid[sizeof(config.apSsid) - 1] = '\0';
            strncpy(config.apPass, newPass.c_str(), sizeof(config.apPass) - 1);
            config.apPass[sizeof(config.apPass) - 1] = '\0';
            saveConfig();
            webServer.send(200, "text/html", "<html><body style='background:#05070e;color:#00fff2;text-align:center;'><h2>Settings Saved! Rebooting NodeMCU...</h2></body></html>");
            delay(1000);
            ESP.restart();
            return;
        }
    }
    webServer.send(400, "text/plain", "Invalid Parameters. Password must be >= 8 chars.");
}

void setup() {
    Serial.begin(115200);
    delay(300);

    loadConfig();

    // Initialize GPIOs
    pinMode(PIN_IR_DIGITAL, INPUT_PULLUP);
    pinMode(PIN_ALERT_BUZZER, OUTPUT);
    pinMode(PIN_ONBOARD_LED, OUTPUT);
    digitalWrite(PIN_ALERT_BUZZER, LOW);
    digitalWrite(PIN_ONBOARD_LED, HIGH); // LED off

    // Attach hardware interrupt for Flock ALPR IR detection
    attachInterrupt(digitalPinToInterrupt(PIN_IR_DIGITAL), onIrPulse, FALLING);

    // Explicitly configure and start SoftAP (Channel 1, visible)
    WiFi.disconnect(true);
    delay(100);
    WiFi.mode(WIFI_AP_STA);
    IPAddress apIP(192, 168, 4, 1);
    IPAddress netMsk(255, 255, 255, 0);
    WiFi.softAPConfig(apIP, apIP, netMsk);
    
    // Start SoftAP: ssid, passphrase, channel, hidden (0 = visible), max_connection (4)
    bool apSuccess = WiFi.softAP(config.apSsid, config.apPass, 1, 0, 4);

    // Start Web Server & TCP Telemetry Server
    webServer.on("/", HTTP_GET, handleWebRoot);
    webServer.on("/save", HTTP_POST, handleWebSave);
    webServer.begin();

    tcpServer.begin();
    tcpServer.setNoDelay(true);

    // Trigger initial asynchronous Wi-Fi scan
    WiFi.scanNetworks(true, true);
    lastScanStart = millis();

    Serial.println();
    Serial.println("==================================================");
    Serial.println("// GHOSTH NODEMCU COMPANION v2.1 ONLINE");
    Serial.println("// SoftAP SSID: " + String(config.apSsid));
    Serial.println("// SoftAP Pass: " + String(config.apPass));
    Serial.println("// SoftAP IP:   " + WiFi.softAPIP().toString());
    Serial.println("// Web Config:  http://" + WiFi.softAPIP().toString());
    Serial.println("// TCP Stream:  port 8888");
    Serial.println("// AP Started:  " + String(apSuccess ? "SUCCESS" : "FAILED"));
    Serial.println("==================================================");

    broadcastTelemetry("$GHOSTH,BOOT,VERSION=2.1,SSID=" + String(config.apSsid) + ",BAUD=115200*7F");
    broadcastTelemetry("GHOSTH,INFO,00:00:00:00:00:00,0,1,GhostH Companion Online (AP: " + String(config.apSsid) + ")");
}

void loop() {
    unsigned long now = millis();

    // 1. Handle Web Configuration Portal requests
    webServer.handleClient();

    // 2. Handle incoming TCP client (Phone companion connection)
    if (!activeClient || !activeClient.connected()) {
        WiFiClient newClient = tcpServer.available();
        if (newClient) {
            activeClient = newClient;
            activeClient.println("GHOSTH,STATUS,00:00:00:00:00:00,0,1,Connected to GhostH AP Sniffer Stream");
        }
    }

    // 3. Process Asynchronous Wi-Fi Network Scan Results
    int scanResult = WiFi.scanComplete();
    if (scanResult >= 0) {
        for (int i = 0; i < scanResult; i++) {
            String bssid = WiFi.BSSIDstr(i);
            int rssi = WiFi.RSSI(i);
            int channel = WiFi.channel(i);
            String ssid = WiFi.SSID(i);
            if (ssid.length() == 0) ssid = "<Hidden>";

            // Format: GHOSTH,BEACON,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
            String packetLine = "GHOSTH,BEACON," + bssid + "," + String(rssi) + "," +
                                String(channel) + "," + ssid;
            broadcastTelemetry(packetLine);
            yield();
        }
        WiFi.scanDelete();
        WiFi.scanNetworks(true, true);
        lastScanStart = now;
    } else if (scanResult == -2 && (now - lastScanStart > 5000)) {
        // Scan was cancelled or stalled, restart
        WiFi.scanNetworks(true, true);
        lastScanStart = now;
    }

    // 4. Optical ALPR Strobe Evaluation Window (400 ms)
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
            digitalWrite(PIN_ONBOARD_LED, LOW); // LED ON

            String alprLine = "GHOSTH,ALPR,FL:OC:CA:LP:01:00," + String(rawIr) + ",1,FLOCK_IR_STROBE_" + String((int)pulseFreq) + "HZ";
            broadcastTelemetry(alprLine);

            String nmeaSentence = "$ALPR_STRIKE,TYPE=PULSED_IR,FREQ=" + String(pulseFreq, 1) +
                                  ",PULSES=" + String(count) + ",RAW=" + String(rawIr) + "*FF";
            broadcastTelemetry(nmeaSentence);
        } else {
            digitalWrite(PIN_ALERT_BUZZER, LOW);
            digitalWrite(PIN_ONBOARD_LED, HIGH); // LED OFF
        }

        lastIrWindowCheck = now;
    }

    // 5. Heartbeat every 10 seconds
    if (now - lastTelemetryHeartbeat >= 10000) {
        lastTelemetryHeartbeat = now;
        broadcastTelemetry("GHOSTH,HEARTBEAT,00:00:00:00:00:00,0,1,GhostH_Companion_Alive");
    }

    yield();
}
