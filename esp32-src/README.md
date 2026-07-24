# ESP32 Captive Splash Portal

WiFi Access Point ESP32 dengan halaman splash: device connect → popup
otomatis muncul → user klik tombol "Lanjut" → audio MP3 bunyi di HP user.

## Cara Kerja Singkat

```
[HP User] --- connect WiFi --- [ESP32 sebagai Access Point]
                                        |
                          popup splash page otomatis muncul
                                        |
                         user klik tombol "Lanjut" -> MP3 bunyi
```

Ini **bukan sistem login** (tidak ada cek username/password), murni
splash/landing page dengan tombol.

## Setup

### 1. Edit konfigurasi
Buka `include/config.h`, isi:
- `AP_SSID` / `AP_PASSWORD` → nama WiFi yang dipancarkan ESP32

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

### 4. Flash ke ESP32 (dari laptop — GitHub Actions TIDAK bisa akses device fisik Anda)

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

## Kenapa Tidak Ada Fitur "Bagikan Internet dari WiFi Lain" (NAT)?

Awalnya proyek ini mencoba fitur NAT (supaya device yang connect ke
ESP32 bisa internetan lewat WiFi rumah/kantor yang di-relay ESP32).
Ternyata ini **gagal di-compile** dengan error:

```
undefined reference to `ip_napt_enable'
```

Setelah ditelusuri, penyebabnya adalah keterbatasan fundamental:
Arduino framework untuk ESP32 di PlatformIO menggunakan library **lwip
versi precompiled** dari Espressif, dan fungsi NAT (`ip_napt_enable`)
memang ada di header tapi implementasinya **tidak ikut ter-compile** ke
dalam library precompiled itu. Flag `-D...` di `build_flags` tidak bisa
memperbaiki ini karena masalahnya ada di binary library, bukan di kode
kita.

Fitur NAT semacam ini **hanya bisa jalan dengan framework ESP-IDF asli**
(bukan Arduino), seperti yang dipakai proyek referensi
[esp32_nat_router](https://github.com/martin-ger/esp32_nat_router).
Itu butuh setup & alur kerja yang jauh lebih rumit (menconfig ESP-IDF,
dll) dan di luar cakupan proyek splash-portal sederhana ini.

**Kalau suatu saat butuh fitur internet-sharing beneran**, opsinya:
1. Rewrite total pakai ESP-IDF framework, atau
2. Pakai firmware `esp32_nat_router` yang sudah jadi & dimodifikasi untuk
   menambahkan splash page ini di dalamnya, atau
3. Gunakan router fisik terpisah untuk urusan internet-sharing, dan biarkan
   ESP32 ini hanya menangani splash page saja.

## Catatan Teknis

- Splash page **bukan sistem login sungguhan** — tidak ada database user,
  cuma landing page + tombol.
- Audio diputar di **browser HP user** (bukan speaker fisik ESP32) via
  tag `<audio>`, dipicu oleh klik tombol (bukan `autoplay`) karena
  kebanyakan browser mobile memblokir autoplay tanpa interaksi user.
- Deteksi captive portal mencakup endpoint umum Android, iOS/macOS,
  Windows, dan Firefox.

## Lisensi
Bebas dipakai/dimodifikasi untuk keperluan pribadi Anda.
