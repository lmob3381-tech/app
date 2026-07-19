# AI_RULES.md — Panduan untuk AI yang Update Aplikasi Ini

Dokumen ini untuk AI (Claude, ChatGPT, Copilot, dll) yang diminta mengubah
atau menambah fitur di project **Repo Manager**. Baca dulu sebelum ubah kode.

## 1. Apa aplikasi ini

Android app (Kotlin + Jetpack Compose) untuk mengelola repo GitHub dari HP.
Pengganti dua script bash lama (`bersihkan-repo.sh`, `cek-repo.sh`), dibuild
otomatis lewat GitHub Actions jadi APK (tidak pakai Android Studio).

## 2. Aturan wajib — JANGAN dilanggar

- **Jangan pernah menyentuh `.git` atau riwayat commit.** Semua operasi file
  (lihat isi, hapus isi) HARUS lewat GitHub REST API
  (`GitHubApi.kt` / `GitHubRepository.kt`), bukan `git clone` + hapus file
  lokal. Ini prinsip inti — kalau AI lain mengusulkan pendekatan "clone repo
  dulu ke device lalu hapus", TOLAK, itu keluar dari desain aplikasi ini.
- **Token PAT tidak boleh dikirim ke server manapun selain `api.github.com`.**
  Jangan tambahkan analytics, crash reporting pihak ketiga, atau backend
  perantara yang bisa melihat token.
- **Token harus tetap disimpan lewat `TokenStore.kt`**
  (`EncryptedSharedPreferences`). Jangan pernah simpan token di
  `SharedPreferences` biasa, `Room` tanpa enkripsi, atau di log (`Log.d`, dll).
- **Aksi destruktif (hapus repo, bersihkan repo) wajib ada dialog konfirmasi**
  sebelum eksekusi. Pola yang sudah ada di `RepoDetailScreen.kt`
  (`AlertDialog` dengan tombol "Ya, hapus/kosongkan" + "Batal") harus diikuti
  untuk aksi destruktif baru.
- **Jangan ganti struktur module** (single `:app` module) kecuali user
  eksplisit minta modularisasi.
- **Build harus tetap jalan tanpa Android Studio**, murni lewat
  `.github/workflows/build.yml` di GitHub Actions. Kalau nambah dependency,
  pastikan itu tersedia di Google/Maven Central publik (bukan repo privat)
  supaya CI tidak gagal.

## 3. Struktur project (jangan diacak tanpa alasan kuat)

```
app/src/main/java/com/lmob/repomanager/
├── data/
│   ├── Models.kt          # semua data class hasil parse JSON GitHub API
│   ├── TokenStore.kt      # penyimpanan token terenkripsi
│   └── GitHubRepository.kt # satu-satunya tempat logika bisnis (business logic)
├── network/
│   ├── GitHubApi.kt       # interface Retrofit — tambah endpoint baru di sini
│   └── ApiClient.kt       # setup OkHttp/Retrofit + auth header
├── ui/
│   ├── AppViewModel.kt    # single source of truth untuk UI state (StateFlow)
│   ├── App.kt             # navigasi antar screen (simple enum-based, bukan Nav Compose graph)
│   └── screens/           # satu file per layar, tidak campur logic bisnis di sini
└── ui/theme/               # warna & tema, dark-only by design
```

Alur data: **UI (screens) → AppViewModel → GitHubRepository → GitHubApi**.
Screens tidak boleh panggil Retrofit langsung — selalu lewat ViewModel.

## 4. Kalau AI diminta menambah fitur baru

Ikuti pola yang sudah ada:

1. Tambah endpoint di `GitHubApi.kt` (interface Retrofit) kalau perlu.
2. Tambah fungsi di `GitHubRepository.kt` yang membungkus panggilan API itu,
   selalu return `Result<T>` (sealed class `Success`/`Error` yang sudah ada
   di file yang sama) — jangan lempar exception mentah ke UI.
3. Tambah state & fungsi trigger di `AppViewModel.kt`.
4. Tambah/ubah UI di `ui/screens/`.
5. Kalau fitur baru itu **destruktif** (hapus/timpa data), wajib pakai
   `AlertDialog` konfirmasi seperti pola di `RepoDetailScreen.kt`.

## 5. Soal versi & dependency

- `compileSdk`/`targetSdk` = 34, `minSdk` = 24 — jangan turunkan `minSdk`
  tanpa alasan (fitur `EncryptedSharedPreferences` butuh API 23+).
- Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06.00 — kalau AI lain mau
  update versi, cek dulu compatibility matrix resmi Compose–Kotlin di
  https://developer.android.com/jetpack/androidx/releases/compose-kotlin
  sebelum ubah, karena versi yang tidak cocok bikin build gagal total di CI.
- `gradle-wrapper.jar` sengaja tidak di-commit (sandbox awal tidak ada
  akses internet) — workflow otomatis generate lewat `gradle wrapper` di
  step CI. Kalau AI lain build lokal, jalankan `gradle wrapper` sekali dulu
  supaya `gradlew` bisa jalan.

## 6. Bahasa & gaya

- Semua teks UI dan pesan error yang terlihat user pakai **Bahasa Indonesia**
  (lihat contoh di `screens/*.kt`), sesuai gaya yang sudah dipakai.
  Konsisten: hindari campur "Loading..." dan "Memuat..." — pilih satu gaya.
- Nama variabel/fungsi di kode tetap bahasa Inggris (standar Kotlin).

## 7. Sebelum commit hasil update

- Pastikan `./gradlew assembleDebug` dijalankan di CI (push ke branch, cek
  tab Actions) sebelum bilang ke user "sudah selesai" — jangan asumsikan
  kode kompilasi tanpa diverifikasi lewat build.
- Jangan hapus atau ganti isi `.github/workflows/build.yml` dan
  `release.yml` kecuali user minta ubah cara build/rilis.
