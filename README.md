# APK Decompile via GitHub Actions

## Cara pakai

1. Taruh file APK kamu di folder `apk/`, kasih nama `app.apk`
2. Commit & push ke GitHub
3. Workflow otomatis jalan (cek tab **Actions**)
4. Setelah selesai (centang hijau), download hasil di bagian **Artifacts** pada run tersebut

## Isi hasil (3 artifact terpisah)

**1. `decompiled-source`** — hasil lengkap
- `java-source/` — kode Java/Kotlin hasil decompile (JADX), approximate, termasuk semua library
- `apktool-decode/` — smali code + resource asli (layout XML, AndroidManifest.xml, dll)

**2. `source-only`** — hasil ringkas, ukuran jauh lebih kecil
- Package name aplikasi otomatis dideteksi dari `AndroidManifest.xml`
- Hanya berisi folder kode sesuai package name kamu (misal `com/namakamu/app/`)
- Semua library pihak ketiga (androidx, kotlinx, dll) sudah dibuang

**3. `android-studio-project`** — skeleton project siap dibuka di Android Studio
- Struktur folder standar: `app/src/main/java/...`, `app/src/main/res/...`, `build.gradle.kts`, `settings.gradle.kts`
- Source code dan resource asli sudah ditaruh di tempat yang benar
- **PENTING**: baca `README_PENTING.md` di dalam folder ini. Dependency library (androidx, retrofit, dll) TIDAK bisa dideteksi otomatis dari APK dan harus ditambahkan manual berdasarkan error saat build. Source hasil decompile juga bisa ada error syntax yang perlu diperbaiki manual.

## Catatan

- Gunakan private repo kalau APK-nya sensitif
- Hasil decompile bukan source code 100% identik, terutama kalau APK di-obfuscate (ProGuard/R8)
- Pastikan APK yang di-decompile itu milik kamu sendiri
