/*
 * ESP32 Captive Portal (Splash Page) + Audio (played on client's phone) + Internet Bridging (NAT)
 *
 * Alur:
 *  1. ESP32 connect ke WiFi "source" (STA) yang punya internet asli.
 *  2. ESP32 juga jadi Access Point (AP) sendiri untuk device lain.
 *  3. Saat device connect ke AP, OS device (Android/iOS) otomatis mendeteksi
 *     captive portal lewat DNS hijack + HTTP probe, lalu membuka splash page.
 *  4. Splash page berisi audio player (file MP3 disajikan dari LittleFS) + tombol "Lanjut".
 *  5. Setelah user klik "Lanjut", MAC address device tsb di-whitelist dan
 *     NAT (IPv4 NAPT) sudah aktif dari awal sehingga device langsung dapat internet.
 *
 * PENTING:
 *  - Upload file audio ke LittleFS lewat `pio run -t uploadfs` (lokal) atau
 *    biarkan CI hanya build firmware (data folder di-flash terpisah, lihat README).
 *  - NAT butuh arduino-esp32 core yang menyediakan lwip_napt.h (sudah di-pin di platformio.ini).
 */

#include <WiFi.h>
#include <DNSServer.h>
#include <LittleFS.h>
#include <ESPAsyncWebServer.h>
#include "lwip/lwip_napt.h"
#include "lwip/inet.h"
#include "config.h"

// ---------- Globals ----------
DNSServer dnsServer;
AsyncWebServer server(80);

const byte DNS_PORT = 53;
IPAddress apIP(192, 168, 4, 1);
IPAddress apSubnet(255, 255, 255, 0);

bool staConnected = false;

// Sederhana: simpan MAC device yang sudah klik "lanjut" supaya tidak
// dipaksa ke splash page lagi selama masih connect (opsional, karena
// NAT sudah aktif untuk semua device di AP secara default di skrip ini).
#define MAX_ALLOWED 32
String allowedMacs[MAX_ALLOWED];
int allowedCount = 0;

bool isAllowed(const String &mac) {
  for (int i = 0; i < allowedCount; i++) {
    if (allowedMacs[i] == mac) return true;
  }
  return false;
}

void addAllowed(const String &mac) {
  if (isAllowed(mac)) return;
  if (allowedCount < MAX_ALLOWED) {
    allowedMacs[allowedCount++] = mac;
  }
}

// Ambil MAC address client dari IP (lookup ARP table ESP32)
String getClientMac(AsyncWebServerRequest *request) {
  IPAddress clientIP = request->client()->remoteIP();
  // ESP32 Arduino core tidak expose ARP table langsung dengan mudah;
  // sebagai fallback kita pakai IP sebagai identifier sesi.
  return clientIP.toString();
}

