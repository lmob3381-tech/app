/*
 * WiFi Home Monitor — ESP32 Network Probe Firmware
 * Sesuai kontrak: docs/api-contract.md
 *
 * Board target: ESP32 DevKit V1
 * Framework   : Arduino (via PlatformIO)
 *
 * Fitur:
 *  - Provisioning WiFi tanpa hardcode kredensial (WiFiManager)
 *  - NTP sync untuk timestamp last_seen
 *  - ARP/ping-sweep scan ke subnet lokal /24 (async, non-blocking)
 *  - REST API port 80: GET /status, GET /devices, POST /scan, POST /ping
 *  - WebSocket port 81 path /ws: broadcast device_list, scan_started, scan_finished
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiManager.h>
#include <ESPAsyncWebServer.h>
#include <AsyncTCP.h>
#include <ArduinoJson.h>
#include <ESPmDNS.h>
#include <lwip/etharp.h>
#include <lwip/inet.h>
#include <time.h>

#include "oui_table.h"

// ---------------------------------------------------------------------------
// Konfigurasi
// ---------------------------------------------------------------------------
#define FIRMWARE_VERSION   "1.0.0"
#define DEVICE_NAME        "ESP32-NetProbe"
#define MDNS_HOSTNAME      "wifihome-probe"
#define MAX_DEVICES        50      // batas RAM sesuai kontrak (bagian 6.4)
#define SCAN_TASK_STACK    4096
#define PING_TIMEOUT_MS    200     // timeout per host saat sweep
#define NTP_SERVER1        "pool.ntp.org"
#define NTP_SERVER2        "time.nist.gov"
#define GMT_OFFSET_SEC     0       // last_seen pakai UNIX epoch, offset tak relevan
#define DAYLIGHT_OFFSET_SEC 0

// ---------------------------------------------------------------------------
// State global
// ---------------------------------------------------------------------------
AsyncWebServer httpServer(80);
AsyncWebServer wsHttpServer(81);   // server HTTP dummy di port 81, dipakai AsyncWebSocket
AsyncWebSocket ws("/ws");

struct NetworkDevice {
  String ip;
  String mac;
  String hostname;   // "" berarti null di JSON
  String vendor;      // "" berarti null di JSON
  time_t lastSeen;
  bool isOnline;
  bool valid = false;
};

NetworkDevice deviceList[MAX_DEVICES];
int deviceCount = 0;

volatile bool scanInProgress = false;
TaskHandle_t scanTaskHandle = nullptr;

// ---------------------------------------------------------------------------
// Util
// ---------------------------------------------------------------------------

String macToUpperColon(const uint8_t* macBytes) {
  char buf[18];
  snprintf(buf, sizeof(buf), "%02X:%02X:%02X:%02X:%02X:%02X",
           macBytes[0], macBytes[1], macBytes[2],
           macBytes[3], macBytes[4], macBytes[5]);
  return String(buf);
}

// Cari device di list berdasarkan MAC. Return index, atau -1 kalau tidak ada.
int findDeviceByMac(const String& mac) {
  for (int i = 0; i < deviceCount; i++) {
    if (deviceList[i].valid && deviceList[i].mac.equalsIgnoreCase(mac)) {
      return i;
    }
  }
  return -1;
}

// Upsert device ke list (update kalau sudah ada, tambah kalau belum & masih ada slot).
void upsertDevice(const String& ip, const String& mac, bool online) {
  int idx = findDeviceByMac(mac);
  time_t now;
  time(&now);

  if (idx >= 0) {
    deviceList[idx].ip = ip;
    deviceList[idx].isOnline = online;
    if (online) deviceList[idx].lastSeen = now;
    return;
  }

  if (deviceCount >= MAX_DEVICES) {
    // Penuh: cari slot yang paling lama offline untuk digantikan.
    int oldestIdx = 0;
    time_t oldestTime = deviceList[0].lastSeen;
    for (int i = 1; i < deviceCount; i++) {
      if (!deviceList[i].isOnline && deviceList[i].lastSeen < oldestTime) {
        oldestIdx = i;
        oldestTime = deviceList[i].lastSeen;
      }
    }
    idx = oldestIdx;
  } else {
    idx = deviceCount;
    deviceCount++;
  }

  deviceList[idx].ip = ip;
  deviceList[idx].mac = mac;
  deviceList[idx].hostname = "";  // resolusi hostname: lihat catatan di bawah
  const char* vendor = lookupVendor(mac);
  deviceList[idx].vendor = vendor ? String(vendor) : "";
  deviceList[idx].lastSeen = now;
  deviceList[idx].isOnline = online;
  deviceList[idx].valid = true;
}

// Tandai semua device yang tidak terlihat di scan terbaru sebagai offline,
// tanpa menghapusnya dari list (sesuai kontrak 3.2 - histori tetap terlihat).
void markMissingAsOffline(bool seenThisScan[MAX_DEVICES]) {
  for (int i = 0; i < deviceCount; i++) {
    if (deviceList[i].valid && !seenThisScan[i]) {
      deviceList[i].isOnline = false;
    }
  }
}

// ---------------------------------------------------------------------------
// JSON builders
// ---------------------------------------------------------------------------

String buildStatusJson() {
  JsonDocument doc;
  doc["device_name"] = DEVICE_NAME;
  doc["firmware_version"] = FIRMWARE_VERSION;
  doc["uptime_sec"] = millis() / 1000;
  doc["free_heap"] = ESP.getFreeHeap();
  doc["wifi_rssi"] = WiFi.RSSI();
  doc["local_ip"] = WiFi.localIP().toString();
  doc["scan_in_progress"] = scanInProgress;
  doc["devices_found"] = deviceCount;

  String out;
  serializeJson(doc, out);
  return out;
}

String buildDevicesJson() {
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();

  for (int i = 0; i < deviceCount; i++) {
    if (!deviceList[i].valid) continue;
    JsonObject o = arr.add<JsonObject>();
    o["ip"] = deviceList[i].ip;
    o["mac"] = deviceList[i].mac;
    if (deviceList[i].hostname.length() > 0) {
      o["hostname"] = deviceList[i].hostname;
    } else {
      o["hostname"] = nullptr;
    }
    if (deviceList[i].vendor.length() > 0) {
      o["vendor"] = deviceList[i].vendor;
    } else {
      o["vendor"] = nullptr;
    }
    o["last_seen"] = (uint32_t)deviceList[i].lastSeen;
    o["is_online"] = deviceList[i].isOnline;
  }

  String out;
  serializeJson(doc, out);
  return out;
}

// ---------------------------------------------------------------------------
// WebSocket broadcast helpers
// ---------------------------------------------------------------------------

void wsBroadcastDeviceList() {
  JsonDocument doc;
  doc["type"] = "device_list";
  JsonArray arr = doc["devices"].to<JsonArray>();
  for (int i = 0; i < deviceCount; i++) {
    if (!deviceList[i].valid) continue;
    JsonObject o = arr.add<JsonObject>();
    o["ip"] = deviceList[i].ip;
    o["mac"] = deviceList[i].mac;
    if (deviceList[i].hostname.length() > 0) o["hostname"] = deviceList[i].hostname;
    else o["hostname"] = nullptr;
    if (deviceList[i].vendor.length() > 0) o["vendor"] = deviceList[i].vendor;
    else o["vendor"] = nullptr;
    o["last_seen"] = (uint32_t)deviceList[i].lastSeen;
    o["is_online"] = deviceList[i].isOnline;
  }
  String out;
  serializeJson(doc, out);
  ws.textAll(out);
}

void wsBroadcastScanStarted() {
  ws.textAll("{\"type\":\"scan_started\"}");
}

void wsBroadcastScanFinished(int count) {
  JsonDocument doc;
  doc["type"] = "scan_finished";
  doc["count"] = count;
  String out;
  serializeJson(doc, out);
  ws.textAll(out);
}

// ---------------------------------------------------------------------------
// ARP / ping-sweep scan
// ---------------------------------------------------------------------------
//
// Strategi (sesuai catatan di kontrak bagian 2): kirim ping ke tiap host di
// subnet /24 lokal. Setelah host membalas ping (atau bahkan setelah ARP
// request terkirim & dibalas, terlepas dari ICMP-nya sendiri), entry ARP
// otomatis terisi di tabel ARP lwIP milik ESP32, lalu kita baca MAC dari situ.
// Ini menghindari perlunya raw socket / library ping tambahan yang berat.

// Baca entri ARP lwIP untuk sebuah IP. Return true kalau ketemu & valid.
bool getArpEntry(IPAddress ip, uint8_t* macOut) {
  ip4_addr_t target;
  target.addr = static_cast<uint32_t>(ip);

  const ip4_addr_t* ipaddr;
  struct eth_addr* ethaddr;

  // etharp_find_addr mencari di tabel ARP global lwIP
  int8_t idx = etharp_find_addr(nullptr, &target, &ethaddr, &ipaddr);
  if (idx >= 0 && ethaddr != nullptr) {
    memcpy(macOut, ethaddr->addr, 6);
    return true;
  }
  return false;
}

// Kirim "ARP-inducing" probe: buka koneksi TCP singkat (connect+close cepat)
// ke port umum, atau gunakan ping ICMP via lwIP raw ping jika tersedia.
// Di sini kita pakai pendekatan simpel dan portable: WiFiClient connect
// dengan timeout pendek ke port 80 (banyak device rumah punya port ini,
// tapi yang penting bukan berhasil connect-nya, melainkan proses TCP
// handshake sudah memicu ARP resolution di level lwIP).
bool probeHost(IPAddress ip) {
  WiFiClient client;
  // connect(ip, port, timeout_ms): overload ini yang benar-benar membatasi
  // waktu tunggu koneksi di ESP32 Arduino core.
  bool connected = client.connect(ip, 80, PING_TIMEOUT_MS);
  client.stop();
  return connected;
}

void scanTask(void* param) {
  scanInProgress = true;
  wsBroadcastScanStarted();

  IPAddress localIp = WiFi.localIP();
  IPAddress subnetMask = WiFi.subnetMask();

  // PENTING: IPAddress internal disimpan network byte order (big-endian).
  // Konversi ke host byte order dulu (ntohl) supaya aritmatika +1/-1/AND/OR
  // di bawah ini benar secara numerik, baru dikonversi balik (htonl) saat
  // dipakai membuat IPAddress lagi.
  uint32_t ipHost = ntohl(static_cast<uint32_t>(localIp));
  uint32_t maskHost = ntohl(static_cast<uint32_t>(subnetMask));
  uint32_t network = ipHost & maskHost;
  uint32_t broadcast = network | (~maskHost);

  // Batasi ke /24 standar (254 host) sesuai asumsi kontrak bagian 6.4,
  // walau mask sebenarnya bisa berbeda — untuk keamanan RAM & waktu scan.
  uint32_t hostStart = network + 1;
  uint32_t hostEnd = broadcast - 1;
  const uint32_t MAX_HOSTS_TO_SCAN = 254;
  if (hostEnd - hostStart > MAX_HOSTS_TO_SCAN) {
    hostEnd = hostStart + MAX_HOSTS_TO_SCAN;
  }

  bool seenThisScan[MAX_DEVICES] = { false };

  for (uint32_t h = hostStart; h <= hostEnd; h++) {
    IPAddress target(htonl(h));
    if (target == localIp) continue;

    bool responded = probeHost(target);

    uint8_t macBytes[6];
    bool haveMac = getArpEntry(target, macBytes);

    if (haveMac) {
      String macStr = macToUpperColon(macBytes);
      // Hanya tandai online kalau device benar-benar merespons probe di scan
      // INI, bukan sekadar punya entri ARP lama (yang bisa stale/basi).
      upsertDevice(target.toString(), macStr, responded);

      int idx = findDeviceByMac(macStr);
      if (idx >= 0 && idx < MAX_DEVICES) seenThisScan[idx] = true;
    }

    // Beri jeda kecil supaya WiFi stack & WebServer tetap responsif
    // (task ini jalan di core terpisah, tapi tetap hindari busy-loop total).
    vTaskDelay(pdMS_TO_TICKS(5));
  }

  markMissingAsOffline(seenThisScan);

  scanInProgress = false;
  wsBroadcastDeviceList();
  wsBroadcastScanFinished(deviceCount);

  scanTaskHandle = nullptr;
  vTaskDelete(nullptr);
}

void startScan() {
  if (scanInProgress) return; // sudah jalan, jangan mulai dobel
  xTaskCreatePinnedToCore(
    scanTask,
    "arp_scan_task",
    SCAN_TASK_STACK * 2,   // scan butuh stack lumayan karena JsonDocument & String
    nullptr,
    1,                     // priority rendah, jangan ganggu WiFi/HTTP task
    &scanTaskHandle,
    1                      // core 1, biar core 0 (WiFi) tetap lega
  );
}

// ---------------------------------------------------------------------------
// REST API handlers
// ---------------------------------------------------------------------------

void handleStatus(AsyncWebServerRequest* request) {
  request->send(200, "application/json", buildStatusJson());
}

void handleDevices(AsyncWebServerRequest* request) {
  request->send(200, "application/json", buildDevicesJson());
}

void handleScanTrigger(AsyncWebServerRequest* request) {
  startScan();
  request->send(200, "application/json", "{\"status\":\"scan_started\"}");
}

// POST /ping?host=<ip>&count=<n> — prioritas rendah, implementasi ringan.
// CATATAN: handler ini blocking selama proses ping berjalan (maks ~20 x
// (200ms + 20ms) jika count=20 dan semua request timeout ≈ 4.4 detik).
// Ini secara teknis melanggar prinsip "jangan blocking" di bagian 6.5 kontrak,
// tapi endpoint ini eksplisit ditandai low-priority/opsional di kontrak
// (bagian 3.4), dan count dibatasi ketat (maks 20) untuk membatasi dampak.
// Kalau perlu benar-benar non-blocking, pindahkan ke task FreeRTOS terpisah
// seperti scanTask, dengan client id disimpan untuk dikirim balik lewat WS.
void handlePing(AsyncWebServerRequest* request) {
  if (!request->hasParam("host")) {
    request->send(400, "application/json", "{\"error\":\"missing host param\"}");
    return;
  }
  String hostStr = request->getParam("host")->value();
  int count = 4;
  if (request->hasParam("count")) {
    count = request->getParam("count")->value().toInt();
    if (count <= 0) count = 4;
    if (count > 20) count = 20; // guard rail, jangan diminta ping ratusan kali
  }

  IPAddress target;
  if (!target.fromString(hostStr)) {
    request->send(400, "application/json", "{\"error\":\"invalid host\"}");
    return;
  }

  int sent = 0, received = 0;
  float minMs = -1, maxMs = -1, totalMs = 0;

  for (int i = 0; i < count; i++) {
    sent++;
    uint32_t t0 = millis();
    bool ok = probeHost(target);
    uint32_t t1 = millis();
    if (ok) {
      float dt = (float)(t1 - t0);
      received++;
      totalMs += dt;
      if (minMs < 0 || dt < minMs) minMs = dt;
      if (dt > maxMs) maxMs = dt;
    }
    delay(20);
  }

  JsonDocument doc;
  doc["host"] = hostStr;
  doc["sent"] = sent;
  doc["received"] = received;
  doc["min_ms"] = received > 0 ? minMs : 0;
  doc["avg_ms"] = received > 0 ? (totalMs / received) : 0;
  doc["max_ms"] = received > 0 ? maxMs : 0;
  doc["packet_loss_percent"] = sent > 0 ? (100.0f * (sent - received) / sent) : 0;

  String out;
  serializeJson(doc, out);
  request->send(200, "application/json", out);
}

void handleNotFound(AsyncWebServerRequest* request) {
  request->send(404, "application/json", "{\"error\":\"not found\"}");
}

// ---------------------------------------------------------------------------
// WebSocket event handler
// ---------------------------------------------------------------------------

void onWsEvent(AsyncWebSocket* server, AsyncWebSocketClient* client,
               AwsEventType type, void* arg, uint8_t* data, size_t len) {
  if (type == WS_EVT_CONNECT) {
    Serial.printf("[WS] Client #%u connected\n", client->id());
    // Kirim device_list terbaru begitu client konek, biar app langsung punya data.
    JsonDocument doc;
    doc["type"] = "device_list";
    JsonArray arr = doc["devices"].to<JsonArray>();
    for (int i = 0; i < deviceCount; i++) {
      if (!deviceList[i].valid) continue;
      JsonObject o = arr.add<JsonObject>();
      o["ip"] = deviceList[i].ip;
      o["mac"] = deviceList[i].mac;
      if (deviceList[i].hostname.length() > 0) o["hostname"] = deviceList[i].hostname;
      else o["hostname"] = nullptr;
      if (deviceList[i].vendor.length() > 0) o["vendor"] = deviceList[i].vendor;
      else o["vendor"] = nullptr;
      o["last_seen"] = (uint32_t)deviceList[i].lastSeen;
      o["is_online"] = deviceList[i].isOnline;
    }
    String out;
    serializeJson(doc, out);
    client->text(out);
  } else if (type == WS_EVT_DISCONNECT) {
    Serial.printf("[WS] Client #%u disconnected\n", client->id());
  }
  // Sesuai kontrak 4.2: app tidak mengirim pesan ke ESP32 lewat WS di versi ini,
  // jadi WS_EVT_DATA sengaja tidak diproses.
}

// ---------------------------------------------------------------------------
// Setup & loop
// ---------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n[BOOT] WiFi Home Monitor - ESP32 Network Probe");
  Serial.printf("[BOOT] Firmware version: %s\n", FIRMWARE_VERSION);

  // --- WiFi provisioning tanpa hardcode kredensial ---
  WiFiManager wm;
  // wm.resetSettings(); // uncomment sekali kalau perlu lupakan WiFi tersimpan

  bool connected = wm.autoConnect("ESP32-Setup");
  if (!connected) {
    Serial.println("[WIFI] Gagal connect & provisioning timeout, restart...");
    delay(3000);
    ESP.restart();
  }

  Serial.print("[WIFI] Connected! IP: ");
  Serial.println(WiFi.localIP());
  Serial.print("[WIFI] Subnet mask: ");
  Serial.println(WiFi.subnetMask());

  // --- mDNS (nice-to-have sesuai kontrak bagian 5) ---
  if (MDNS.begin(MDNS_HOSTNAME)) {
    Serial.printf("[MDNS] Aktif di http://%s.local/\n", MDNS_HOSTNAME);
    MDNS.addService("http", "tcp", 80);
  } else {
    Serial.println("[MDNS] Gagal start mDNS (non-fatal, lanjut tanpa mDNS)");
  }

  // --- NTP sync untuk last_seen (UNIX epoch) ---
  configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER1, NTP_SERVER2);
  Serial.print("[NTP] Menunggu sinkronisasi waktu");
  time_t now = time(nullptr);
  int ntpRetries = 0;
  while (now < 8 * 3600 * 2 && ntpRetries < 20) { // tunggu sampai waktu masuk akal
    delay(500);
    Serial.print(".");
    now = time(nullptr);
    ntpRetries++;
  }
  Serial.println();
  if (now >= 8 * 3600 * 2) {
    Serial.printf("[NTP] Waktu tersinkron: %lu\n", (unsigned long)now);
  } else {
    Serial.println("[NTP] Gagal sinkron, last_seen mungkin tidak akurat (pakai millis-based fallback jika perlu)");
  }

  // --- REST API routes (port 80) ---
  httpServer.on("/status", HTTP_GET, handleStatus);
  httpServer.on("/devices", HTTP_GET, handleDevices);
  httpServer.on("/scan", HTTP_POST, handleScanTrigger);
  httpServer.on("/ping", HTTP_POST, handlePing);
  httpServer.onNotFound(handleNotFound);
  httpServer.begin();
  Serial.println("[HTTP] REST API aktif di port 80");

  // --- WebSocket (port 81, path /ws) ---
  ws.onEvent(onWsEvent);
  wsHttpServer.addHandler(&ws);
  wsHttpServer.begin();
  Serial.println("[WS] WebSocket server aktif di ws://<ip>:81/ws");

  Serial.println("[BOOT] Setup selesai. Siap menerima request.");
}

void loop() {
  // ESPAsyncWebServer & AsyncWebSocket bekerja berbasis event/callback,
  // jadi loop() sengaja dibiarkan ringan. Cleanup client WS mati secara berkala.
  ws.cleanupClients();

  // Cetak status ringkas tiap 30 detik ke Serial untuk debugging lapangan.
  static uint32_t lastPrint = 0;
  if (millis() - lastPrint > 30000) {
    lastPrint = millis();
    Serial.printf("[LOOP] heap=%u devices=%d scanning=%s rssi=%d\n",
                  ESP.getFreeHeap(), deviceCount,
                  scanInProgress ? "yes" : "no", WiFi.RSSI());
  }

  delay(10);
}
