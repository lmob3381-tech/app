# Kontrak API — Firmware ESP32 DevKit V1 untuk WiFi Home Monitor

Dokumen ini adalah SPESIFIKASI WAJIB untuk siapa pun (manusia atau AI) yang akan
menulis firmware ESP32. Aplikasi Android (`WiFi Home Monitor`) di repo ini SUDAH
DIBUAT dan mengasumsikan ESP32 mengikuti kontrak di bawah ini persis. Kalau
firmware menyimpang dari kontrak ini, app Android harus ikut diubah — jangan
ubah sepihak tanpa update kode Kotlin juga.

---

## 1. Ringkasan Peran ESP32

ESP32 DevKit V1 berfungsi sebagai **network probe** yang:
1. Terhubung ke WiFi rumah yang sama dengan HP (mode STA, bukan Access Point).
2. Melakukan **ARP scan** ke subnet lokal untuk menemukan semua perangkat yang
   terhubung (IP, MAC, hostname jika bisa didapat via mDNS/NetBIOS, vendor dari
   OUI MAC).
3. Menyediakan **REST API (HTTP)** di port 80 dan **WebSocket** di port 81 agar
   app Android bisa mengambil data device list & status ESP32, serta menerima
   update realtime.
4. (Opsional, prioritas rendah) Bisa melakukan ping ke host tertentu dari sisi
   jaringan lokal dan melaporkan hasilnya — berguna untuk cek latency LAN,
   BUKAN pengganti ping dari HP (HP sudah ping sendiri via binary ping Android).

ESP32 TIDAK bertanggung jawab atas: speedtest (dilakukan app langsung ke
internet), remote TV, atau screenshot TV (keduanya langsung dari app ke TV).

---

## 2. Environment & Library yang Disarankan

- Platform: **Arduino framework untuk ESP32** (bukan ESP-IDF murni, supaya
  lebih cepat dikembangkan) — atau PlatformIO dengan board `esp32dev`.
- Library yang disarankan:
  - `WiFi.h` (bawaan ESP32 core)
  - `WebServer.h` atau `ESPAsyncWebServer` (untuk REST API) — **ESPAsyncWebServer
    lebih disarankan** karena non-blocking, penting karena ESP32 juga harus
    handle WebSocket + ARP scan bersamaan.
  - `AsyncTCP.h` (dependency ESPAsyncWebServer)
  - `ArduinoJson.h` (untuk serialisasi JSON, versi 6.x atau 7.x)
  - `ESPmDNS.h` (opsional, untuk resolve hostname perangkat lain via mDNS)
  - Untuk ARP scan: bisa pakai raw socket ping sweep + baca ARP table ESP32
    sendiri, ATAU library seperti `ESP32Ping` dikombinasikan dengan pembacaan
    tabel ARP internal lwIP (`etharp_find_addr` dari lwIP, agak advanced) —
    ATAU cara paling simpel: ping seluruh subnet (misal /24 = 254 host) satu
    per satu, lalu setelah ping, ARP entry otomatis terisi dan bisa dibaca.

---

## 3. REST API — Wajib Diimplementasikan

Base URL: `http://<ip-esp32>/` — HTTP polos (bukan HTTPS), port 80.

### 3.1. `GET /status`

Mengembalikan status kesehatan ESP32 itu sendiri.

**Response 200 JSON:**
```json
{
  "device_name": "ESP32-NetProbe",
  "firmware_version": "1.0.0",
  "uptime_sec": 3600,
  "free_heap": 180000,
  "wifi_rssi": -55,
  "local_ip": "192.168.1.50",
  "scan_in_progress": false,
  "devices_found": 12
}
```

Field wajib ada semua (pakai nama field PERSIS seperti di atas, snake_case).
Kelas Kotlin yang membaca ini: `Esp32Status.kt`.

### 3.2. `GET /devices`

Mengembalikan daftar perangkat hasil scan TERAKHIR (cached, tidak memicu scan
baru — supaya endpoint ini cepat dan bisa dipanggil sering).

**Response 200 JSON (array):**
```json
[
  {
    "ip": "192.168.1.10",
    "mac": "AA:BB:CC:DD:EE:FF",
    "hostname": "android-abc123",
    "vendor": "Samsung Electronics",
    "last_seen": 1721500000,
    "is_online": true
  },
  {
    "ip": "192.168.1.11",
    "mac": "11:22:33:44:55:66",
    "hostname": null,
    "vendor": "TP-Link",
    "last_seen": 1721499800,
    "is_online": false
  }
]
```