// ---------- HTML Splash Page ----------
String buildSplashPage() {
  String html = R"HTML(
<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>__TITLE__</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: linear-gradient(135deg, #1e3c72, #2a5298);
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
  }
  .card {
    background: #fff;
    border-radius: 16px;
    padding: 32px 24px;
    max-width: 400px;
    width: 100%;
    text-align: center;
    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
  }
  h1 { font-size: 22px; color: #1e293b; margin-bottom: 8px; }
  p.subtitle { color: #64748b; font-size: 14px; margin-bottom: 24px; }
  audio { width: 100%; margin-bottom: 24px; }
  button {
    width: 100%;
    padding: 14px;
    font-size: 16px;
    font-weight: 600;
    color: #fff;
    background: #2563eb;
    border: none;
    border-radius: 10px;
    cursor: pointer;
    transition: background 0.2s;
  }
  button:active { background: #1d4ed8; }
  button:disabled { background: #94a3b8; cursor: not-allowed; }
  .hint { margin-top: 14px; font-size: 12px; color: #94a3b8; }
</style>
</head>
<body>
  <div class="card">
    <h1>__TITLE__</h1>
    <p class="subtitle">__SUBTITLE__</p>
    <audio controls autoplay>
      <source src="__AUDIO_FILE__" type="audio/mpeg">
      Browser Anda tidak mendukung pemutar audio.
    </audio>
    <button id="btnContinue" onclick="goContinue()">Lanjut ke Internet</button>
    <p class="hint">Dengan menekan tombol, Anda menyetujui akses WiFi ini.</p>
  </div>

<script>
function goContinue() {
  var btn = document.getElementById('btnContinue');
  btn.disabled = true;
  btn.innerText = 'Menghubungkan...';
  fetch('/continue', { method: 'POST' })
    .then(function() {
      // Arahkan ke halaman netral agar OS menutup captive portal window
      window.location.href = 'http://connectivitycheck.gstatic.com/generate_204';
    })
    .catch(function() {
      btn.disabled = false;
      btn.innerText = 'Lanjut ke Internet';
    });
}
</script>
</body>
</html>
)HTML";

  html.replace("__TITLE__", SPLASH_TITLE);
  html.replace("__SUBTITLE__", SPLASH_SUBTITLE);
  html.replace("__AUDIO_FILE__", AUDIO_FILENAME);
  return html;
}

// ---------- Setup NAT / IP forwarding ----------
void setupNAT() {
  // Aktifkan IP forwarding dan NAPT pada interface AP -> STA
  ip_napt_enable(WiFi.localIP(), 1);
  Serial.println("[NAT] NAPT diaktifkan, AP -> STA forwarding siap.");
}

// ---------- WiFi Setup ----------
void connectSTA() {
  Serial.printf("[STA] Menghubungkan ke WiFi source: %s\n", STA_SSID);
  WiFi.mode(WIFI_AP_STA);
  WiFi.begin(STA_SSID, STA_PASSWORD);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    delay(300);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    staConnected = true;
    Serial.printf("[STA] Terhubung! IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    staConnected = false;
    Serial.println("[STA] GAGAL terhubung ke WiFi source. Cek SSID/password di config.h");
  }
}

void setupAP() {
  WiFi.softAPConfig(apIP, apIP, apSubnet);
  bool ok;
  if (strlen(AP_PASSWORD) == 0) {
    ok = WiFi.softAP(AP_SSID, NULL, AP_CHANNEL, false, AP_MAX_CONN);
  } else {
    ok = WiFi.softAP(AP_SSID, AP_PASSWORD, AP_CHANNEL, false, AP_MAX_CONN);
  }
  Serial.printf("[AP] SoftAP %s -> %s | IP: %s\n",
                AP_SSID, ok ? "OK" : "GAGAL",
                WiFi.softAPIP().toString().c_str());
}

// ---------- Captive Portal Detection Handlers ----------
// Berbagai OS punya endpoint probe berbeda untuk mendeteksi captive portal.
void registerCaptiveProbes() {
  auto redirectToSplash = [](AsyncWebServerRequest *request) {
    request->redirect("http://192.168.4.1/");
  };

  // Android
  server.on("/generate_204", HTTP_GET, redirectToSplash);
  server.on("/gen_204", HTTP_GET, redirectToSplash);
  // iOS / macOS
  server.on("/hotspot-detect.html", HTTP_GET, redirectToSplash);
  server.on("/library/test/success.html", HTTP_GET, redirectToSplash);
  // Windows
  server.on("/connecttest.txt", HTTP_GET, redirectToSplash);
  server.on("/ncsi.txt", HTTP_GET, redirectToSplash);
  // Firefox
  server.on("/canonical.html", HTTP_GET, redirectToSplash);
}

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== ESP32 Captive Splash Portal ===");

  if (!LittleFS.begin(true)) {
    Serial.println("[FS] Gagal mount LittleFS!");
  } else {
    Serial.println("[FS] LittleFS siap.");
  }

  connectSTA();
  setupAP();

  if (staConnected) {
    setupNAT();
  } else {
    Serial.println("[NAT] Dilewati karena STA tidak terhubung. Device akan connect ke AP tapi TANPA internet.");
  }

  // DNS: semua domain diarahkan ke IP ESP32 supaya captive portal terdeteksi
  dnsServer.start(DNS_PORT, "*", apIP);

  // Splash page utama
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request) {
    request->send(200, "text/html", buildSplashPage());
  });

  // Sajikan file MP3 dari LittleFS
  server.on(AUDIO_FILENAME, HTTP_GET, [](AsyncWebServerRequest *request) {
    if (LittleFS.exists(AUDIO_FILENAME)) {
      request->send(LittleFS, AUDIO_FILENAME, "audio/mpeg");
    } else {
      request->send(404, "text/plain", "File audio tidak ditemukan. Upload dulu ke LittleFS.");
    }
  });

  // Endpoint saat tombol "Lanjut" ditekan
  server.on("/continue", HTTP_POST, [](AsyncWebServerRequest *request) {
    String id = getClientMac(request);
    addAllowed(id);
    Serial.printf("[PORTAL] Device %s menekan lanjut. Internet aktif (NAT global sudah jalan).\n", id.c_str());
    request->send(200, "text/plain", "OK");
  });

  registerCaptiveProbes();

  // Semua request lain yang tidak dikenal -> redirect ke splash (memicu captive portal popup)
  server.onNotFound([](AsyncWebServerRequest *request) {
    request->redirect("http://192.168.4.1/");
  });

  server.begin();
  Serial.println("[WEB] Server siap di port 80.");
}

void loop() {
  dnsServer.processNextRequest();

  // Coba reconnect STA kalau putus, supaya NAT tidak mati total
  static unsigned long lastCheck = 0;
  if (millis() - lastCheck > 10000) {
    lastCheck = millis();
    if (WiFi.status() != WL_CONNECTED && staConnected) {
      Serial.println("[STA] Koneksi ke source terputus, mencoba reconnect...");
      staConnected = false;
      WiFi.reconnect();
    } else if (WiFi.status() == WL_CONNECTED && !staConnected) {
      staConnected = true;
      Serial.println("[STA] Reconnect berhasil, mengaktifkan ulang NAT...");
      setupNAT();
    }
  }
}
