/*
  =====================================================================
   ESP32 WiFi ANALYZER & TOOLKIT
  =====================================================================
   Board target : ESP32 DevKit V1 (esp32dev / WROOM-32)
   Framework    : Arduino (via PlatformIO)

   FITUR:
   ------
   1. WiFi Scanner        - scan SSID sekitar (RSSI, channel, enkripsi,
                             BSSID, hidden SSID)
   2. Client Monitor       - lihat semua device yang konek ke SoftAP
                             dashboard (IP, MAC, vendor OUI, hostname,
                             RSSI, waktu konek)
   3. Multi-SSID Beacon    - broadcast hingga MAX_FAKE_SSID (15) SSID
                             palsu sekaligus (beacon frame spoofing,
                             MAC random per SSID) - untuk riset /
                             demo cara kerja beacon frame & SSID.
   4. Deauth Detector      - sniffer promiscuous mode mendeteksi
                             deauth / disassoc frame di udara
                             (indikasi serangan WiFi deauth)
   5. Channel Analyzer     - hitung kepadatan AP per channel (1-13),
                             bantu pilih channel paling "sepi"
   6. Ping Tool            - ping IP/host dari ESP32 (ICMP) via web UI
   7. Web Dashboard        - semua fitur di atas dikontrol lewat
                             halaman web (AsyncWebServer + WebSocket
                             live update), mobile friendly, dark theme
   8. Packet Counter       - total packet count & data rate kasar
                             per channel (dari promiscuous sniffer)
   9. Export CSV           - hasil scan bisa didownload sebagai CSV
   10. OLED-ready hook      - (opsional) print status ke Serial /
                             siap ditambah OLED SSD1306 belakangan

   CATATAN PENTING SOAL "15 SSID":
   --------------------------------
   Chip ESP32 asli (bukan S2/S3/C3) hardware WiFi radio-nya cuma bisa
   punya SATU SoftAP fungsional (yang beneran bisa dikonek & dapat
   IP). Untuk menampilkan BANYAK SSID sekaligus (mis. 15), firmware
   ini memakai teknik "beacon frame spoofing": ESP32 mengirim paket
   beacon 802.11 mentah dengan SSID & MAC berbeda-beda secara
   bergantian sangat cepat, sehingga device lain (HP/laptop) melihat
   seolah ada banyak AP. SSID hasil spoof ini HANYA TAMPIL DI LIST
   WIFI, tidak bisa dipakai internetan (bukan AP asli). SSID ke-0
   (SSID_MAIN) adalah SoftAP ASLI tempat dashboard web ini jalan &
   bisa dikonek beneran.

   Gunakan fitur beacon-spoof ini hanya untuk riset / edukasi /
   testing jaringan milik sendiri. Jangan dipakai mengganggu jaringan
   orang lain.
  =====================================================================
*/

#include <Arduino.h>
#include <WiFi.h>
#include <esp_wifi.h>
#include <esp_wifi_types.h>
#include <tcpip_adapter.h>
#include <AsyncTCP.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <DNSServer.h>
#include <Preferences.h>
#include <lwip/inet.h>
#include <lwip/netdb.h>

#include "webpages.h"
#include "beacon.h"
#include "sniffer.h"
#include "pingtool.h"

// ---------------------------------------------------------------------
// KONFIGURASI UTAMA
// ---------------------------------------------------------------------
#define FW_VERSION        "1.0.0"
#define SSID_MAIN          "ESP32-Analyzer"     // SSID asli (bisa konek beneran)
#define PASS_MAIN          "analyzer123"        // min 8 karakter
#define MAX_FAKE_SSID      15                   // batas SSID palsu (spec: max 15)
#define WEBSERVER_PORT      80
#define DNS_PORT            53
#define MAX_SCAN_RESULT     60
#define MAX_CLIENTS_TRACK   32
#define SNIFFER_CHANNEL_HOP_MS 300

AsyncWebServer server(WEBSERVER_PORT);
AsyncWebSocket ws("/ws");
DNSServer dnsServer;
Preferences prefs;

