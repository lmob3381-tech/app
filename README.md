# WiFi Home Monitor — Firmware ESP32 DevKit V1

Firmware ini mengimplementasikan kontrak API di `docs/api-contract.md` (kalau
kamu taruh dokumen kontrak di repo, sebaiknya simpan di path itu) untuk
aplikasi Android **WiFi Home Monitor**.

Project ini pakai **PlatformIO** (bukan Arduino IDE `.ino`), supaya semua
library ter-resolve otomatis dan bisa di-compile lewat GitHub Actions tanpa
install apa pun di komputer/HP.

---

## Struktur project

```
wifihome-esp32/
├── platformio.ini              # konfigurasi board + library
├── src/
│   ├── main.cpp                 # firmware utama
│   └── oui_table.h               # tabel OUI vendor MAC
└── .github/workflows/build.yml   # compile otomatis via GitHub Actions
```

---

## Cara compile dari HP (tanpa laptop) via GitHub Actions

Kamu tidak perlu Arduino IDE atau PlatformIO di HP. Compile terjadi di server
GitHub, HP cuma dipakai untuk push kode & download hasil `.bin`.

### 1. Push project ini ke repo GitHub
- Buat repo baru (misal `wifihome-esp32`) lewat app **GitHub Mobile** atau
  browser di HP.
- Upload semua file di folder ini ke repo tsb. Cara termudah dari HP:
  - Buka repo di GitHub Mobile / browser → tombol **Add file → Upload files**
    → pilih semua file (jaga struktur folder `src/` dan `.github/workflows/`
    tetap sama).
  - Atau kalau lebih nyaman, pakai app **Working Copy** (iOS) / **GitJournal**
    / **Termux + git** (Android) untuk `git push` dari HP.

### 2. Actions otomatis jalan
- Begitu file `platformio.ini` atau isi `src/` ke-push ke branch `main`,
  workflow `build.yml` otomatis jalan.
- Bisa juga dipicu manual: buka tab **Actions** di repo (tersedia juga di app
  GitHub Mobile) → pilih workflow **"Build ESP32 Firmware"** → tombol
  **Run workflow**.

### 3. Download hasil compile
- Setelah selesai (~2-4 menit), buka run yang barusan di tab **Actions**.
- Scroll ke bagian **Artifacts** → download `firmware-esp32dev.zip`.
- Di dalamnya ada `firmware.bin`, `bootloader.bin`, `partitions.bin`,
  dan `firmware.elf` (untuk debugging).

### 4. Flash ke ESP32
Karena flashing butuh koneksi USB fisik ke ESP32, ini **harus** dilakukan dari
komputer (Windows/Mac/Linux) — tidak bisa langsung dari HP kecuali pakai OTG
+ tool khusus (jarang dan tidak direkomendasikan untuk pemula). Langkah dari
komputer:

```bash
pip install esptool
esptool.py --chip esp32 --port <PORT_USB_ESP32> --baud 921600 write_flash \
  0x1000  bootloader.bin \
  0x8000  partitions.bin \
  0x10000 firmware.bin
```

Ganti `<PORT_USB_ESP32>` dengan port yang sesuai (`COM3` di Windows,
`/dev/ttyUSB0` di Linux, `/dev/cu.usbserial-XXXX` di Mac).

> Alternatif tanpa command line: pakai tool GUI **ESP32 Flash Download Tool**
> (Windows) atau **ESP Flasher** yang mendukung upload 3 file `.bin` di atas
> pada offset yang sama.

---

## Setelah flashing: setup WiFi rumah

1. ESP32 boot pertama kali akan membuat Access Point bernama **`ESP32-Setup`**.
2. Dari HP, connect ke WiFi `ESP32-Setup` tsb.
3. Browser otomatis terbuka (captive portal) ke `192.168.4.1` — kalau tidak,
   buka manual.
4. Pilih SSID WiFi rumah, masukkan password, simpan.
5. ESP32 restart dan connect ke WiFi rumah. Buka **Serial Monitor** (butuh
   USB + komputer, baud rate `115200`) untuk melihat IP yang didapat — IP ini
   yang dimasukkan ke Settings di app Android.
6. Alternatif tanpa serial monitor: coba akses `http://wifihome-probe.local/status`
   dari HP yang terhubung ke WiFi rumah yang sama (pakai mDNS, tapi tidak
   selalu didukung semua router/OS).

---

## Catatan implementasi penting

- **Deteksi device via ARP**: firmware melakukan koneksi TCP singkat ke tiap
  IP di subnet (bukan ICMP ping asli, karena ESP32 Arduino core tidak punya
  raw socket ICMP bawaan yang ringan). Efek sampingnya sama: begitu ada
  komunikasi ke suatu IP, entri ARP lwIP terisi dan MAC address bisa dibaca
  lewat `etharp_find_addr()`. Device yang connect-nya gagal pun tetap
  terdeteksi selama ARP resolution berhasil (device hidup di layer 2).
- **Hostname resolution**: belum diimplementasikan (selalu `null`) — sesuai
  kontrak ini opsional dan app akan fallback ke vendor/IP. Bisa ditambah nanti
  pakai mDNS query `_device-info._tcp` per-IP kalau diperlukan.
- **Batas 50 device**: kalau subnet punya >50 host aktif, entry offline paling
  lama akan digantikan entry baru (LRU sederhana).
- **Scan berjalan di Core 1** (task terpisah) supaya HTTP/WebSocket di Core 0
  tetap responsif selama scan /24 berlangsung (bisa makan waktu beberapa
  detik — 254 host x ~200ms timeout kalau banyak yang tidak respons).
- **`POST /ping`**: implementasi ringan pakai mekanisme probe yang sama
  (bukan ICMP asli), cukup akurat untuk indikasi latency LAN kasar.

## Testing cepat setelah flash & connect WiFi

Dari HP/komputer yang di WiFi yang sama:

```bash
curl http://<IP_ESP32>/status
curl http://<IP_ESP32>/devices
curl -X POST http://<IP_ESP32>/scan
curl -X POST "http://<IP_ESP32>/ping?host=192.168.1.1&count=4"
```

Untuk WebSocket, bisa test pakai app **"Simple WebSocket Client"** atau situs
seperti websocket testing tool, connect ke `ws://<IP_ESP32>:81/ws`.
