# PROGRESS.md — Status Project GitRepo Manager

Dokumen ini untuk siapa pun (manusia atau AI) yang melanjutkan project ini,
supaya tidak perlu menebak-nebak arsitektur yang sudah ada.

## Status: Step 1 & 2 selesai — siap untuk build & test di Android Studio

Semua fitur inti sudah diimplementasikan secara lengkap (bukan skeleton/
placeholder). Total ~3.450 baris Kotlin di 23 file, plus resource XML,
Gradle config, dan dokumentasi. Belum pernah di-compile dengan Gradle
sungguhan karena environment pembuatan tidak memiliki Android SDK — hanya
diaudit secara statis (import checking, brace matching, cross-reference
field/method antara model, repository, viewmodel, dan UI).

## Yang WAJIB dilakukan sebelum dianggap production-ready

1. **Buka di Android Studio, jalankan Gradle Sync**, lalu Build. Ini
   langkah paling penting yang belum bisa dilakukan di environment
   pembuatan project ini.
2. Perbaiki error compile apa pun yang muncul (kemungkinan kecil, karena
   sudah diaudit, tapi belum divalidasi compiler sungguhan).
3. Test end-to-end: login dengan PAT asli, lihat daftar repo, buka detail,
   jelajahi file, lihat Actions, dan **uji fitur Danger Zone di repo test
   (BUKAN repo penting)** karena sifatnya destruktif permanen.

## Arsitektur (jangan diubah tanpa alasan kuat)

- MVVM: `ViewModel` (Hilt, StateFlow) → `GitHubRepository` (single source
  of truth) → `GitHubApiService` (Retrofit interface)
- Semua network response dibungkus `Resource<T>` (`util/Resource.kt`):
  `Loading` / `Success<T>` / `Error`
- Navigasi terpusat di `ui/navigation/Routes.kt` (string rute + helper
  fungsi) dan `ui/navigation/NavGraph.kt` (NavHost). **Jangan hardcode
  string rute di tempat lain.**
- Token PAT disimpan di `data/local/TokenStorage.kt` via
  `EncryptedSharedPreferences`. `AuthInterceptor` otomatis menambahkan
  header `Authorization: Bearer <token>` ke semua request.

## Fitur yang sudah lengkap

| Fitur | File utama | Mirror dari script asli |
|---|---|---|
| Login PAT | `ui/screens/login/*` | — |
| List & search/sort repo | `ui/screens/repolist/*` | `cek-repo-2.sh` |
| Detail repo (tab Files/Actions/About) | `ui/screens/repodetail/*` | `cek-repo-2.sh` |
| File browser + preview teks | `ui/screens/filebrowser/*` | `cek-repo-2.sh` |
| Riwayat GitHub Actions | `ui/screens/actions/*` | `cek-repo-2.sh` |
| Kosongkan repo (dgn konfirmasi ketik nama) | `ui/screens/dangerzone/*` | `bersihkan-repo.sh` |
| Settings (profil, logout) | `ui/screens/settings/*` | — |

## Fitur yang BELUM ada (ide pengembangan lanjutan, opsional)

- Tidak ada unit test / instrumented test sama sekali.
- Tidak ada dark/light theme toggle manual (saat ini otomatis ikut sistem).
- Tidak ada dukungan multi-akun / switch akun.
- Tidak ada create/edit/upload file baru (hanya baca + hapus semua saat
  Danger Zone).
- File Browser tidak mendukung file besar (>1MB) atau file biner (by
  design, untuk keamanan/performa — lihat `FileBrowserViewModel.MAX_PREVIEWABLE_BYTES`).
- `getBranch()` endpoint sengaja dihapus dari `GitHubApiService` karena
  tidak terpakai dan berisiko (return type `Unit` bermasalah dengan
  Kotlinx Serialization converter). Jika suatu saat perlu validasi branch,
  tambahkan kembali dengan return type yang benar (misal `Response<ResponseBody>`
  dari `okhttp3.ResponseBody`, bukan `Response<Unit>`).

## Bug yang sudah diperbaiki selama audit (untuk referensi, jangan diulang)

- `Modifier.width()` dipakai di beberapa Screen tanpa import
  `androidx.compose.foundation.layout.width` — sudah diperbaiki di semua
  file yang terpengaruh.
- `okhttp3.MediaType.get()` (deprecated/dihapus di OkHttp 4) diganti
  dengan `"...".toMediaType()` extension yang benar di `NetworkModule.kt`.
- Import tak terpakai (`Credentials`, `Header`, `ExposedDropdownMenu*`)
  dibersihkan dari `GitHubRepository.kt` dan `GitHubApiService.kt`.

## Kontak arsitektur cepat

- Namespace/applicationId: `com.lmob.gitrepomanager`
- minSdk 29, compileSdk 34, targetSdk 34
- Kotlin 1.9.24, Compose BOM 2024.06.00, Hilt 2.51.1
