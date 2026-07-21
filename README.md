# WiFi Home Monitor (Android)

Aplikasi Android untuk memantau jaringan WiFi rumah: status koneksi, ping,
speedtest, daftar perangkat di jaringan lokal (via ESP32), remote Android
TV/Google TV, dan screenshot TV.

## Struktur Project

```
wifihome/
├── app/                          # Source code Android (Kotlin + Jetpack Compose)
│   └── src/main/java/com/localnet/wifihome/
│       ├── data/                 # Model, network client, repository
│       │   ├── model/            # Data class (NetworkDevice, WifiStatus, dll)
│       │   ├── network/          # Retrofit/OkHttp/WebSocket ke ESP32, Ping, SpeedTest
│       │   ├── tvremote/         # Android TV Remote Protocol v2 client
│       │   └── adb/              # ADB client (dadb) untuk screenshot TV
│       ├── ui/                   # ViewModel, Screen (Compose), Navigation
│       └── MainActivity.kt
├── .github/workflows/build.yml   # GitHub Actions — build APK otomatis
└── docs/
    └── ESP32_API_CONTRACT.md     # WAJIB DIBACA sebelum bikin firmware ESP32
```

## Fitur

| Fitur | Status | Catatan |
|---|---|---|
| Status WiFi (SSID, IP, sinyal) | ✅ Selesai | Native Android WifiManager |
| Ping | ✅ Selesai | Pakai binary `ping` sistem Android |
| Speedtest | ✅ Selesai | Server publik Cloudflare, estimasi kasar |
| Daftar device jaringan (IP/MAC/nama) | ✅ App selesai, **butuh firmware ESP32** | Lihat `docs/ESP32_API_CONTRACT.md` |
| Remote TV Google | ⚠️ Kerangka dasar | Android TV Remote Protocol v2, pairing perlu penyempurnaan (lihat TODO di `TvRemoteClient.kt`) |
| Screenshot TV | ✅ Selesai (perlu device fisik utk tes) | Via ADB Wireless Debugging, pakai library `dadb` |

## Cara Build

### Lewat GitHub Actions (otomatis)
1. Push repo ini ke GitHub.
2. Workflow `.github/workflows/build.yml` otomatis jalan tiap push ke `main`.
3. APK hasil build ada di tab **Actions** → pilih run terakhir → **Artifacts**
   → unduh `wifihome-debug-apk` atau `wifihome-release-apk-unsigned`.

### Build lokal (opsional, kalau punya Android Studio)
```bash
./gradlew assembleDebug
```
APK ada di `app/build/outputs/apk/debug/`.

## Setelah Install APK

1. Buka app, izinkan permission lokasi (wajib agar SSID WiFi terbaca).
2. Buka tab **Pengaturan**, isi IP ESP32 (setelah firmware ESP32 di-flash &
   terhubung ke WiFi rumah) dan IP Android TV (lihat di Settings TV > Network).
3. Untuk fitur screenshot TV: aktifkan **Wireless Debugging** di
   Settings > System > Developer Options di TV.

## Bagian yang Masih Perlu Dikerjakan (untuk AI/dev berikutnya)

1. **Firmware ESP32** — belum dibuat sama sekali di repo ini. Baca
   `docs/ESP32_API_CONTRACT.md` untuk spesifikasi lengkap REST API + WebSocket
   yang harus diimplementasikan supaya kompatibel dengan app Android yang
   sudah ada.
2. **TV Remote pairing** — `TvRemoteClient.kt` punya kerangka dasar protokol
   tapi bagian derivation secret pairing (SHA-256 dari sertifikat + kode)
   masih TODO. Perlu diuji langsung dengan Android TV fisik.
3. **Gradle wrapper jar** — belum di-commit (biner). Workflow CI sudah
   dikonfigurasi untuk generate otomatis (`gradle wrapper`), tapi kalau mau
   build lokal, jalankan `gradle wrapper --gradle-version 8.7` dulu sekali.
