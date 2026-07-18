# Network Checker

Aplikasi Android sederhana (Kotlin) untuk mengecek kondisi jaringan:

- Informasi WiFi: SSID, BSSID, IP lokal, kekuatan sinyal (RSSI), kecepatan link, frekuensi.
- Informasi jaringan: jenis koneksi (WiFi/Seluler/Ethernet), gateway, DNS server, interface, estimasi bandwidth.
- Tes Ping ke host/IP mana pun (default 8.8.8.8), 4x percobaan.
- IP Publik (via api.ipify.org).
- Tarik ke bawah (swipe refresh) untuk memuat ulang semua data.

## Cara build lewat GitHub Actions

1. Buat repository baru di GitHub.
2. Upload/push seluruh isi folder project ini ke repository tersebut (pastikan struktur `.github/workflows/android-build.yml` ikut ter-push).
3. Buka tab **Actions** di repository → workflow **Android CI Build** akan otomatis berjalan setiap ada push ke branch `main`.
4. Setelah selesai (centang hijau), buka run tersebut → bagian **Artifacts** → unduh `NetworkChecker-debug-apk`.
5. Ekstrak zip-nya, di dalamnya ada `app-debug.apk` — tinggal install di HP Android (aktifkan "Install dari sumber tidak dikenal" jika diminta).

## Build manual (opsional, di komputer sendiri)

Jika punya Android Studio:
```
File > Open > pilih folder NetworkChecker
```
Tunggu Gradle sync, lalu klik Run atau Build > Build Bundle(s)/APK(s) > Build APK(s).

## Catatan izin

Aplikasi meminta izin lokasi (`ACCESS_FINE_LOCATION`) karena sejak Android 8+, membaca SSID WiFi memerlukan izin lokasi dari sistem Android. Izinkan saat diminta agar SSID bisa terbaca.