Catatan field:
- `mac`: format harus UPPERCASE dengan pemisah `:` (contoh `AA:BB:CC:DD:EE:FF`).
- `hostname`: boleh `null`/kosong kalau tidak berhasil di-resolve (misal via
  mDNS query `_device-info._tcp` atau NetBIOS). Kalau tidak sanggup implementasi
  hostname resolution, kirim `null` saja — app akan fallback ke vendor/IP.
- `vendor`: hasil lookup OUI (24-bit pertama MAC) ke nama vendor. Bisa pakai
  tabel OUI lokal ringkas yang di-embed ke firmware (tidak perlu lengkap,
  cukup vendor umum: Samsung, Xiaomi, Apple, TP-Link, Espressif, dll), atau
  kirim `null` kalau tidak ketemu.
- `last_seen`: UNIX epoch (detik) kapan device terakhir merespons ping/ARP.
  ESP32 perlu tahu waktu sekarang — gunakan NTP sync (`configTime()`) saat boot.
- `is_online`: `true` kalau device merespons di scan TERAKHIR, `false` kalau
  device tercatat sebelumnya tapi sudah tidak merespons di scan terbaru
  (jangan langsung dihapus dari list, biar histori terlihat).

### 3.3. `POST /scan`

Memicu ARP/ping-sweep scan baru secara ASYNC (tidak menunggu selesai sebelum
merespons — karena scan subnet /24 bisa makan waktu beberapa detik).

**Response 200 JSON:**
```json
{ "status": "scan_started" }
```

Setelah scan selesai, ESP32 harus:
1. Update data yang dikembalikan `GET /devices`.
2. Broadcast pesan WebSocket `scan_finished` (lihat bagian WebSocket).

### 3.4. `POST /ping?host=<ip>&count=<n>` (opsional, prioritas rendah)

Query param: `host` (wajib), `count` (opsional, default 4).

**Response 200 JSON:**
```json
{
  "host": "192.168.1.1",
  "sent": 4,
  "received": 4,
  "min_ms": 1.2,
  "avg_ms": 2.5,
  "max_ms": 4.1,
  "packet_loss_percent": 0.0
}
```

Boleh dilewati dulu di versi pertama firmware kalau waktu terbatas — app
Android tidak bergantung keras pada endpoint ini (dipakai sebagai fitur
tambahan, bukan inti).

---

## 4. WebSocket API — untuk Update Realtime

Endpoint: `ws://<ip-esp32>:81/ws` (port **81**, path `/ws`).

Kenapa port terpisah dari HTTP (80)? Supaya lebih mudah dipisah kalau pakai
`ESPAsyncWebServer` untuk HTTP dan library WebSocket terpisah (misal
`arduinoWebSockets` oleh Links2004) untuk port 81. Kalau pakai
`ESPAsyncWebServer` yang sudah include `AsyncWebSocket`, boleh saja disatukan
ke port 80 dengan path `/ws` — **TAPI kalau begitu, app Android HARUS diberi
tahu / kode Kotlin di `AppRepository.kt` fungsi `connectToEsp32Realtime()`
HARUS diubah untuk match**. Default asumsi app saat ini: port 81 terpisah.

### 4.1. Pesan dari ESP32 ke App (server → client)

Setiap pesan adalah JSON dengan field `type`:

**Saat ada update device list (kirim setiap kali ada perubahan, atau tiap
interval polling internal ESP32 misal 10 detik):**
```json
{
  "type": "device_list",
  "devices": [ /* array NetworkDevice sama seperti GET /devices */ ]
}
```

**Saat scan mulai:**
```json
{ "type": "scan_started" }
```

**Saat scan selesai:**
```json
{ "type": "scan_finished", "count": 12 }
```

### 4.2. Pesan dari App ke ESP32 (client → server)

Untuk versi pertama, app TIDAK mengirim pesan lewat WebSocket (hanya
mendengarkan). Trigger scan tetap lewat `POST /scan` di HTTP. Ini
menyederhanakan firmware — WebSocket cukup satu arah (broadcast) dulu.

---

## 5. Konfigurasi WiFi ESP32 (Provisioning)