// ---------------------------------------------------------------------
// STATE GLOBAL
// ---------------------------------------------------------------------
struct ScanResultItem {
  char ssid[33];
  uint8_t bssid[6];
  int32_t rssi;
  uint8_t channel;
  wifi_auth_mode_t auth;
  bool hidden;
};

ScanResultItem lastScan[MAX_SCAN_RESULT];
int lastScanCount = 0;
bool scanInProgress = false;
unsigned long lastScanTime = 0;

struct ClientInfo {
  uint8_t mac[6];
  IPAddress ip;
  int8_t rssi;
  unsigned long connectedSince;
  bool active;
};
ClientInfo trackedClients[MAX_CLIENTS_TRACK];

bool beaconSpamActive = false;
String fakeSSIDList[MAX_FAKE_SSID];
int fakeSSIDCount = 0;

bool snifferActive = false;
bool deauthDetectActive = false;
uint32_t deauthCount = 0;
uint32_t totalPacketCount = 0;
uint32_t packetsPerChannel[14] = {0};
uint8_t currentSnifferChannel = 1;
unsigned long lastChannelHop = 0;

// forward decl
void broadcastState();
void setupRoutes();
void handleScanRequest();
void updateClientList();
String macToStr(const uint8_t *mac);
String getVendorFromMac(const uint8_t *mac);

