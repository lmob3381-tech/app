# GitRepo Manager

Aplikasi Android modern untuk mengelola repository GitHub langsung dari
perangkat mobile — versi aplikasi dari script Termux `bersihkan-repo.sh`
dan `cek-repo-2.sh`.

## Fitur

- **Login dengan Personal Access Token** — token disimpan terenkripsi
  (AES256-GCM via Android Keystore), tidak pernah meninggalkan perangkat
  kecuali sebagai header `Authorization` ke `api.github.com`.
- **Daftar Repository** — pencarian, filter, dan pengurutan (terbaru,
  nama, bintang), pull-to-refresh.
- **Detail Repository** — info lengkap (bahasa, bintang, fork, issue,
  branch utama) dalam tab Berkas / Actions / Tentang.
- **File Browser** — jelajahi struktur folder repository, preview isi
  file teks langsung di aplikasi.
- **GitHub Actions** — riwayat workflow run dengan status badge
  (sukses/gagal/berjalan/dibatalkan).
- **Danger Zone (Kosongkan Repository)** — mirror dari `bersihkan-repo.sh`,
  menghapus seluruh isi file repository lewat GitHub Contents API, dengan
  konfirmasi wajib (ketik ulang nama repo) sebelum eksekusi.
- **Pengaturan** — info akun, logout.

## Arsitektur

- **Bahasa & UI**: Kotlin + Jetpack Compose (Material 3)
- **Pola**: MVVM — `ViewModel` (StateFlow) + `Repository` + `Retrofit`
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp + Kotlinx Serialization
- **Penyimpanan token**: EncryptedSharedPreferences (androidx.security-crypto)
- **Navigasi**: Jetpack Navigation Compose, satu `NavGraph` terpusat
- **Target**: minSdk 29 (Android 10+), compileSdk 34

## Struktur Folder

```
app/src/main/java/com/lmob/gitrepomanager/
├── data/
│   ├── model/       # DTO GitHub API (Kotlinx Serialization)
│   ├── remote/      # Retrofit service + AuthInterceptor
│   ├── local/        # TokenStorage (EncryptedSharedPreferences)
│   └── repository/  # GitHubRepository — single source of truth
├── di/              # Hilt modules
├── ui/
│   ├── theme/        # Color, Type, Theme (Material 3, dark/light)
│   ├── navigation/   # Routes.kt + NavGraph.kt
│   ├── components/    # Komponen reusable (loading, error, badge, dst)
│   └── screens/       # login, repolist, repodetail, filebrowser,
│                       # actions, dangerzone, settings
├── util/            # Resource<T> sealed class
├── MainActivity.kt
└── GitRepoManagerApp.kt
```

## Cara Build

1. Buka folder ini di Android Studio (Koala atau lebih baru).
2. Saat pertama kali membuka, Android Studio akan menampilkan prompt
   untuk melengkapi Gradle Wrapper (`gradle-wrapper.jar` tidak disertakan
   dalam paket ini karena berupa file biner) — klik "OK"/"Sync Now" saja,
   ini otomatis.
3. Sync Gradle (dependency akan otomatis terunduh).
4. Jalankan di emulator/device dengan Android 10 (API 29) atau lebih baru.

## Token GitHub

Buat token di https://github.com/settings/tokens/new dengan scope
minimal `repo` (untuk akses penuh) dan `workflow` (untuk membaca status
GitHub Actions).

## Catatan Keamanan

Fitur "Kosongkan Repository" bersifat destruktif dan permanen terhadap
isi file (riwayat commit tetap ada). Aplikasi mewajibkan pengguna mengetik
ulang nama repository sebagai konfirmasi sebelum operasi dijalankan.