Firmware perlu cara agar ESP32 bisa disambungkan ke WiFi rumah TANPA hardcode
SSID/password di kode (supaya reusable). Rekomendasi:

- Gunakan library **WiFiManager** (oleh tzapu) — saat ESP32 pertama kali boot
  dan belum ada kredensial WiFi tersimpan, ESP32 otomatis membuat Access Point
  sementara (misal SSID `ESP32-Setup`), user connect ke situ dari HP, buka
  `192.168.4.1` di browser, pilih WiFi rumah & masukkan password. Setelah itu
  ESP32 auto-connect ke WiFi itu di boot berikutnya (kredensial tersimpan di
  flash/NVS).
- Setelah connect ke WiFi rumah, catat IP yang didapat (biasanya via DHCP) dan
  tampilkan di Serial Monitor untuk keperluan setup awal (user perlu tahu IP
  ini untuk dimasukkan ke Settings di app Android). Pertimbangkan juga pasang
  **mDNS** (`ESPmDNS.h`, hostname misal `wifihome-probe.local`) supaya user
  bisa pakai `http://wifihome-probe.local/` sebagai alternatif dari IP mentah
  yang bisa berubah — tapi app Android saat ini pakai IP mentah, jadi mDNS
  sifatnya nice-to-have, bukan wajib untuk versi pertama.

---

## 6. Hal yang PENTING Diperhatikan

1. **CORS**: karena app Android native (bukan web), CORS tidak relevan — tidak
   perlu header `Access-Control-Allow-Origin`.
2. **Content-Type**: semua response REST API harus `Content-Type: application/json`.
3. **Timeout**: app Android men-timeout request ke ESP32 setelah ~10 detik
   (lihat `RetrofitClient.kt`). Pastikan endpoint `GET /status` dan
   `GET /devices` merespons cepat (<1 detik idealnya, karena data di-cache,
   bukan realtime scan).
4. **Ukuran subnet**: asumsikan subnet rumah tipikal `/24` (254 host). Kalau
   mau lebih hemat resource ESP32 (RAM terbatas ~300KB free heap), boleh batasi
   jumlah device yang disimpan (misal maksimal 50 entry, cukup untuk rumah).
5. **Stabilitas**: ESP32 harus tetap responsif walau lagi scan — pakai
   task/async, JANGAN blocking delay yang bikin HTTP server macet total saat
   scan berjalan (makanya direkomendasikan ESPAsyncWebServer).
6. **Versi firmware**: field `firmware_version` di `/status` sebaiknya di-bump
   tiap kali kontrak berubah, supaya app bisa (nantinya) mendeteksi
   ketidakcocokan versi.

---

## 7. Checklist Implementasi untuk AI/Dev Berikutnya

- [ ] Setup WiFiManager untuk provisioning WiFi rumah tanpa hardcode
- [ ] Sinkronisasi waktu via NTP (untuk field `last_seen`)
- [ ] Implementasi ARP/ping-sweep scan ke subnet lokal
- [ ] Tabel OUI ringkas untuk resolve `vendor` dari MAC address
- [ ] (Opsional) Resolve `hostname` via mDNS/NetBIOS query
- [ ] Endpoint `GET /status`
- [ ] Endpoint `GET /devices`
- [ ] Endpoint `POST /scan` (async, tidak blocking)
- [ ] (Opsional) Endpoint `POST /ping`
- [ ] WebSocket server di port 81, path `/ws`, broadcast `device_list`,
      `scan_started`, `scan_finished`
- [ ] Testing: pastikan JSON yang dihasilkan match PERSIS dengan contoh di
      dokumen ini (nama field, tipe data, format MAC address)
- [ ] Dokumentasikan IP ESP32 & cara setup di README firmware terpisah

---

## 8. Referensi Kode Android yang Relevan (untuk dicocokkan)

- `app/src/main/java/com/localnet/wifihome/data/model/NetworkDevice.kt`
- `app/src/main/java/com/localnet/wifihome/data/model/Esp32Status.kt`
- `app/src/main/java/com/localnet/wifihome/data/network/Esp32Api.kt`
- `app/src/main/java/com/localnet/wifihome/data/network/Esp32WebSocketClient.kt`
- `app/src/main/java/com/localnet/wifihome/data/AppRepository.kt`
