# XPW300 WiFi Manager

Aplikasi Android untuk mengatur router **HSAirPo XPW300** (login otomatis ke `192.168.1.1`, user `admin` / pass `admin`).

## Fitur
- **Login otomatis** setiap kali membuka app / refresh.
- **Beranda** — kembali ke halaman utama router.
- **Cek User** — menampilkan daftar perangkat yang terhubung (dari tabel DHCP Server) dalam bentuk daftar yang mudah dibaca.
- **SSID 1 / 2 / 3 / 4** — nyalakan/matikan WiFi tertentu dengan sekali tekan (router ini kemungkinan hanya punya SSID1 & SSID2 aktif — kalau SSID3/4 tidak tersedia, app akan memberi tahu).
- **Prioritas User** — alur terpandu untuk membuat aturan QoS berdasarkan MAC address (isi form otomatis, kamu tinggal menekan tombol "Add" untuk konfirmasi akhir demi keamanan).
- **Pengaturan WiFi** — pintasan ke halaman WLAN Basic (channel, mode, dll).
- WebView tetap terlihat sepanjang waktu, jadi kalau ada tombol yang gagal ditemukan otomatis, kamu selalu bisa lanjutkan manual di layar yang sama.

## Kenapa otomasinya "menebak" elemen?
Saya hanya punya tangkapan layar antarmuka router, bukan kode HTML aslinya. Jadi skrip di app ini mencari tombol/kotak berdasarkan **teks yang terlihat** (misalnya tulisan "SSIDEnable", "Submit", "Login"), bukan berdasarkan id internal. Ini cukup andal untuk halaman bergaya tabel sederhana seperti punya router ini, tapi kalau suatu saat ada tombol yang tidak terdeteksi otomatis, WebView tetap tampil sehingga kamu bisa menekannya sendiri — aplikasi tidak akan "buntu".

Kalau nanti kamu mau otomasinya dibuat 100% presisi, kirim saya hasil "View Page Source" (atau HTML) dari halaman SSID Settings, Rule Type, dan Rule Setting — saya bisa sesuaikan selector-nya persis.

## Cara build APK (tanpa Android Studio)
Project ini sudah dilengkapi GitHub Actions (`.github/workflows/android-build.yml`) mengikuti panduan versi Gradle/AGP/Kotlin/JDK yang sudah kamu berikan:
- JDK 17, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24

Langkah:
1. Buat repository baru di GitHub (bisa privat).
2. Upload semua isi folder ini (bukan foldernya, tapi isinya) ke repo tersebut, lalu push ke branch `main`.
3. Buka tab **Actions** di GitHub, workflow "Android CI Build" akan berjalan otomatis.
4. Setelah selesai (centang hijau), buka hasil run tersebut → bagian **Artifacts** → unduh `app-debug-apk`.
5. Ekstrak zip-nya, dapatkan `app-debug.apk`, lalu install di HP (aktifkan "Install dari sumber tidak dikenal" kalau diminta).

## Cara pakai
1. Sambungkan HP ke WiFi router XPW300.
2. Buka aplikasi — otomatis login ke `192.168.1.1`.
3. Pakai tombol-tombol di bar atas untuk cek user, nyala/mati SSID, atau atur prioritas.

## Catatan keamanan
Password admin router (`admin`/`admin`) di-hardcode di dalam app supaya login otomatis. Sebaiknya jangan bagikan APK ini ke orang lain kalau kamu tidak ingin mereka bisa masuk ke pengaturan router-mu. Kalau kamu sudah mengganti password admin router, ubah nilai `defaultUser` / `defaultPass` di `MainActivity.kt` sesuai password barumu, lalu build ulang.
