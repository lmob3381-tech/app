# Repo Manager (Android)

Aplikasi Android (Kotlin + Jetpack Compose) untuk mengelola repo GitHub kamu
langsung dari HP — pengganti `bersihkan-repo.sh` dan `cek-repo.sh`, plus fitur
tambahan (buat & hapus repo), dalam satu aplikasi.

## Fitur

| Fitur | Setara dengan |
|---|---|
| 📃 List semua repo | `gh repo list` |
| ➕ Buat repo baru | — (baru) |
| 🗑️ Hapus repo | — (baru) |
| 🧹 Bersihkan repo (kosongkan semua file) | `bersihkan-repo.sh` |
| 📁 Lihat isi file/folder repo | bagian 1 `cek-repo.sh` |
| ⚙️ Cek status & riwayat GitHub Actions | bagian 2 `cek-repo.sh` |

Login pakai **Personal Access Token (PAT)**, disimpan terenkripsi di HP
(`EncryptedSharedPreferences`) — tidak pernah dikirim ke server manapun selain
`api.github.com`.

## Struktur project

```
RepoManager/
├── app/
│   └── src/main/java/com/lmob/repomanager/
│       ├── data/            # Model, TokenStore, GitHubRepository
│       ├── network/         # Retrofit API + client
│       ├── ui/              # ViewModel, App.kt (navigasi)
│       └── ui/screens/      # Login, RepoList, RepoDetail, CreateRepo
├── .github/workflows/
│   ├── build.yml            # Build debug APK tiap push/PR ke main
│   └── release.yml          # Build release APK saat push tag v*.*.*
└── ...
```

## Cara build lewat GitHub Actions

1. Push folder ini ke repo GitHub kamu (root repo = folder `RepoManager` ini).
2. Buka tab **Actions** di GitHub — workflow **Build APK** akan otomatis
   jalan tiap push ke branch `main` (atau jalankan manual lewat
   "Run workflow").
3. Setelah selesai, buka run tersebut → bagian **Artifacts** → download
   `repo-manager-debug-apk` → install APK-nya ke HP.
4. Untuk rilis resmi: buat tag versi, misal:
   ```
   git tag v1.0.0
   git push origin v1.0.0
   ```
   Workflow **Release APK** akan jalan dan otomatis membuat GitHub Release
   berisi APK.

> Catatan: APK yang dihasilkan **unsigned/debug-signed**, cukup untuk
> instal manual (`Install dari sumber tidak dikenal`). Kalau nanti mau
> publish ke Play Store, perlu setup signing key terpisah (bisa saya
> bantu tambahkan kalau perlu).

## Cara pakai token GitHub

1. Di GitHub: **Settings → Developer settings → Personal access tokens**
   (fine-grained atau classic).
2. Beri scope minimal: `repo` (akses penuh repo) dan `workflow` (untuk baca
   status Actions).
3. Copy token, tempel di layar login aplikasi.

## Build lokal (opsional, kalau punya Android Studio)

```bash
./gradlew assembleDebug
```
APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Kenapa strukturnya begini

- **Retrofit + OkHttp** → komunikasi ke GitHub REST API v3, sama seperti
  `gh` CLI di balik layar.
- **Jetpack Compose + Material 3** → UI modern, dark theme terinspirasi
  GitHub.
- **EncryptedSharedPreferences** → token tidak pernah disimpan plaintext.
- **Bersihkan repo** memakai Git Trees API (`git/trees/{branch}?recursive=1`)
  lalu hapus tiap file satu-satu lewat Contents API — logikanya identik
  dengan `bersihkan-repo.sh`, hanya dijalankan lewat REST API bukan CLI.
