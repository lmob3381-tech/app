# StreamLocal Android App

Aplikasi Android native (Kotlin) untuk StreamLocal — player pribadi yang konek ke server
`server.js` kamu sendiri (yt-dlp + ffmpeg lewat Express, biasanya di-expose via Cloudflare Tunnel).

## Fitur

- Search video (YouTube biasa) & search musik (via music.youtube.com)
- Resolve metadata otomatis sebelum play (judul, durasi asli, thumbnail)
- Player video (Media3/ExoPlayer) & mode audio-only (hemat kuota, cocok buat lagu)
- Durasi & progress pakai angka dari `/api/resolve`, bukan dari elemen player
  (sesuai catatan di API.md — stream fragmented, `duration` bawaan player naik-naik sendiri)
- Riwayat tontonan + hapus per-item
- **Base URL server bisa diubah kapan saja dari layar Pengaturan** — tidak perlu rebuild
  APK setiap kali link Cloudflare Tunnel kamu berubah
- Tes koneksi ke server langsung dari Pengaturan
- UI dark, modern, minim dependency (tanpa Retrofit/OkHttp/Glide — pakai HttpURLConnection
  murni supaya source tetap kecil)

## Cara pakai

1. Jalankan `server.js` di komputer kamu, expose lewat `cloudflared tunnel --url http://localhost:4200`
   (atau port berapa pun sesuai `.env` kamu).
2. Install APK ini di HP.
3. Buka **Pengaturan** (ikon gear di kanan atas) → masukkan URL tunnel-nya
   (contoh: `https://xxxx-xxxx.trycloudflare.com`) → **Simpan** → **Tes Koneksi**.
4. Kembali ke halaman utama, cari video/lagu, tap untuk memutar.
5. Setiap kali tunnel restart dan URL-nya beda, tinggal ganti lagi di Pengaturan — tanpa install ulang.

## Build via GitHub Actions

Workflow ada di `.github/workflows/build.yml`. Setiap push ke branch `main` (atau trigger manual
lewat tab **Actions → Build Android APK → Run workflow**), CI akan:

1. Setup JDK 17 + Android SDK
2. Generate Gradle wrapper otomatis (jar wrapper sengaja tidak di-commit)
3. Build `assembleDebug` dan `assembleRelease` (unsigned)
4. Upload kedua APK sebagai artifact — download dari halaman run workflow tersebut

APK release masih **unsigned**. Untuk publish/sideload lebih permanen, sign manual pakai
`apksigner` atau tambahkan keystore + secrets kalau mau CI yang men-sign otomatis.

## Struktur singkat

```
app/src/main/java/com/streamlocal/app/
├── MainActivity.kt      # search + tabs (video/musik/riwayat)
├── PlayerActivity.kt     # resolve + ExoPlayer (video & audio-only)
├── SettingsActivity.kt   # ubah base URL & tes koneksi
├── ApiClient.kt          # wrapper semua endpoint /api/*
├── MediaAdapter.kt       # RecyclerView adapter
├── Models.kt             # data class MediaItem/ResolveResult
└── Prefs.kt              # simpan base URL di SharedPreferences
```
