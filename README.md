# ESP32 NetScan Console

Firmware ESP32 DevKit (V1 compatible) untuk:
- **Scan WiFi sekitar** (SSID, channel, RSSI, enkripsi, BSSID) — semacam "airodump" ringan.
- **Scan host di jaringan** (mini-nmap: ping sweep /24 + cek port umum: 21,22,23,25,53,80,139,443,445,8080).
- **Deploy Access Point sendiri** (SSID/password custom, tersimpan permanen di flash).
- **Web control panel** yang di-serve langsung dari ESP32, bisa diakses dari HP/laptop.

> Dashboard HTML/CSS/JS di-**embed langsung ke dalam firmware.bin** (disimpan di flash program via `PROGMEM`), jadi **tidak perlu flash file filesystem terpisah** (`littlefs.bin`). Cukup 3 file: `bootloader.bin`, `partitions.bin`, `firmware.bin`.

⚠️ **Etika & legal**: hanya gunakan pada jaringan/perangkat milik sendiri atau yang sudah diberi izin eksplisit. Scanning jaringan orang lain tanpa izin bisa melanggar hukum.

## Cara pakai (setelah firmware ter-flash)

1. Nyalakan ESP32. Secara default ia langsung jadi Access Point:
   - SSID: `ESP32-NetScan`
   - Password: `netscan123`
2. Sambungkan HP/laptop ke WiFi tersebut.
3. Buka browser ke `http://192.168.4.1` (biasanya otomatis redirect / captive portal).
4. Dari dashboard:
   - **Scan SSID** — lihat semua WiFi di sekitar ESP32.
   - **Konek ke jaringan lain (mode STA)** — masukkan SSID+password jaringan target, ESP32 akan pindah jadi client jaringan itu.
   - **Host Scan** — setelah mode STA aktif dan konek berhasil, jalankan ping sweep ke seluruh subnet untuk menemukan perangkat aktif + port terbuka.
   - **Ganti identitas AP** — ubah SSID/password Access Point milik ESP32 sendiri, tersimpan permanen (NVS/Preferences).
   - **Kembali ke mode AP** kapan saja dari tombol di panel.

## Build lokal (PlatformIO)

```bash
pip install platformio
pio run -e esp32dev          # compile firmware (HTML sudah ter-embed di dalamnya)
pio run -e esp32dev -t upload # flash ke board via USB
```

## Build otomatis via GitHub Actions

Push ke branch `main` akan otomatis men-trigger `.github/workflows/build.yml`, yang:
1. Meng-compile firmware (`firmware.bin`, HTML dashboard sudah ter-embed di dalamnya).
2. Mengunggah `firmware.bin`, `bootloader.bin`, dan `partitions.bin` sebagai artifact — bisa diunduh dari tab **Actions** di repo, lalu di-flash manual dengan `esptool.py`, app flash tool Android, atau PlatformIO/Arduino IDE.

### Flash manual dari hasil build Actions (pakai esptool)

```bash
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 115200 write_flash \
  0x1000  bootloader.bin \
  0x8000  partitions.bin \
  0x10000 firmware.bin
```

Hanya 3 file ini yang perlu diflash — tidak ada lagi `littlefs.bin` / offset `0x290000`.

## Struktur project

```
esp32-netscan/
├── platformio.ini          # konfigurasi board & library
├── src/main.cpp             # firmware utama (WiFi scan, host scan, AP, web server)
├── src/index_html.h         # dashboard web, embedded sebagai PROGMEM string
└── .github/workflows/build.yml
```

> Kalau mau edit tampilan dashboard: edit isi HTML di dalam `src/index_html.h` (di antara `R"RAWHTML( ... )RAWHTML"`), lalu compile ulang. Tidak ada file `.html` terpisah lagi.

## Library yang dipakai

- `WiFi.h`, `WebServer.h`, `DNSServer.h`, `Preferences.h` — bawaan ESP32 Arduino core.
- `ESP32Ping` — untuk ping sweep host scan.
- `ArduinoJson` (`bblanchon/ArduinoJson`) — sudah dideklarasikan lengkap di `platformio.ini` dengan owner prefix supaya PlatformIO bisa resolve dari registry.

## Catatan teknis

- Host scan bersifat **blocking** (satu request HTTP ditahan sampai selesai) — untuk subnet /24 penuh bisa makan waktu ~1-2 menit tergantung berapa banyak host aktif dan ping timeout. Cukup untuk jaringan rumah/kantor kecil.
- ESP32 hanya bisa STA **atau** AP+ping-sweep sekaligus dengan keterbatasan RF (WiFi radio tunggal), jadi saat mode STA aktif, AP untuk client lain otomatis nonaktif — itu sebabnya panel menampilkan peringatan untuk reconnect ke SSID ESP32 setelah kembali ke mode AP.
- Konfigurasi AP & kredensial STA terakhir disimpan di NVS (`Preferences`) sehingga bertahan setelah reboot.
