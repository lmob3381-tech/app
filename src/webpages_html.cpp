#include "webpages.h"

const char INDEX_HTML[] PROGMEM = R"HTMLDOC(
<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<title>ESP32 WiFi Analyzer</title>
<link rel="stylesheet" href="/style.css">
</head>
<body>
<div class="topbar">
  <div class="brand">
    <span class="logo">&#128225;</span>
    <div>
      <h1>ESP32 WiFi Analyzer</h1>
      <span class="subtitle" id="subtitleInfo">memuat info perangkat...</span>
    </div>
  </div>
  <div class="stat-pill" id="heapPill">Heap: -- KB</div>
</div>

<div class="tabs">
  <button class="tab-btn active" data-tab="scan">Scanner</button>
  <button class="tab-btn" data-tab="clients">Clients</button>
  <button class="tab-btn" data-tab="beacon">Multi-SSID</button>
  <button class="tab-btn" data-tab="sniffer">Deauth/Sniffer</button>
  <button class="tab-btn" data-tab="ping">Ping</button>
  <button class="tab-btn" data-tab="about">Info</button>
</div>

<!-- TAB: SCANNER -->
<section class="tab-panel active" id="tab-scan">
  <div class="card">
    <div class="card-head">
      <h2>WiFi Scanner</h2>
      <div class="btn-row">
        <button class="btn primary" id="btnScan">Scan Sekarang</button>
        <a class="btn ghost" id="btnExport" href="/api/scan.csv" download>Export CSV</a>
      </div>
    </div>
    <p class="hint" id="scanHint">Belum ada data. Tekan "Scan Sekarang".</p>
    <div class="table-wrap">
      <table id="scanTable">
        <thead>
          <tr><th>SSID</th><th>BSSID</th><th>RSSI</th><th>CH</th><th>Auth</th><th>Vendor</th><th>Sinyal</th></tr>
        </thead>
        <tbody></tbody>
      </table>
    </div>
  </div>

  <div class="card">
    <h2>Kepadatan Channel (1-13)</h2>
    <p class="hint">Semakin tinggi bar, semakin ramai channel tsb dipakai AP lain di sekitar. Pilih channel yang rendah untuk AP baru.</p>
    <div class="bars" id="channelBars"></div>
  </div>
</section>

<!-- TAB: CLIENTS -->
<section class="tab-panel" id="tab-clients">
  <div class="card">
    <div class="card-head">
      <h2>Client Terhubung ke Dashboard AP</h2>
      <span class="badge" id="clientCountBadge">0 client</span>
    </div>
    <p class="hint">Menampilkan device yang konek langsung ke SSID "ESP32-Analyzer" (dashboard ini). Untuk melihat client di router WiFi lain, ESP32 tidak punya akses (perlu akses admin router tsb).</p>
    <div class="table-wrap">
      <table id="clientTable">
        <thead><tr><th>MAC Address</th><th>Vendor</th><th>IP</th><th>RSSI</th><th>Terhubung</th></tr></thead>
        <tbody></tbody>
      </table>
    </div>
  </div>
</section>

<!-- TAB: BEACON / MULTI SSID -->
<section class="tab-panel" id="tab-beacon">
  <div class="card">
    <h2>Multi-SSID Broadcaster (maks 15)</h2>
    <p class="hint warn">
      &#9888; Fitur ini mengirim beacon frame 802.11 palsu (SSID spoof) agar
      SSID di bawah tampil di daftar WiFi HP/laptop lain. SSID hasil spoof
      <b>tidak bisa dipakai internetan</b> (bukan AP asli), hanya untuk
      riset / demo cara kerja beacon frame. Gunakan secara bertanggung
      jawab pada jaringan sendiri.
    </p>
    <textarea id="ssidListInput" rows="8" placeholder="Satu SSID per baris, maksimal 15 baris">Free_WiFi_1
