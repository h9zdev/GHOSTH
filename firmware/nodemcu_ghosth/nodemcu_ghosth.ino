/*
 * =====================================================================
 * GHOSTH // WARDRIVER HX OS - NODEMCU COMPANION FIRMWARE v2.2
 * Target: ESP8266 (NodeMCU v2 / v3 / CP2102 / CH340)
 *
 * Features:
 * 1. Dedicated Always-On SoftAP ("GhostH" / "ghosth1234")
 * 2. HayOS-Themed Tactical Web Interface (http://192.168.4.1)
 *    - Default system status displayed first
 *    - Real-time wireless information table (SSID, BSSID, RSSI, Channel)
 *    - Live AJAX auto-refresh via /api/scan endpoint
 *    - Collapsible AP configuration form (persisted in EEPROM)
 *    - Developer profile: https://haybnz.web.app/ & GitHub Wiki links
 * 3. Continuous Asynchronous Wi-Fi RF Ingestion (Channels 1 - 13)
 * 4. Flock ALPR Optical Infrared (850nm / 940nm) Pulse Strobe Discriminator (25-65Hz)
 * 5. Dual Telemetry Stream:
 *    - USB Serial @ 115200 baud
 *    - TCP Socket Server on Port 8888
 * =====================================================================
 */

#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <EEPROM.h>

// Hardware Pin Definitions
#define PIN_IR_DIGITAL   4   // D2 (GPIO 4): LM393 IR Comparator Output (Interrupt)
#define PIN_IR_ANALOG    A0  // A0: Raw IR Irradiance Level
#define PIN_ALERT_BUZZER 5   // D1 (GPIO 5): Local Alert Buzzer / LED
#define PIN_ONBOARD_LED  2   // D4 (GPIO 2): Built-in Blue LED (Active LOW)

// ALPR Optical IR Strobe Timing
volatile unsigned long lastPulseMicros = 0;
volatile unsigned long pulseIntervalSum = 0;
volatile unsigned int pulseCount = 0;

unsigned long lastIrWindowCheck = 0;
const unsigned long IR_WINDOW_MS = 400;
unsigned int alprStrikeCount = 0;
float lastAlprFreq = 0.0;

// Configurable AP Settings (Stored in EEPROM)
#define EEPROM_MAGIC 0x4748 // "GH"
struct Config {
    uint16_t magic;
    char apSsid[32];
    char apPass[32];
} config;

// In-Memory Scanned AP Buffer
struct ScannedAp {
    char bssid[18];
    char ssid[33];
    int rssi;
    int channel;
};
#define MAX_SCANNED 30
ScannedAp scannedAps[MAX_SCANNED];
int scannedCount = 0;

// Servers
WiFiServer tcpServer(8888);
WiFiClient activeClient;
ESP8266WebServer webServer(80);

unsigned long lastTelemetryHeartbeat = 0;
unsigned long lastScanStart = 0;
unsigned long lastScanCompleted = 0;
unsigned long totalPacketsIngested = 0;

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

// Hardware Interrupt for IR Pulse Detection
void ICACHE_RAM_ATTR onIrPulse() {
    unsigned long now = micros();
    unsigned long interval = now - lastPulseMicros;
    lastPulseMicros = now;

    if (interval >= 14000 && interval <= 42000) {
        pulseIntervalSum += interval;
        pulseCount++;
    }
}

void broadcastTelemetry(const String &line) {
    Serial.println(line);
    totalPacketsIngested++;
    if (activeClient && activeClient.connected()) {
        activeClient.println(line);
    }
}

// REST API endpoint returning scanned networks in JSON
void handleApiScan() {
    String json = "{\"scanned\":[";
    for (int i = 0; i < scannedCount; i++) {
        if (i > 0) json += ",";
        json += "{\"bssid\":\"" + String(scannedAps[i].bssid) + "\",";
        String s = String(scannedAps[i].ssid);
        s.replace("\"", "\\\"");
        json += "\"ssid\":\"" + s + "\",";
        json += "\"rssi\":" + String(scannedAps[i].rssi) + ",";
        json += "\"channel\":" + String(scannedAps[i].channel) + "}";
    }
    json += "],\"totalPackets\":" + String(totalPacketsIngested) + ",";
    json += "\"alprStrikes\":" + String(alprStrikeCount) + ",";
    json += "\"lastAlprFreq\":" + String(lastAlprFreq, 1) + ",";
    json += "\"uptime\":" + String(millis() / 1000) + "}";
    webServer.send(200, "application/json", json);
}

