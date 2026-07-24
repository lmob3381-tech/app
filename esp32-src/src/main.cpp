/*
 * ESP32 Captive Splash Portal
 *
 * Alur:
 *  1. ESP32 jadi Access Point (AP) sendiri, device lain bisa connect.
 *  2. Saat device connect, OS-nya (Android/iOS/Windows) otomatis mendeteksi
 *     captive portal lewat DNS hijack + HTTP probe, lalu membuka splash page.
 *  3. Splash page berisi audio player (file MP3 dari LittleFS, autoplay di HP
 *     user) + tombol "Lanjut".
 *  4. Setelah tombol diklik, portal ditutup dan device browsing seperti biasa
 *     (mengikuti pengaturan WiFi normal ESP32).
 *
 * CATATAN:
 *  - Versi ini TIDAK melakukan NAT/internet-sharing dari WiFi lain (fitur itu
 *    dibuang karena ip_napt_enable() tidak tersedia di precompiled Arduino
 *    framework untuk ESP32 - lihat README.md untuk detail & alternatif).
 *  - Kalau device yang connect ke AP ESP32 butuh akses internet asli, itu
 *    perlu disiapkan terpisah (mis. lewat router fisik atau proyek berbasis
 *    ESP-IDF murni).
 */

#include <WiFi.h>
#include <DNSServer.h>
#include <LittleFS.h>
#include <ESPAsyncWebServer.h>
#include "config.h"

// ---------- Globals ----------
DNSServer dnsServer;
AsyncWebServer server(80);

const byte DNS_PORT = 53;
IPAddress apIP(192, 168, 4, 1);
IPAddress apSubnet(255, 255, 255, 0);

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
    <audio id="player" controls>
      <source src="__AUDIO_FILE__" type="audio/mpeg">
      Browser Anda tidak mendukung pemutar audio.
    </audio>
    <button id="btnContinue" onclick="goContinue()">Lanjut</button>
    <p class="hint">Tekan tombol untuk memutar audio dan melanjutkan.</p>
  </div>

<script>
function goContinue() {
  var btn = document.getElementById('btnContinue');
  var player = document.getElementById('player');

  // Beberapa browser mobile hanya izinkan audio play() setelah ada
  // interaksi user (klik) -- makanya play() dipanggil di sini, bukan autoplay.
  player.play().catch(function(e) {
    console.log('Audio play error:', e);
  });

  btn.disabled = true;
  btn.innerText = 'Memutar audio...';

  fetch('/continue', { method: 'POST' })
    .then(function() {
      btn.innerText = 'Selesai';
      setTimeout(function() {
        window.location.href = 'http://connectivitycheck.gstatic.com/generate_204';
      }, 1500);
    })
    .catch(function() {
      btn.disabled = false;
      btn.innerText = 'Lanjut';
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

// ---------- WiFi AP Setup ----------
void setupAP() {
  WiFi.mode(WIFI_AP);
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
    if (!LittleFS.exists(AUDIO_FILENAME)) {
      Serial.printf("[FS] PERINGATAN: file %s tidak ditemukan! Upload dulu ke LittleFS.\n", AUDIO_FILENAME);
    }
  }

  setupAP();

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
    Serial.println("[PORTAL] Tombol lanjut ditekan.");
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
}