Free_WiFi_2
Office_Guest
Cafe_Hotspot
Airport_Free
Hotel_Guest
Library_WiFi
Mall_WiFi
Park_Free_Net
Home_Guest
Test_SSID_1
Test_SSID_2
Demo_AP_A
Demo_AP_B
Analyzer_Fake</textarea>
    <div class="btn-row">
      <button class="btn primary" id="btnBeaconStart">Mulai Broadcast</button>
      <button class="btn danger" id="btnBeaconStop">Stop</button>
    </div>
    <div class="status-box" id="beaconStatus">Status: berhenti</div>
  </div>
</section>

<!-- TAB: SNIFFER -->
<section class="tab-panel" id="tab-sniffer">
  <div class="card">
    <h2>Deauth Detector & Packet Sniffer</h2>
    <p class="hint">
      Mode promiscuous (pasif, hanya mendengarkan) untuk mendeteksi
      frame deauthentication/disassociation di sekitar — indikasi
      kemungkinan ada serangan deauth pada jaringan WiFi di area ini.
    </p>
    <div class="btn-row">
      <button class="btn primary" id="btnSnifferStart">Mulai Monitor</button>
      <button class="btn danger" id="btnSnifferStop">Stop</button>
    </div>
    <div class="grid-stats">
      <div class="stat-box">
        <span class="stat-label">Total Packet</span>
        <span class="stat-value" id="statTotalPacket">0</span>
      </div>
      <div class="stat-box alert-box">
        <span class="stat-label">Deauth/Disassoc Terdeteksi</span>
        <span class="stat-value" id="statDeauth">0</span>
      </div>
      <div class="stat-box">
        <span class="stat-label">Channel Aktif</span>
        <span class="stat-value" id="statChannel">1</span>
      </div>
    </div>
    <p class="hint" id="deauthWarning" style="display:none;color:#ff6b6b;">
      &#9888; Terdeteksi frame deauth/disassoc dalam jumlah signifikan. Ini
      bisa indikasi ada aktivitas deauth attack di sekitar area ini.
    </p>
  </div>
</section>

<!-- TAB: PING -->
<section class="tab-panel" id="tab-ping">
  <div class="card">
    <h2>Ping Tool</h2>
    <p class="hint">Ping dari ESP32 (via koneksi STA / internet jika ESP32 juga konek ke WiFi lain sebagai station).</p>
    <div class="form-row">
      <input type="text" id="pingHost" placeholder="contoh: 8.8.8.8 atau google.com" value="8.8.8.8">
      <input type="number" id="pingCount" min="1" max="20" value="4" style="width:80px;">
      <button class="btn primary" id="btnPing">Ping</button>
    </div>
    <div class="status-box" id="pingResult">Belum ada hasil ping.</div>
  </div>
</section>

<!-- TAB: ABOUT -->
<section class="tab-panel" id="tab-about">
  <div class="card">
    <h2>Informasi Perangkat</h2>
    <table class="kv-table" id="infoTable"></table>
  </div>
  <div class="card">
    <h2>Catatan Fitur</h2>
    <ul class="notes">
      <li><b>Scanner</b>: menampilkan seluruh AP WiFi di sekitar beserta RSSI, channel, enkripsi, dan vendor (dari OUI MAC).</li>
      <li><b>Clients</b>: daftar device yang konek ke AP dashboard ESP32 ini sendiri.</li>
      <li><b>Multi-SSID</b>: broadcast hingga 15 SSID palsu via beacon frame spoofing (bukan AP fungsional).</li>
      <li><b>Deauth/Sniffer</b>: mode promiscuous pasif untuk mendeteksi serangan deauth & menghitung kepadatan paket per channel.</li>
      <li><b>Ping</b>: uji konektivitas ICMP ke host manapun.</li>
    </ul>
  </div>
</section>

<div class="toast" id="toast"></div>

<script src="/app.js"></script>
</body>
</html>
)HTMLDOC";