// Main Web Portal styled with HayOS tactical theme & loader
void handleWebRoot() {
    String html = F("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
"<title>GhostH // Tactical NodeMCU</title>"
"<style>"
":root{--primary:#00FFD1;--bg:#050505;--card:#0A0A0A;--panel:#080808;--border:rgba(0,255,209,0.25);--text:#00FFD1;--text-dim:#3A6B6B;--accent-red:#CC3333;--accent-amber:#FFBF00;}"
"*{box-sizing:border-box;margin:0;padding:0;outline:none;}"
"body{background-color:var(--bg);color:var(--text);font-family:'Courier New',monospace;padding:12px;letter-spacing:0.5px;}"
"/* HayOS Loader */"
"#loader-wrap{position:fixed;inset:0;background:#030303;display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:9999;transition:opacity 0.6s ease,visibility 0.6s;}"
".spin-box{width:50px;height:50px;border:2px solid rgba(0,255,209,0.2);border-top:2px solid var(--primary);animation:spin 0.8s linear infinite;margin-bottom:12px;}"
"@keyframes spin{0%{transform:rotate(0deg);}100%{transform:rotate(360deg);}}"
".container{max-width:850px;margin:auto;}"
".header{border-bottom:1px solid var(--border);padding-bottom:10px;margin-bottom:14px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;}"
".badge{background:var(--primary);color:#050505;font-weight:bold;font-size:11px;padding:3px 8px;border-radius:2px;}"
".grid-stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px;margin-bottom:14px;}"
".stat-card{background:var(--card);border:1px solid var(--border);padding:10px;}"
".stat-lbl{font-size:10px;color:var(--text-dim);text-transform:uppercase;}"
".stat-val{font-size:18px;font-weight:bold;color:var(--primary);margin-top:2px;}"
".panel{background:var(--panel);border:1px solid var(--border);padding:14px;margin-bottom:14px;}"
".panel-title{font-size:13px;font-weight:bold;color:var(--primary);margin-bottom:10px;display:flex;justify-content:space-between;align-items:center;}"
"table{width:100%;border-collapse:collapse;font-size:11px;text-align:left;}"
"th{border-bottom:1px solid var(--border);padding:6px;color:var(--text-dim);}"
"td{border-bottom:1px solid rgba(0,255,209,0.1);padding:6px;word-break:break-all;}"
"input{width:100%;padding:8px;margin:6px 0 12px;background:#050505;border:1px solid var(--border);color:#fff;font-family:monospace;font-size:13px;}"
"button{background:var(--primary);color:#050505;border:none;padding:10px 16px;font-weight:bold;cursor:pointer;font-family:monospace;font-size:12px;}"
"button:hover{background:#00d9b2;}"
".btn-toggle{background:none;border:1px solid var(--border);color:var(--primary);padding:4px 8px;font-size:10px;cursor:pointer;}"
"footer{border-top:1px solid var(--border);padding-top:12px;margin-top:16px;font-size:11px;color:var(--text-dim);display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;}"
"a{color:var(--primary);text-decoration:none;}"
"a:hover{text-decoration:underline;}"
"</style></head><body>"
"<div id='loader-wrap'><div class='spin-box'></div><div style='color:var(--primary);font-size:12px;'>INITIALIZING GHOSTH NODE...</div></div>"
"<div class='container'>"
"<div class='header'><div><h2 style='font-size:18px;'>// GHOSTH TACTICAL NODE</h2><span style='font-size:11px;color:var(--text-dim);'>ESP8266 RF &amp; OPTICAL SENSOR SUITE</span></div><div class='badge'>STATUS: ONLINE</div></div>"
"<div class='grid-stats'>"
"<div class='stat-card'><div class='stat-lbl'>Access Point</div><div class='stat-val'>");
    html += String(config.apSsid);
    html += F("</div></div><div class='stat-card'><div class='stat-lbl'>Companion IP</div><div class='stat-val'>");
    html += WiFi.softAPIP().toString();
    html += F("</div></div><div class='stat-card'><div class='stat-lbl'>RF Networks Cached</div><div class='stat-val' id='stat-count'>");
    html += String(scannedCount);
    html += F("</div></div><div class='stat-card'><div class='stat-lbl'>ALPR IR Strikes</div><div class='stat-val' style='color:var(--accent-red);' id='stat-alpr'>");
    html += String(alprStrikeCount);
    html += F("</div></div></div>"
"<div class='panel'><div class='panel-title'><span>LIVE WIRELESS DISCOVERY FEED</span><span id='live-indicator' style='color:#00ff66;font-size:10px;'>● SCANNING</span></div>"
"<div style='max-height:280px;overflow-y:auto;'>"
"<table><thead><tr><th>SSID</th><th>BSSID</th><th>CH</th><th>RSSI</th></tr></thead><tbody id='wifi-table'>");

    for (int i = 0; i < scannedCount; i++) {
        html += "<tr><td style='color:#fff;font-weight:bold;'>" + String(scannedAps[i].ssid) + "</td>";
        html += "<td style='color:var(--text-dim);'>" + String(scannedAps[i].bssid) + "</td>";
        html += "<td>" + String(scannedAps[i].channel) + "</td>";
        html += "<td style='color:var(--primary);'>" + String(scannedAps[i].rssi) + " dBm</td></tr>";
    }
    if (scannedCount == 0) {
        html += "<tr><td colspan='4' style='text-align:center;color:var(--text-dim);padding:14px;'>Awaiting background scan results...</td></tr>";
    }

    html += F("</tbody></table></div></div>"
"<div class='panel'><div class='panel-title'><span>ACCESS POINT SETTINGS</span><button type='button' class='btn-toggle' onclick='toggleConfig()'>[EXPAND / HIDE]</button></div>"
"<div id='config-section' style='display:none;'><p style='font-size:11px;color:var(--text-dim);margin-bottom:10px;'>Configure custom Wi-Fi Access Point credentials (persisted in EEPROM across power cycles):</p>"
"<form method='POST' action='/save'>"
"<label style='font-size:11px;'>SSID Name:</label><input type='text' name='ssid' value='");
    html += String(config.apSsid);
    html += F("' required minlength='3' maxlength='31'>"
"<label style='font-size:11px;'>WPA2 Password (min 8 characters):</label><input type='text' name='pass' value='");
    html += String(config.apPass);
    html += F("' required minlength='8' maxlength='31'>"
"<button type='submit'>SAVE &amp; REBOOT NODE</button></form></div></div>"
"<footer>"
"<div>Developer Profile: <a href='https://haybnz.web.app/' target='_blank'>haybnz.web.app</a></div>"
"<div>Wiki Guide: <a href='https://github.com/h9zdev/GHOSTH/wiki' target='_blank'>GitHub Wiki</a></div>"
"<div>Telemetry: Port 8888 | Serial: 115200</div>"
"</footer></div>"
"<script>"
"window.addEventListener('load',function(){setTimeout(function(){var el=document.getElementById('loader-wrap');if(el){el.style.opacity='0';setTimeout(function(){el.style.display='none';},600);}},400);});"
"function toggleConfig(){var s=document.getElementById('config-section');s.style.display=(s.style.display==='none'?'block':'none');}"
"setInterval(function(){fetch('/api/scan').then(function(r){return r.json();}).then(function(d){"
"document.getElementById('stat-count').innerText=d.scanned.length;"
"document.getElementById('stat-alpr').innerText=d.alprStrikes;"
"var tb=document.getElementById('wifi-table');if(!tb)return;var h='';"
"d.scanned.forEach(function(ap){h+='<tr><td style=\"color:#fff;font-weight:bold;\">'+ap.ssid+'</td><td style=\"color:var(--text-dim);\">'+ap.bssid+'</td><td>'+ap.channel+'</td><td style=\"color:var(--primary);\">'+ap.rssi+' dBm</td></tr>';});"
"if(d.scanned.length===0){h='<tr><td colspan=\"4\" style=\"text-align:center;color:var(--text-dim);padding:14px;\">Scanning in progress...</td></tr>';}"
"tb.innerHTML=h;}).catch(function(e){});},3000);"
"</script></body></html>");

    webServer.send(200, "text/html", html);
}

