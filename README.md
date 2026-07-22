# ESP32 Network Tool

Firmware ESP32 (DevKit V1) dengan web dashboard untuk:
- **WiFi Scan** — daftar SSID, RSSI, channel, BSSID, jenis enkripsi di sekitar.
- **AP Config** — deploy/ubah Access Point ESP32 sendiri (SSID, password, channel, hidden).
- **Network Scan** — ping sweep + port scan ringan (port umum: 21,22,23,25,53,80,110,139,143,443,445,3389,8080) ke semua host di subnet /24 tempat ESP32 berada. Ini fungsi "nmap sederhana"-nya.

> ⚠️ Gunakan hanya untuk jaringan/perangkat milik sendiri atau yang sudah ada izin eksplisit untuk diuji. Scan port ke jaringan orang lain tanpa izin bisa melanggar hukum di banyak negara.

## Cara pakai

1. Flash firmware ke ESP32 DevKit V1 (via USB, atau ambil `firmware.bin` hasil GitHub Actions dan flash pakai `esptool.py`).
2. Setelah boot, ESP32 akan membuat AP dengan SSID default `ESP32-NetTool`, password `12345678`.
3. Konek HP/laptop ke AP tersebut.
4. Buka browser ke `192.168.4.1` (IP default softAP ESP32).
5. Dari dashboard:
   - Tab **WiFi Scan**: klik "Scan WiFi" untuk lihat jaringan sekitar. Bisa juga langsung connect STA ke jaringan rumah/kantor dari sini.
   - Tab **AP Config**: ganti SSID/password/channel AP ESP32 sesuai keinginan.
   - Tab **Network Scan**: scan semua host aktif + port terbuka di subnet tempat ESP32 sedang berada (baik saat masih di AP-nya sendiri, atau setelah connect STA ke jaringan lain).

## Compile via GitHub Actions

Repo ini sudah menyertakan `.github/workflows/build.yml`. Setelah push ke `main` (atau trigger manual lewat tab Actions), workflow akan:
1. Install PlatformIO
2. Build target `esp32doit-devkit-v1`
3. Upload `firmware.bin` sebagai artifact yang bisa didownload dari halaman run Actions

Setelah download, flash manual pakai esptool:
```bash
esptool.py --chip esp32 --port /dev/ttyUSB0 write_flash -z 0x10000 firmware.bin
```
(Alamat offset `0x10000` untuk PlatformIO Arduino framework; sesuaikan bila ada partition scheme custom.)

## Struktur project

```
esp32-net-tool/
├── platformio.ini
├── src/
│   └── main.cpp
├── .github/workflows/build.yml
└── README.md
```

## Catatan teknis / batasan

- Network scan bersifat blocking (server web tidak melayani request lain selama scan berjalan, biasanya 1-3 menit untuk 254 host). Untuk versi lebih responsif bisa dikembangkan pakai AsyncWebServer + task terpisah di core kedua ESP32.
- Deteksi subnet diasumsikan selalu `/24`. Kalau jaringan kamu pakai subnet lain, sesuaikan `handleNetScan()` di `main.cpp`.
- Password AP kurang dari 8 karakter otomatis dianggap "open network" (tanpa password), sesuai batas minimum WPA2.
- Library `ESP32Ping` diambil dari PlatformIO registry (`marian-craciunescu/ESP32Ping`), sudah dideklarasikan di `platformio.ini`.
