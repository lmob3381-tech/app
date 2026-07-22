/*
 * ESP32 NetScan Console
 * -----------------------------------------------------------
 * Fitur:
 *  - WiFi Scanner: scan semua SSID sekitar (channel, RSSI, enkripsi, BSSID)
 *  - Host Scanner (mini-nmap): setelah konek ke jaringan (mode STA),
 *    lakukan ping sweep ke seluruh subnet /24 untuk menemukan host aktif,
 *    lalu port-scan ringan (port umum) ke tiap host yang hidup.
 *  - AP Deployer: jalankan ESP32 sebagai Access Point sendiri dengan
 *    SSID/password custom yang bisa diganti dari web panel.
 *  - Web Control Panel: dashboard HTML+JS yang di-serve langsung dari
 *    ESP32, bisa diakses dari HP/laptop yang konek ke WiFi ESP32 (mode AP)
 *    atau dari jaringan yang sama (mode STA).
 *
 * PERINGATAN ETIKA/HUKUM:
 *  Gunakan hanya pada jaringan milik sendiri atau yang sudah diberi izin.
 *  Melakukan scanning terhadap jaringan orang lain tanpa izin bisa
 *  melanggar hukum (mis. UU ITE di Indonesia).
 * -----------------------------------------------------------
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <DNSServer.h>
#include <Preferences.h>
#include <ESP32Ping.h>
#include <ArduinoJson.h>
#include <LittleFS.h>

// ----------------------------- Konfigurasi Default -----------------------------
#define DEFAULT_AP_SSID     "ESP32-NetScan"
#define DEFAULT_AP_PASS     "netscan123"
#define AP_CHANNEL          1
#define AP_MAX_CONN         4
#define HTTP_PORT           80
#define DNS_PORT            53
#define MAX_HOSTS           254
#define PING_TIMEOUT_MS     150
#define COMMON_PORTS_COUNT  10

WebServer server(HTTP_PORT);
DNSServer dnsServer;
Preferences prefs;

// Port umum yang akan dicek saat host scan (mirip nmap "top ports" versi mini)
const uint16_t commonPorts[COMMON_PORTS_COUNT] = {21, 22, 23, 25, 53, 80, 139, 443, 445, 8080};
const char* commonPortNames[COMMON_PORTS_COUNT] = {
    "FTP", "SSH", "Telnet", "SMTP", "DNS", "HTTP", "NetBIOS", "HTTPS", "SMB", "HTTP-Alt"
};

// ----------------------------- State Global -----------------------------
enum OpMode { MODE_AP, MODE_STA };
OpMode currentMode = MODE_AP;

bool wifiScanRunning = false;
bool hostScanRunning = false;
String wifiScanResultJson = "[]";
String hostScanResultJson = "[]";
String hostScanProgress = "idle";

String apSsid = DEFAULT_AP_SSID;
String apPass = DEFAULT_AP_PASS;
String staSsid = "";
String staPass = "";

unsigned long lastActivity = 0;

// ----------------------------- Util -----------------------------
String encTypeToStr(wifi_auth_mode_t enc) {
    switch (enc) {
        case WIFI_AUTH_OPEN: return "OPEN";
        case WIFI_AUTH_WEP: return "WEP";
        case WIFI_AUTH_WPA_PSK: return "WPA-PSK";
        case WIFI_AUTH_WPA2_PSK: return "WPA2-PSK";
        case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2-PSK";
        case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2-ENT";
        case WIFI_AUTH_WPA3_PSK: return "WPA3-PSK";
        case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2/WPA3-PSK";
        default: return "UNKNOWN";
    }
}

void loadConfig() {
    prefs.begin("netscan", true);
    apSsid = prefs.getString("ap_ssid", DEFAULT_AP_SSID);
    apPass = prefs.getString("ap_pass", DEFAULT_AP_PASS);
    staSsid = prefs.getString("sta_ssid", "");
    staPass = prefs.getString("sta_pass", "");
    prefs.end();
}

void saveApConfig(const String &ssid, const String &pass) {
    prefs.begin("netscan", false);
    prefs.putString("ap_ssid", ssid);
    prefs.putString("ap_pass", pass);
    prefs.end();
}

void saveStaConfig(const String &ssid, const String &pass) {
    prefs.begin("netscan", false);
    prefs.putString("sta_ssid", ssid);
    prefs.putString("sta_pass", pass);
    prefs.end();
}

// ----------------------------- WiFi Scan (SSID sekitar) -----------------------------
void runWifiScan() {
    wifiScanRunning = true;
    int n = WiFi.scanNetworks(false, true); // async=false, show_hidden=true

    DynamicJsonDocument doc(8192);
    JsonArray arr = doc.to<JsonArray>();

    for (int i = 0; i < n; i++) {
        JsonObject o = arr.createNestedObject();
        o["ssid"] = WiFi.SSID(i);
        o["bssid"] = WiFi.BSSIDstr(i);
        o["rssi"] = WiFi.RSSI(i);
        o["channel"] = WiFi.channel(i);
        o["enc"] = encTypeToStr(WiFi.encryptionType(i));
    }

    String out;
    serializeJson(doc, out);
    wifiScanResultJson = out;
    WiFi.scanDelete();
    wifiScanRunning = false;
}

// ----------------------------- Host Scan (mini nmap: ping sweep + port cek) -----------------------------
void runHostScan() {
    if (currentMode != MODE_STA || WiFi.status() != WL_CONNECTED) {
        hostScanResultJson = "[]";
        hostScanProgress = "error: not connected to a network (STA mode required)";
        return;
    }

    hostScanRunning = true;
    IPAddress localIp = WiFi.localIP();
    IPAddress subnetMask = WiFi.subnetMask();

    IPAddress base(localIp[0] & subnetMask[0],
                    localIp[1] & subnetMask[1],
                    localIp[2] & subnetMask[2],
                    0);

    DynamicJsonDocument doc(16384);
    JsonArray arr = doc.to<JsonArray>();

    for (int host = 1; host < 255; host++) {
        IPAddress target(base[0], base[1], base[2], host);
        if (target == localIp) {
            JsonObject o = arr.createNestedObject();
            o["ip"] = target.toString();
            o["alive"] = true;
            o["self"] = true;
            JsonArray ports = o.createNestedArray("open_ports");
            hostScanProgress = "scanning " + target.toString() + " (this device)";
            continue;
        }

        hostScanProgress = "pinging " + target.toString();
        bool alive = Ping.ping(target, 1);

        if (alive) {
            JsonObject o = arr.createNestedObject();
            o["ip"] = target.toString();
            o["alive"] = true;
            o["self"] = false;
            o["ping_ms"] = Ping.averageTime();

            JsonArray ports = o.createNestedArray("open_ports");
            for (int p = 0; p < COMMON_PORTS_COUNT; p++) {
                hostScanProgress = "port scan " + target.toString() + ":" + String(commonPorts[p]);
                WiFiClient client;
                client.setTimeout(200);
                if (client.connect(target, commonPorts[p], 200)) {
                    JsonObject po = ports.createNestedObject();
                    po["port"] = commonPorts[p];
                    po["service"] = commonPortNames[p];
                    client.stop();
                }
                yield();
            }
        }
        yield();
        delay(5);
    }

    String out;
    serializeJson(doc, out);
    hostScanResultJson = out;
    hostScanProgress = "done";
    hostScanRunning = false;
}

// ----------------------------- Mode Switching -----------------------------
void startApMode() {
    WiFi.disconnect(true);
    WiFi.mode(WIFI_AP);
    WiFi.softAP(apSsid.c_str(), apPass.c_str(), AP_CHANNEL, 0, AP_MAX_CONN);
    dnsServer.start(DNS_PORT, "*", WiFi.softAPIP());
    currentMode = MODE_AP;
    Serial.println("[AP] SSID: " + apSsid);
    Serial.println("[AP] IP: " + WiFi.softAPIP().toString());
}

bool startStaMode(const String &ssid, const String &pass, unsigned long timeoutMs = 15000) {
    WiFi.disconnect(true);
    dnsServer.stop();
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid.c_str(), pass.c_str());

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < timeoutMs) {
        delay(250);
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {
        currentMode = MODE_STA;
        Serial.println("[STA] Connected. IP: " + WiFi.localIP().toString());
        return true;
    } else {
        Serial.println("[STA] Failed to connect, reverting to AP mode.");
        startApMode();
        return false;
    }
}

// ----------------------------- Web Handlers -----------------------------
void handleRoot() {
    File f = LittleFS.open("/index.html", "r");
    if (!f) {
        server.send(500, "text/plain", "index.html not found in filesystem. Did you upload LittleFS image?");
        return;
    }
    server.streamFile(f, "text/html");
    f.close();
}

void handleStatus() {
    DynamicJsonDocument doc(1024);
    doc["mode"] = (currentMode == MODE_AP) ? "AP" : "STA";
    doc["ap_ssid"] = apSsid;
    doc["sta_ssid"] = staSsid;
    doc["sta_connected"] = (WiFi.status() == WL_CONNECTED && currentMode == MODE_STA);
    doc["ip"] = (currentMode == MODE_AP) ? WiFi.softAPIP().toString() : WiFi.localIP().toString();
    doc["clients_connected_ap"] = WiFi.softAPgetStationNum();
    doc["free_heap"] = ESP.getFreeHeap();
    doc["uptime_s"] = millis() / 1000;
    doc["wifi_scan_running"] = wifiScanRunning;
    doc["host_scan_running"] = hostScanRunning;
    doc["host_scan_progress"] = hostScanProgress;

    String out;
    serializeJson(doc, out);
    server.send(200, "application/json", out);
}

void handleWifiScanStart() {
    if (!wifiScanRunning) {
        runWifiScan(); // synchronous; fine for small networks, scan is fast
    }
    server.send(200, "application/json", "{\"status\":\"ok\"}");
}

void handleWifiScanResult() {
    server.send(200, "application/json", wifiScanResultJson);
}

void handleHostScanStart() {
    if (currentMode != MODE_STA) {
        server.send(400, "application/json", "{\"error\":\"Switch to STA mode first (connect to a WiFi network)\"}");
        return;
    }
    if (!hostScanRunning) {
        // run inline; for large subnets this can take a while (blocking).
        runHostScan();
    }
    server.send(200, "application/json", "{\"status\":\"ok\"}");
}

void handleHostScanResult() {
    DynamicJsonDocument doc(256);
    doc["progress"] = hostScanProgress;
    doc["running"] = hostScanRunning;
    String meta;
    serializeJson(doc, meta);

    // combine meta + results
    String out = "{\"meta\":" + meta + ",\"hosts\":" + hostScanResultJson + "}";
    server.send(200, "application/json", out);
}

void handleSetApConfig() {
    if (!server.hasArg("ssid")) {
        server.send(400, "application/json", "{\"error\":\"ssid required\"}");
        return;
    }
    apSsid = server.arg("ssid");
    apPass = server.arg("pass");
    if (apPass.length() > 0 && apPass.length() < 8) {
        server.send(400, "application/json", "{\"error\":\"password must be 0 or >=8 chars\"}");
        return;
    }
    saveApConfig(apSsid, apPass);
    server.send(200, "application/json", "{\"status\":\"saved, restarting AP\"}");
    delay(300);
    if (currentMode == MODE_AP) startApMode();
}

void handleConnectSta() {
    if (!server.hasArg("ssid")) {
        server.send(400, "application/json", "{\"error\":\"ssid required\"}");
        return;
    }
    staSsid = server.arg("ssid");
    staPass = server.arg("pass");
    saveStaConfig(staSsid, staPass);

    server.send(200, "application/json", "{\"status\":\"connecting\"}");
    delay(200);
    startStaMode(staSsid, staPass);
}

void handleSwitchAp() {
    server.send(200, "application/json", "{\"status\":\"switching to AP\"}");
    delay(200);
    startApMode();
}

void handleNotFound() {
    // Captive portal redirect when in AP mode
    if (currentMode == MODE_AP) {
        server.sendHeader("Location", "http://" + WiFi.softAPIP().toString(), true);
        server.send(302, "text/plain", "");
        return;
    }
    server.send(404, "text/plain", "Not found");
}

// ----------------------------- Setup / Loop -----------------------------
void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println("\n[BOOT] ESP32 NetScan Console");

    if (!LittleFS.begin(true)) {
        Serial.println("[FS] LittleFS mount failed");
    }

    loadConfig();
    startApMode(); // boot always in AP mode for safe access

    server.on("/", handleRoot);
    server.on("/api/status", handleStatus);
    server.on("/api/wifi-scan/start", handleWifiScanStart);
    server.on("/api/wifi-scan/result", handleWifiScanResult);
    server.on("/api/host-scan/start", handleHostScanStart);
    server.on("/api/host-scan/result", handleHostScanResult);
    server.on("/api/config/ap", HTTP_POST, handleSetApConfig);
    server.on("/api/connect", HTTP_POST, handleConnectSta);
    server.on("/api/switch-ap", HTTP_POST, handleSwitchAp);
    server.onNotFound(handleNotFound);

    server.begin();
    Serial.println("[HTTP] Server started on port " + String(HTTP_PORT));
}

void loop() {
    if (currentMode == MODE_AP) {
        dnsServer.processNextRequest();
    }
    server.handleClient();
}
