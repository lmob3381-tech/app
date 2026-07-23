# ESP32 BLE Scanner

Scan perangkat Bluetooth Low Energy (BLE) di sekitar menggunakan ESP32 DevKit,
tanpa memerlukan komponen tambahan apa pun (memanfaatkan modul BLE bawaan ESP32).

## Struktur Project

```
esp32-ble-scanner/
├── platformio.ini              # konfigurasi board & framework
├── src/
│   └── main.cpp                 # source code utama
└── .github/workflows/build.yml  # workflow build otomatis via GitHub Actions
```

## Build via GitHub Actions

1. Push repo ini ke GitHub (branch `main` atau `master`).
2. Buka tab **Actions** di repo — workflow "Build ESP32 Firmware" akan otomatis
   jalan setiap ada push/PR, atau bisa dipicu manual lewat tombol
   **Run workflow** (workflow_dispatch).
3. Setelah build sukses, unduh hasilnya di bagian **Artifacts**
   pada halaman run tersebut — berisi `firmware.bin` dan `firmware.elf`.

## Flashing firmware.bin ke ESP32

Setelah unduh artifact, flash manual pakai `esptool`:

```bash
pip install esptool
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 921600 write_flash -z 0x10000 firmware.bin
```

Sesuaikan `/dev/ttyUSB0` dengan port serial ESP32 di komputer kamu
(di Windows biasanya `COM3`, `COM4`, dst).

> Catatan: GitHub Actions hanya melakukan **compile**, bukan upload ke board.
> Proses upload/flash tetap harus dilakukan di komputer lokal karena ESP32
> perlu terhubung fisik lewat USB.

## Build & upload lokal (opsional, kalau ada PlatformIO di komputer)

```bash
pio run --target upload
pio device monitor
```

## Cara Kerja

- Menggunakan library `BLEDevice` bawaan Arduino core ESP32 (tidak perlu
  install library tambahan di `platformio.ini`).
- Scan aktif (`setActiveScan(true)`) setiap 5 detik, lalu menampilkan hasil
  (alamat MAC, nama device jika ada, RSSI, TX power) ke Serial Monitor
  (baud rate `115200`).
