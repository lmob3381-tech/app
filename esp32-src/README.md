# ESP32 Captive Splash Portal

WiFi Access Point ESP32 dengan halaman splash (bukan login kredensial —
cuma tombol "Lanjut") yang memutar audio di HP user, lalu memberi akses
internet asli lewat NAT dari WiFi source lain.

## Cara Kerja Singkat

```
[Internet] --- [WiFi Source/Rumah] --- (STA) [ESP32] (AP) --- [HP User]
                                          |
                                     NAT/IP forward
```

1. ESP32 connect sebagai **STA** ke WiFi rumah/kantor Anda (sumber internet).
2. ESP32 juga jadi **AP** sendiri untuk device lain connect.
3. Saat HP connect ke AP ESP32, otomatis muncul popup captive portal
   (splash page) berisi audio player + tombol "Lanjut".
4. Setelah "Lanjut" ditekan, HP diarahkan keluar dari captive portal
   dan browsing normal — trafiknya di-NAT oleh ESP32 ke WiFi source.

## Setup

### 1. Edit konfigurasi
Buka `include/config.h`, isi:
- `AP_SSID` / `AP_PASSWORD` → nama WiFi yang dipancarkan ESP32
- `STA_SSID` / `STA_PASSWORD` → WiFi source Anda yang punya internet

### 2. Siapkan file audio
Taruh file MP3 di `data/welcome.mp3` (nama harus sama dengan
`AUDIO_FILENAME` di config.h).

### 3. Build via GitHub Actions
Push ke repo ini, workflow `.github/workflows/build.yml` otomatis jalan
dan menghasilkan artifact `esp32-firmware` berisi:
- `firmware.bin`
- `littlefs.bin` (berisi splash page assets/audio)
- `bootloader.bin`
- `partitions.bin`

Download artifact itu dari tab **Actions** di GitHub setelah build selesai.

### 4. Flash ke ESP32 (dari laptop, GitHub Actions TIDAK bisa akses device fisik Anda)

Install esptool:
```bash
pip install esptool
```

Flash firmware + bootloader + partition table:
```bash
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 460800 write_flash \
  0x1000  bootloader.bin \
  0x8000  partitions.bin \
  0x10000 firmware.bin
```

Flash filesystem (LittleFS) — cek offset sesuai partition table Anda,
biasanya di sekitar `0x290000` untuk partisi default 4MB, atau lihat
output build untuk offset pastinya:
```bash
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 460800 write_flash \
  0x290000 littlefs.bin
```

> Ganti `/dev/ttyUSB0` sesuai port serial ESP32 Anda
> (Windows: `COM3` dst, Mac: `/dev/cu.usbserial-XXXX`).

## Catatan Teknis

- **NAT/IP forwarding** memakai `lwip_napt.h` bawaan Arduino-ESP32 core.
  Ini di-pin lewat `platform = espressif32@6.5.0` di `platformio.ini`
  agar API-nya tersedia. Kalau upgrade versi platform, cek dulu apakah
  `lwip/lwip_napt.h` masih ada.
- Splash page **bukan sistem login sungguhan** — tidak ada database user,
  cuma landing page + tombol lanjut, sesuai kebutuhan.
- Deteksi captive portal sudah mencakup endpoint umum Android, iOS/macOS,
  Windows, dan Firefox.
- Jika WiFi source terputus, ESP32 otomatis mencoba reconnect di `loop()`.

## Lisensi
Bebas dipakai/dimodifikasi untuk keperluan pribadi Anda.