// ---------------------------------------------------------------------
// SETUP
// ---------------------------------------------------------------------
void onWsEvent(AsyncWebSocket *server, AsyncWebSocketClient *client,
               AwsEventType type, void *arg, uint8_t *data, size_t len) {
  if (type == WS_EVT_CONNECT) {
    Serial.printf("[WS] Client #%u connected\n", client->id());
    broadcastState();
  } else if (type == WS_EVT_DISCONNECT) {
    Serial.printf("[WS] Client #%u disconnected\n", client->id());
  } else if (type == WS_EVT_DATA) {
    AwsFrameInfo *info = (AwsFrameInfo*)arg;
    if (info->final && info->index == 0 && info->len == len && info->opcode == WS_TEXT) {
      String msg = String((char*)data, len);
      Serial.printf("[WS] msg: %s\n", msg.c_str());
      // simple command protocol: cmd:payload
      if (msg == "ping_state") {
        broadcastState();
      }
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println();
  Serial.println("=====================================================");
  Serial.println(" ESP32 WiFi Analyzer & Toolkit  v" FW_VERSION);
  Serial.println(" DevKit V1 (WROOM-32)  |  Author: generated firmware");
  Serial.println("=====================================================");

  prefs.begin("wifianalyzer", false);

  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP(SSID_MAIN, PASS_MAIN, 1, 0, 8);
  delay(200);
  IPAddress apIP = WiFi.softAPIP();
  Serial.print("[AP] SoftAP started, IP: ");
  Serial.println(apIP);
  Serial.printf("[AP] SSID: %s  PASS: %s\n", SSID_MAIN, PASS_MAIN);

  dnsServer.start(DNS_PORT, "*", apIP);

  // init modul
  beaconInit();
  snifferInit();
  pingToolInit();

  ws.onEvent(onWsEvent);
  server.addHandler(&ws);
  setupRoutes();
  server.begin();
  Serial.println("[HTTP] Web server started on port 80");
  Serial.println("[INFO] Buka http://192.168.4.1 setelah connect ke SSID di atas");

  lastScanTime = millis() - 999999; // force initial scan allowed
}

// ---------------------------------------------------------------------
// LOOP
// ---------------------------------------------------------------------
unsigned long lastBroadcast = 0;
unsigned long lastClientPoll = 0;

void loop() {
  dnsServer.processNextRequest();
  ws.cleanupClients();

  // beacon spam tick (non blocking, dipanggil terus kalau aktif)
  if (beaconSpamActive) {
    beaconTick();
  }

  // sniffer channel hopping
  if (snifferActive) {
    if (millis() - lastChannelHop > SNIFFER_CHANNEL_HOP_MS) {
      lastChannelHop = millis();
      currentSnifferChannel++;
      if (currentSnifferChannel > 13) currentSnifferChannel = 1;
      esp_wifi_set_channel(currentSnifferChannel, WIFI_SECOND_CHAN_NONE);
    }
  }

  // update client list tiap 2 detik
  if (millis() - lastClientPoll > 2000) {
    lastClientPoll = millis();
    updateClientList();
  }

  // broadcast realtime state ke semua ws client tiap 1.5 detik
  if (millis() - lastBroadcast > 1500) {
    lastBroadcast = millis();
    broadcastState();
  }

  pingToolLoop();

  delay(2);
}

// ---------------------------------------------------------------------
// UTIL
// ---------------------------------------------------------------------
String macToStr(const uint8_t *mac) {
  char buf[18];
  snprintf(buf, sizeof(buf), "%02X:%02X:%02X:%02X:%02X:%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
  return String(buf);
}

// Mini OUI lookup (beberapa vendor umum), biar tidak butuh internet
String getVendorFromMac(const uint8_t *mac) {
  char oui[7];
  snprintf(oui, sizeof(oui), "%02X%02X%02X", mac[0], mac[1], mac[2]);
  String o = String(oui);
  struct OuiEntry { const char* prefix; const char* name; };
  static const OuiEntry table[] = {
    {"3C5AB4", "Google"}, {"F4F5D8", "Google"}, {"A4C138", "Google"},
    {"001A11", "Google"}, {"3CA062", "Apple"},  {"F0DBF8", "Apple"},
    {"A4B197", "Apple"},  {"DC2B2A", "Apple"},  {"5C9976", "Apple"},
    {"E0ACCB", "Apple"},  {"CC29F5", "Apple"},  {"8863DF", "Apple"},
    {"001CB3", "Apple"},  {"3C15C2", "Apple"},  {"F02475", "Samsung"},
    {"B0EC71", "Samsung"},{"8425DB", "Samsung"},{"CCF9E8", "Samsung"},
    {"5C0A5B", "Samsung"},{"E8508B", "Samsung"},{"001E7D", "Samsung"},
    {"DC4427", "Huawei"}, {"0019E0", "Huawei"},{"F83DFF", "Huawei"},
    {"00E0FC", "Huawei"}, {"48435A", "Huawei"},  {"1CBFCE", "Xiaomi"},
    {"F8A45F", "Xiaomi"}, {"64B473", "Xiaomi"},  {"98FAE3", "Xiaomi"},
    {"B0A732", "Xiaomi"}, {"7C1DD9", "Xiaomi"},  {"AC5F3E", "Sony"},
    {"FCF152", "Sony"},   {"3C0754", "Sony"},    {"D8321D", "Sony"},
    {"B827EB", "Raspberry Pi"}, {"DCA632", "Raspberry Pi"},
    {"E45F01", "Raspberry Pi"}, {"D83ADD", "TP-Link"},
    {"F4F26D", "TP-Link"}, {"14CC20", "TP-Link"}, {"A0F3C1", "TP-Link"},
    {"C46E1F", "TP-Link"}, {"18D6C7", "TP-Link"}, {"001D0F", "TP-Link"},
    {"B4750E", "Netgear"}, {"204E7F", "Netgear"}, {"C0F893", "Netgear"},
    {"E091F5", "Netgear"}, {"9C1E95", "Netgear"}, {"E4F4C6", "Espressif"},
    {"246F28", "Espressif"}, {"3030F9", "Espressif"}, {"7CDFA1", "Espressif"},
    {"C44F33", "Espressif"}, {"CC50E3", "Espressif"}, {"84F3EB", "Espressif"},
    {"A020A6", "Espressif"}, {"DC544A", "Espressif"}, {"EC94CB", "Intel"},
    {"001B77", "Intel"},  {"7085C2", "Intel"},   {"A0A8CD", "Intel"},
    {"3497F6", "Intel"},  {"1C4D70", "ASUS"},    {"08606E", "ASUS"},
    {"2C56DC", "ASUS"},   {"AC220B", "ASUS"},    {"D45D64", "ASUS"},
  };
  for (auto &e : table) {
    if (o == e.prefix) return String(e.name);
  }
  return "Unknown";
}

// ---------------------------------------------------------------------
// WIFI SCANNER
// ---------------------------------------------------------------------
void handleScanRequest() {
  if (scanInProgress) return;
  scanInProgress = true;
  Serial.println("[SCAN] Memulai WiFi scan...");
  int n = WiFi.scanNetworks(false, true, false, 300);
  lastScanCount = 0;
  memset(packetsPerChannel, 0, sizeof(packetsPerChannel));

  if (n > 0) {
    int count = min(n, MAX_SCAN_RESULT);
    for (int i = 0; i < count; i++) {
      ScanResultItem &item = lastScan[i];
      String ssid = WiFi.SSID(i);
      ssid.toCharArray(item.ssid, sizeof(item.ssid));
      item.hidden = (ssid.length() == 0);
      memcpy(item.bssid, WiFi.BSSID(i), 6);
      item.rssi = WiFi.RSSI(i);
      item.channel = WiFi.channel(i);
      item.auth = WiFi.encryptionType(i);
      if (item.channel >= 1 && item.channel <= 13) {
        packetsPerChannel[item.channel]++;
      }
    }
    lastScanCount = count;
  }
  WiFi.scanDelete();
  scanInProgress = false;
  lastScanTime = millis();
  Serial.printf("[SCAN] Selesai. %d jaringan ditemukan.\n", lastScanCount);
}

const char* authModeToStr(wifi_auth_mode_t m) {
  switch (m) {
    case WIFI_AUTH_OPEN: return "OPEN";
    case WIFI_AUTH_WEP: return "WEP";
    case WIFI_AUTH_WPA_PSK: return "WPA";
    case WIFI_AUTH_WPA2_PSK: return "WPA2";
    case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2";
    case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2-ENT";
    case WIFI_AUTH_WPA3_PSK: return "WPA3";
    case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2/WPA3";
    default: return "UNKNOWN";
  }
}

// ---------------------------------------------------------------------
// CLIENT MONITOR
// ---------------------------------------------------------------------
void updateClientList() {
  wifi_sta_list_t wifiStaList;
  tcpip_adapter_sta_list_t adapterStaList;
  esp_wifi_ap_get_sta_list(&wifiStaList);
  tcpip_adapter_get_sta_list(&wifiStaList, &adapterStaList);

  for (int i = 0; i < MAX_CLIENTS_TRACK; i++) trackedClients[i].active = false;

  int total = adapterStaList.num;
  for (int i = 0; i < total && i < MAX_CLIENTS_TRACK; i++) {
    tcpip_adapter_sta_info_t station = adapterStaList.sta[i];
    trackedClients[i].active = true;
    memcpy(trackedClients[i].mac, station.mac, 6);
    trackedClients[i].ip = IPAddress(station.ip.addr);
    if (trackedClients[i].connectedSince == 0) {
      trackedClients[i].connectedSince = millis();
    }
    // rssi client tidak selalu tersedia langsung; default 0 kalau tidak ada info
    trackedClients[i].rssi = 0;
    for (int j = 0; j < wifiStaList.num; j++) {
      if (memcmp(wifiStaList.sta[j].mac, station.mac, 6) == 0) {
        trackedClients[i].rssi = wifiStaList.sta[j].rssi;
        break;
      }
    }
  }
}

// ---------------------------------------------------------------------
// BROADCAST STATE (WEBSOCKET) - dikirim berkala ke dashboard
// ---------------------------------------------------------------------
void broadcastState() {
  if (ws.count() == 0) return;

  DynamicJsonDocument doc(12288);
  doc["type"] = "state";
  doc["uptime"] = millis() / 1000;
  doc["heap"] = ESP.getFreeHeap();
  doc["beaconActive"] = beaconSpamActive;
  doc["fakeSSIDCount"] = fakeSSIDCount;
  doc["snifferActive"] = snifferActive;
  doc["deauthCount"] = deauthCount;
  doc["totalPackets"] = totalPacketCount;
  doc["snifferChannel"] = currentSnifferChannel;
  doc["scanCount"] = lastScanCount;
  doc["clientCount"] = WiFi.softAPgetStationNum();

  JsonArray chArr = doc.createNestedArray("channelDensity");
  for (int c = 1; c <= 13; c++) chArr.add(packetsPerChannel[c]);

  JsonArray clientArr = doc.createNestedArray("clients");
  for (int i = 0; i < MAX_CLIENTS_TRACK; i++) {
    if (!trackedClients[i].active) continue;
    JsonObject o = clientArr.createNestedObject();
    o["mac"] = macToStr(trackedClients[i].mac);
    o["ip"] = trackedClients[i].ip.toString();
    o["rssi"] = trackedClients[i].rssi;
    o["vendor"] = getVendorFromMac(trackedClients[i].mac);
    o["since"] = (millis() - trackedClients[i].connectedSince) / 1000;
  }

  String out;
  serializeJson(doc, out);
  ws.textAll(out);
}

// ---------------------------------------------------------------------
// HTTP ROUTES
// ---------------------------------------------------------------------
void setupRoutes() {
  // dashboard utama
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request) {
    AsyncWebServerResponse *response = request->beginResponse(200, "text/html", INDEX_HTML);
    response->addHeader("Cache-Control", "no-cache");
    request->send(response);
  });

  server.on("/style.css", HTTP_GET, [](AsyncWebServerRequest *request) {
    request->send(200, "text/css", STYLE_CSS);
  });

  server.on("/app.js", HTTP_GET, [](AsyncWebServerRequest *request) {
    request->send(200, "application/javascript", APP_JS);
  });

  // captive portal helpers
  server.on("/generate_204", HTTP_GET, [](AsyncWebServerRequest *request) {
    request->redirect("/");
  });
  server.on("/fwlink", HTTP_GET, [](AsyncWebServerRequest *request) {
    request->redirect("/");
  });

  // --- API: scan ---
  server.on("/api/scan", HTTP_GET, [](AsyncWebServerRequest *request) {
    handleScanRequest();
    DynamicJsonDocument doc(16384);
    JsonArray arr = doc.createNestedArray("networks");
    for (int i = 0; i < lastScanCount; i++) {
      ScanResultItem &it = lastScan[i];
      JsonObject o = arr.createNestedObject();
      o["ssid"] = it.hidden ? "(hidden)" : String(it.ssid);
      o["bssid"] = macToStr(it.bssid);
      o["rssi"] = it.rssi;
      o["channel"] = it.channel;
      o["auth"] = authModeToStr(it.auth);
      o["vendor"] = getVendorFromMac(it.bssid);
    }
    doc["count"] = lastScanCount;
    String out;
    serializeJson(doc, out);
    request->send(200, "application/json", out);
  });

  // --- API: export scan CSV ---
  server.on("/api/scan.csv", HTTP_GET, [](AsyncWebServerRequest *request) {
    String csv = "SSID,BSSID,RSSI,Channel,Auth,Vendor\n";
    for (int i = 0; i < lastScanCount; i++) {
      ScanResultItem &it = lastScan[i];
      csv += (it.hidden ? "(hidden)" : String(it.ssid)) + ",";
      csv += macToStr(it.bssid) + ",";
      csv += String(it.rssi) + ",";
      csv += String(it.channel) + ",";
      csv += String(authModeToStr(it.auth)) + ",";
      csv += getVendorFromMac(it.bssid) + "\n";
    }
    AsyncWebServerResponse *response = request->beginResponse(200, "text/csv", csv);
    response->addHeader("Content-Disposition", "attachment; filename=wifi_scan.csv");
    request->send(response);
  });

  // --- API: client list ---
  server.on("/api/clients", HTTP_GET, [](AsyncWebServerRequest *request) {
    updateClientList();
    DynamicJsonDocument doc(4096);
    JsonArray arr = doc.createNestedArray("clients");
    for (int i = 0; i < MAX_CLIENTS_TRACK; i++) {
      if (!trackedClients[i].active) continue;
      JsonObject o = arr.createNestedObject();
      o["mac"] = macToStr(trackedClients[i].mac);
      o["ip"] = trackedClients[i].ip.toString();
      o["rssi"] = trackedClients[i].rssi;
      o["vendor"] = getVendorFromMac(trackedClients[i].mac);
    }
    String out;
    serializeJson(doc, out);
    request->send(200, "application/json", out);
  });

  // --- API: start beacon spam dengan list SSID custom (max 15) ---
  server.on("/api/beacon/start", HTTP_POST, [](AsyncWebServerRequest *request) {},
    NULL,
    [](AsyncWebServerRequest *request, uint8_t *data, size_t len, size_t index, size_t total) {
      DynamicJsonDocument doc(2048);
      DeserializationError err = deserializeJson(doc, data, len);
      if (err) {
        request->send(400, "application/json", "{\"ok\":false,\"error\":\"bad json\"}");
        return;
      }
      JsonArray arr = doc["ssids"];
      fakeSSIDCount = 0;
      for (JsonVariant v : arr) {
        if (fakeSSIDCount >= MAX_FAKE_SSID) break;
        fakeSSIDList[fakeSSIDCount] = v.as<String>();
        fakeSSIDCount++;
      }
      if (fakeSSIDCount == 0) {
        // default demo list kalau kosong
        const char* demo[] = {"Free_WiFi_1","Free_WiFi_2","Office_Guest","Cafe_Hotspot",
                               "Airport_Free","Hotel_Guest","Library_WiFi","Mall_WiFi",
                               "Park_Free_Net","Home_Guest","Test_SSID_1","Test_SSID_2",
                               "Demo_AP_A","Demo_AP_B","Analyzer_Fake"};
        for (int i = 0; i < MAX_FAKE_SSID; i++) fakeSSIDList[i] = String(demo[i]);
        fakeSSIDCount = MAX_FAKE_SSID;
      }
      beaconSetSSIDList(fakeSSIDList, fakeSSIDCount);
      beaconSpamActive = true;
      beaconStart();
      request->send(200, "application/json", "{\"ok\":true}");
    });

  server.on("/api/beacon/stop", HTTP_POST, [](AsyncWebServerRequest *request) {
    beaconSpamActive = false;
    beaconStop();
    request->send(200, "application/json", "{\"ok\":true}");
  });

  // --- API: sniffer / deauth detector ---
  server.on("/api/sniffer/start", HTTP_POST, [](AsyncWebServerRequest *request) {
    snifferActive = true;
    deauthCount = 0;
    totalPacketCount = 0;
    memset(packetsPerChannel, 0, sizeof(packetsPerChannel));
    snifferStart();
    request->send(200, "application/json", "{\"ok\":true}");
  });

  server.on("/api/sniffer/stop", HTTP_POST, [](AsyncWebServerRequest *request) {
    snifferActive = false;
    snifferStop();
    request->send(200, "application/json", "{\"ok\":true}");
  });

  // --- API: ping tool ---
  server.on("/api/ping", HTTP_POST, [](AsyncWebServerRequest *request) {},
    NULL,
    [](AsyncWebServerRequest *request, uint8_t *data, size_t len, size_t index, size_t total) {
      DynamicJsonDocument doc(512);
      deserializeJson(doc, data, len);
      String host = doc["host"] | "8.8.8.8";
      int count = doc["count"] | 4;
      pingToolStart(host, count);
      request->send(200, "application/json", "{\"ok\":true}");
    });

  server.on("/api/ping/result", HTTP_GET, [](AsyncWebServerRequest *request) {
    String out = pingToolGetResultJson();
    request->send(200, "application/json", out);
  });

  // --- API: device info ---
  server.on("/api/info", HTTP_GET, [](AsyncWebServerRequest *request) {
    DynamicJsonDocument doc(1024);
    doc["fw_version"] = FW_VERSION;
    doc["chip_model"] = ESP.getChipModel();
    doc["chip_rev"] = ESP.getChipRevision();
    doc["cores"] = ESP.getChipCores();
    doc["cpu_freq_mhz"] = ESP.getCpuFreqMHz();
    doc["flash_size"] = ESP.getFlashChipSize();
    doc["free_heap"] = ESP.getFreeHeap();
    doc["sdk_version"] = ESP.getSdkVersion();
    doc["mac_ap"] = WiFi.softAPmacAddress();
    doc["ap_ip"] = WiFi.softAPIP().toString();
    doc["ssid_main"] = SSID_MAIN;
    doc["max_fake_ssid"] = MAX_FAKE_SSID;
    String out;
    serializeJson(doc, out);
    request->send(200, "application/json", out);
  });

  server.onNotFound([](AsyncWebServerRequest *request) {
    request->redirect("/");
  });
}
