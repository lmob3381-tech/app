# StreamLocal App (Android)

Aplikasi Android native (Kotlin) yang menjadi client untuk server **StreamLocal**
(backend Express + yt-dlp/ffmpeg). Base URL server **tidak di-hardcode** — diatur
langsung di layar Pengaturan aplikasi, jadi kalau kamu ganti alamat Cloudflare
Tunnel (cloudflared), cukup update di Settings tanpa perlu rebuild APK.

## Fitur

- **Pengaturan Server** — isi base URL (mis. `https://xxxx.trycloudflare.com`
  atau `http://127.0.0.1:4200`), dengan tombol "Tes Koneksi".
- **Pencarian** — tab Video / Musik, memanggil `GET /api/search`.
- **Player** — `POST /api/resolve` lalu putar `stream_url` / `stream_url_audio`
  memakai ExoPlayer (Media3). Durasi total selalu diambil dari hasil resolve
  (bukan dari player), sesuai catatan di `API.md` server.
- **Riwayat** — `GET /api/history`, hapus per item lewat `DELETE /api/history/{id}`.

## Struktur endpoint yang dipakai

| Fungsi | Endpoint |
|---|---|
| Cari video/musik | `GET /api/search?q=...&type=video|music&limit=...` |
| Resolve + ambil link stream | `POST /api/resolve` (`{ "url": "..." }`) |
| Stream video | `GET /api/stream?url=...` (dipakai lewat `stream_url`) |
| Stream audio | `GET /api/stream/audio?url=...` (dipakai lewat `stream_url_audio`) |
| Riwayat | `GET /api/history`, `DELETE /api/history/{id}` |

## Build lokal

Butuh JDK 17 dan Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug
```

APK hasil build ada di `app/build/outputs/apk/debug/`.

## Build otomatis via GitHub Actions

Workflow `.github/workflows/build.yml` akan:
1. Setup JDK 17 + Gradle 8.7
2. Generate `gradle-wrapper.jar` (sengaja tidak disertakan di repo, di-generate saat build)
3. Build APK debug dan release (unsigned)
4. Upload keduanya sebagai artifact — bisa diunduh dari tab **Actions** di GitHub
   setelah workflow selesai.

Trigger otomatis saat push ke branch `main`, atau jalankan manual lewat
tab **Actions → Build APK → Run workflow**.

## Catatan penting (dari dokumentasi API server)

- **Durasi** tidak bisa diandalkan dari elemen video/audio player karena stream
  di-remux fragmented — aplikasi ini selalu memakai angka `duration` dari hasil
  `/api/resolve`.
- **Seek** terbatas karena stream adalah live-pipe (`yt-dlp → ffmpeg → response`),
  bukan file statis dengan byte-range. Lompat jauh ke bagian yang belum di-buffer
  bisa gagal.
- Server belum ada autentikasi. Kalau tunnel-nya publik dan dipakai banyak orang,
  pertimbangkan menambahkan API key di sisi server.
- `network_security_config.xml` mengizinkan cleartext (http) karena base URL
  bisa berupa alamat lokal (`http://192.168.x.x:4200`) maupun tunnel https.

## Mengganti server saat cloudflared berubah

Buka aplikasi → tombol gear (Pengaturan) di kanan bawah → isi Base URL baru →
Simpan. Tidak perlu rebuild atau reinstall APK.
