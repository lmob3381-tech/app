# ESP32 Network Tool

Firmware ESP32 (DevKit V1) dengan web dashboard bergaya console/terminal untuk:

- **WiFi Scan** — daftar SSID, RSSI (dengan indikator bar sinyal), channel, BSSID, jenis enkripsi di sekitar. Bisa langsung connect STA dari sini.
- **AP Clients** — daftar perangkat yang terhubung ke hotspot ESP32 sendiri: MAC address, IP, dan tebakan vendor (dari tabel OUI offline).
- **Network Scan** — ping sweep + port scan ringan (port umum: 21,22,23,25,53,80,110,139,143,443,445,3389,8080) ke semua host di subnet /24, plus latency dan best-effort MAC/vendor lookup lewat ARP cache. Ini fungsi "nmap sederhana"-nya. Hasil bisa di-export ke CSV.
- **AP Config** — deploy/ubah Access Point ESP32 sendiri (SSID, password, channel, hidden).
- **Status bar live** — uptime, free heap, jumlah klien AP, dan mini "oscilloscope" RSSI trend untuk koneksi STA.
- **Activity log & toast notification** di UI, semua client-side.

> ⚠️ Gunakan hanya untuk jaringan/perangkat milik sendiri atau yang sudah ada izin eksplisit untuk diuji. Scan port ke jaringan orang lain tanpa izin bisa melanggar hukum di banyak negara.

## Cara pakai

1. Flash firmware ke ESP32 DevKit V1 (lihat bagian *Compile & Flash* di bawah).
2. Setelah boot, ESP32 membuat AP dengan SSID default `ESP32-NetTool`, password `12345678`.
3. Konek HP/laptop ke AP tersebut.
4. Buka browser ke `192.168.4.1` (IP default softAP ESP32).
5. Dari dashboard:
   - **WiFi Scan**: klik "Scan" untuk lihat jaringan sekitar, atau connect ke jaringan rumah/kantor.
   - **AP Clients**: lihat siapa saja yang sedang konek ke hotspot ESP32 ini.
   - **Network Scan**: scan semua host aktif + port terbuka + MAC/vendor di subnet tempat ESP32 berada (baik di AP sendiri atau setelah connect STA ke jaringan lain).
   - **AP Config**: ganti SSID/password/channel AP ESP32.

## Compile & Flash via GitHub Actions

Repo ini sudah menyertakan `.github/workflows/build.yml`. Setelah push ke `main` (atau trigger manual lewat tab Actions), workflow akan:
1. Install PlatformIO
2. Build target `esp32doit-devkit-v1`
3. Ambil **semua** komponen yang dibutuhkan untuk flash dari nol: `bootloader.bin`, `partitions.bin`, `boot_app0.bin`, `firmware.bin`
4. Gabungkan semuanya jadi satu file `merged-firmware.bin`
5. Upload dua artifact:
   - **firmware-parts** — 4 file terpisah
   - **firmware-merged** — satu file `merged-firmware.bin`, paling gampang buat di-flash

### Opsi A — flash file merged (paling gampang)
```bash
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 460800 write_flash 0x0 merged-firmware.bin
```

### Opsi B — flash 4 file terpisah manual
```bash
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 460800 write_flash \
  0x1000  bootloader.bin \
  0x8000  partitions.bin \
  0xe000  boot_app0.bin \
  0x10000 firmware.bin
```

Kalau ESP32 tidak masuk mode download otomatis, tahan tombol **BOOT** saat proses `Connecting...` di esptool.

Offset di atas berlaku untuk partition scheme default PlatformIO Arduino. Kalau kamu ganti `board_build.partitions` custom di `platformio.ini`, sesuaikan offset partition table & app-nya.

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

- **Network scan bersifat blocking** — server web tidak melayani request lain selama scan berjalan, biasanya 1-3 menit untuk 254 host.
- **Deteksi subnet diasumsikan `/24`.** Kalau jaringan kamu pakai subnet lain, sesuaikan `handleNetScan()` di `main.cpp`.
- **MAC/vendor di Network Scan bersifat best-effort** — diambil dari ARP cache lwIP (`etharp_find_addr`), hanya terisi untuk host yang sudah pernah "ditemui" ESP32 (biasanya otomatis terisi karena proses ping/connect memicu ARP resolution). Kadang bisa kosong tergantung timing.
- **Tabel vendor OUI terbatas** (~40 entri populer: Apple, Google, Samsung, Xiaomi, Espressif, dst) dan disimpan offline di firmware — bukan database lengkap seperti IEEE OUI resmi.
- **AP Clients** memakai `esp_wifi_ap_get_sta_list()` + `esp_netif_get_sta_list()` (API resmi ESP-IDF) untuk MAC dan IP dari perangkat yang connect ke AP ESP32 sendiri.
- Password AP kurang dari 8 karakter otomatis dianggap "open network" (tanpa password), sesuai batas minimum WPA2.
- Library `ESP32Ping` diambil dari PlatformIO registry (`marian-craciunescu/ESP32Ping`), sudah dideklarasikan di `platformio.ini`.
- API `esp_netif_get_sta_list` dan `etharp_find_addr` sudah stabil di ESP-IDF yang dipakai arduino-esp32 versi umum, tapi kalau muncul error compile terkait signature fungsi ini, cek versi `platform = espressif32` di `platformio.ini` dan sesuaikan (biasanya cukup pin ke versi platform yang lebih baru/lama).
