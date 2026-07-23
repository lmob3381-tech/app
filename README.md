# ESP32 WiFi Analyzer & Toolkit

Firmware WiFi Analyzer lengkap untuk **ESP32 DevKit V1 (WROOM-32)**, dikontrol
lewat web dashboard mobile-friendly. Dibangun dengan PlatformIO + Arduino
framework, siap di-build otomatis via **GitHub Actions**.

## Fitur

| Fitur | Keterangan |
|---|---|
| **WiFi Scanner** | Scan semua AP sekitar: SSID, BSSID, RSSI, channel, jenis enkripsi, vendor (OUI lookup), hidden SSID |
| **Client Monitor** | Lihat device yang konek ke SoftAP dashboard ESP32 (MAC, IP, RSSI, vendor, lama konek) |
| **Multi-SSID Broadcast** | Broadcast hingga **15 SSID** sekaligus via beacon frame spoofing (lihat catatan di bawah) |
| **Deauth Detector** | Mode promiscuous pasif mendeteksi frame deauth/disassoc (indikasi serangan WiFi) |
| **Channel Analyzer** | Grafik kepadatan AP per channel 1-13, bantu pilih channel kosong |
| **Ping Tool** | ICMP ping ke IP/hostname manapun dari web UI |
| **Export CSV** | Download hasil scan sebagai file CSV |
| **Live Dashboard** | Update realtime via WebSocket, dark theme, responsive mobile |

## ⚠️ Catatan penting: soal "15 SSID"

ESP32 (chip asli, bukan S2/S3/C3) hanya punya **1 radio WiFi**, sehingga hanya
bisa punya **1 SoftAP fungsional** yang beneran bisa dikonek dan dapat IP.

Untuk menampilkan banyak SSID sekaligus, firmware ini memakai teknik
**beacon frame spoofing**: ESP32 mengirim paket beacon 802.11 mentah dengan
SSID & MAC berbeda secara bergantian sangat cepat. Device lain (HP/laptop)
akan **melihat** SSID-SSID itu muncul di daftar WiFi, tapi:

- SSID hasil spoof **tidak bisa dipakai internetan** (bukan AP asli)
- Hanya SSID utama (`ESP32-Analyzer`, default password `analyzer123`) yang
  merupakan AP sungguhan tempat dashboard ini berjalan

Gunakan fitur ini untuk riset/edukasi jaringan sendiri, bukan untuk
mengganggu jaringan orang lain.

## Struktur Project

```
esp32_wifi_analyzer/
├── platformio.ini          # konfigurasi board & library
├── src/
│   ├── main.cpp             # logic utama, web server, routing API
│   ├── beacon.cpp/.h        # modul beacon frame spoofing (multi-SSID)
│   ├── sniffer.cpp/.h       # modul promiscuous sniffer & deauth detector
│   ├── pingtool.cpp/.h      # modul ping ICMP
│   ├── webpages.h           # header deklarasi asset web
│   ├── webpages_html.cpp    # dashboard HTML (embedded PROGMEM)
│   ├── webpages_css.cpp     # dashboard CSS (embedded PROGMEM)
│   └── webpages_js.cpp      # dashboard JS (embedded PROGMEM)
└── .github/workflows/
    └── build.yml             # GitHub Actions: auto build firmware.bin
```

## Cara pakai (setelah build via GitHub Actions)

1. Push project ini ke repo GitHub kamu.
2. GitHub Actions otomatis jalan (lihat tab **Actions**), hasil build
   berupa `firmware.bin`, `bootloader.bin`, `partitions.bin` bisa didownload
   di bagian **Artifacts** pada run yang sukses.
3. Flash ke ESP32 pakai salah satu cara:
   - **esptool.py** (paling umum):
     ```
     esptool.py --chip esp32 --port COMx --baud 921600 write_flash \
       0x1000  bootloader.bin \
       0x8000  partitions.bin \
       0x10000 firmware.bin
     ```
   - Atau clone repo dan flash langsung via PlatformIO:
     ```
     pio run -t upload
     ```
4. Setelah nyala, cari WiFi bernama **`ESP32-Analyzer`** dari HP/laptop,
   connect dengan password **`analyzer123`**.
5. Buka browser ke `http://192.168.4.1` — dashboard akan terbuka otomatis
   (captive portal) atau buka manual di alamat tsb.

## Mengubah SSID/Password Dashboard

Edit di `src/main.cpp`:
```cpp
#define SSID_MAIN "ESP32-Analyzer"
#define PASS_MAIN "analyzer123"
```

## Build Lokal (opsional, kalau tidak lewat GitHub Actions)

Butuh [PlatformIO](https://platformio.org/) terpasang:
```bash
pip install platformio
cd esp32_wifi_analyzer
pio run -e esp32dev        # build
pio run -e esp32dev -t upload   # build + flash (ESP32 harus tercolok USB)
pio device monitor              # lihat serial log
```

## Kebutuhan Hardware

- ESP32 DevKit V1 (WROOM-32) — chip tunggal, tanpa modul tambahan
- Kabel USB (data, bukan cuma charging)
- (Opsional) OLED SSD1306 bisa ditambahkan belakangan untuk status offline,
  kode sudah punya hook comment di `main.cpp` bagian FITUR

## Library yang dipakai

- [ESPAsyncWebServer](https://github.com/me-no-dev/ESPAsyncWebServer) — web server + WebSocket async
- [AsyncTCP](https://github.com/me-no-dev/AsyncTCP) — dependency ESPAsyncWebServer
- [ArduinoJson](https://arduinojson.org/) v6 — parsing & serialize JSON API

Semua sudah didefinisikan di `platformio.ini`, otomatis ter-download saat
build (baik lokal maupun GitHub Actions).

## Troubleshooting

| Masalah | Solusi |
|---|---|
| Build gagal di Actions, error `tcpip_adapter` not found | Pastikan `platform = espressif32@6.9.0` di `platformio.ini` tidak berubah ke versi lebih baru (core v3.x menghapus API ini) |
| Tidak bisa akses `192.168.4.1` | Pastikan HP benar-benar connect ke SSID `ESP32-Analyzer`, cek IP yang didapat harus `192.168.4.x` |
| Fitur Ping gagal terus | ESP32 perlu koneksi internet aktif sebagai STA (opsional, bisa ditambahkan `WiFi.begin(ssid, pass)` untuk konek ke WiFi rumah sekaligus jadi AP) |
| Multi-SSID tidak muncul di HP | Beberapa HP cache hasil scan WiFi, coba refresh/scan ulang WiFi list di HP |