void handleWebSave() {
    if (webServer.hasArg("ssid") && webServer.hasArg("pass")) {
        String newSsid = webServer.arg("ssid");
        String newPass = webServer.arg("pass");
        if (newSsid.length() >= 3 && newPass.length() >= 8) {
            strncpy(config.apSsid, newSsid.c_str(), sizeof(config.apSsid) - 1);
            config.apSsid[sizeof(config.apSsid) - 1] = '\0';
            strncpy(config.apPass, newPass.c_str(), sizeof(config.apPass) - 1);
            config.apPass[sizeof(config.apPass) - 1] = '\0';
            saveConfig();
            webServer.send(200, "text/html", "<html><body style='background:#050505;color:#00FFD1;font-family:monospace;padding:30px;text-align:center;'><h2>[OK] Credentials Saved!</h2><p>Rebooting NodeMCU...</p></body></html>");
            delay(1000);
            ESP.restart();
            return;
        }
    }
    webServer.send(400, "text/plain", "Invalid Parameters. SSID min 3 chars, password min 8 chars.");
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
    WiFi.disconnect();
    delay(100);
    WiFi.mode(WIFI_AP_STA);
    WiFi.setSleepMode(WIFI_NONE_SLEEP);
    WiFi.setOutputPower(20.5);
    IPAddress apIP(192, 168, 4, 1);
    IPAddress netMsk(255, 255, 255, 0);
    WiFi.softAPConfig(apIP, apIP, netMsk);
    
    // Start SoftAP: ssid, passphrase, channel, hidden (0 = visible), max_connection (4)
    bool apSuccess = WiFi.softAP(config.apSsid, config.apPass, 1, 0, 4);

    // Start Web Server & Endpoints
    webServer.on("/", HTTP_GET, handleWebRoot);
    webServer.on("/save", HTTP_POST, handleWebSave);
    webServer.on("/api/scan", HTTP_GET, handleApiScan);
    webServer.on("/generate_204", []() { webServer.send(204, "text/plain", ""); });
    webServer.on("/gen_204", []() { webServer.send(204, "text/plain", ""); });
    webServer.on("/hotspot-detect.html", handleWebRoot);
    webServer.onNotFound(handleWebRoot);
    webServer.begin();

    tcpServer.begin();
    tcpServer.setNoDelay(true);

    // Trigger initial asynchronous Wi-Fi scan
    WiFi.scanNetworks(true, true);
    lastScanStart = millis();

    Serial.println();
    Serial.println("==================================================");
    Serial.println("// GHOSTH NODEMCU COMPANION v2.2 ONLINE");
    Serial.println("// SoftAP SSID: " + String(config.apSsid));
    Serial.println("// SoftAP Pass: " + String(config.apPass));
    Serial.println("// SoftAP IP:   " + WiFi.softAPIP().toString());
    Serial.println("// Web Portal:  http://" + WiFi.softAPIP().toString());
    Serial.println("// TCP Stream:  port 8888");
    Serial.println("// Developer:   https://haybnz.web.app/");
    Serial.println("==================================================");

    broadcastTelemetry("$GHOSTH,BOOT,VERSION=2.2,SSID=" + String(config.apSsid) + ",BAUD=115200*7F");
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
        scannedCount = (scanResult > MAX_SCANNED) ? MAX_SCANNED : scanResult;
        for (int i = 0; i < scanResult; i++) {
            String bssid = WiFi.BSSIDstr(i);
            int rssi = WiFi.RSSI(i);
            int channel = WiFi.channel(i);
            String ssid = WiFi.SSID(i);
            if (ssid.length() == 0) ssid = "<Hidden>";

            if (i < MAX_SCANNED) {
                strncpy(scannedAps[i].bssid, bssid.c_str(), sizeof(scannedAps[i].bssid) - 1);
                scannedAps[i].bssid[sizeof(scannedAps[i].bssid) - 1] = '\0';
                strncpy(scannedAps[i].ssid, ssid.c_str(), sizeof(scannedAps[i].ssid) - 1);
                scannedAps[i].ssid[sizeof(scannedAps[i].ssid) - 1] = '\0';
                scannedAps[i].rssi = rssi;
                scannedAps[i].channel = channel;
            }

            // Format: GHOSTH,BEACON,<BSSID>,<RSSI>,<CHANNEL>,<SSID>
            String packetLine = "GHOSTH,BEACON," + bssid + "," + String(rssi) + "," +
                                String(channel) + "," + ssid;
            broadcastTelemetry(packetLine);
            yield();
        }
        WiFi.scanDelete();
        lastScanCompleted = now;
    } else if (scanResult == -1 && (now - lastScanStart > 8000)) {
        // Recover from stalled scan
        WiFi.scanDelete();
        lastScanCompleted = now;
    }

    // Dwell on Channel 1 for 1.8 seconds between scans so SoftAP transmits beacons reliably
    // and phones / computers can discover GhostH and maintain rock-solid connection
    if (WiFi.scanComplete() != -1 && (now - lastScanCompleted >= 1800)) {
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
            alprStrikeCount++;
            lastAlprFreq = pulseFreq;
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
