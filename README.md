# GitHub Manager (Android)

Aplikasi Android sederhana untuk mengelola akun GitHub via Personal Access Token (PAT):

- Login pakai PAT
- List, buat, hapus repository
- Browse folder & file di repo
- Buat file baru, edit isi file (teks), upload file (apa saja), hapus file
- Download file/hasil ke folder Downloads HP
- Kelola GitHub Actions: lihat workflow, enable/disable, trigger run manual, lihat riwayat run, cancel/rerun/hapus run, download artifact hasil build

## Cara pakai

1. Push semua isi folder ini ke repo GitHub baru kamu (root repo, bukan di dalam subfolder).
2. Buka tab **Actions** di GitHub repo tersebut → workflow **Build APK** akan otomatis jalan setiap push ke branch `main`, atau kamu bisa trigger manual lewat "Run workflow".
3. Setelah selesai, buka run yang sukses → di bagian **Artifacts** ada `app-debug-apk` → download & extract, itu adalah file APK-nya.
4. Install APK tersebut di HP Android kamu (aktifkan "Install unknown apps" kalau diminta).
5. Buka app → masukkan GitHub Personal Access Token kamu.

## Cara bikin Personal Access Token (PAT)

1. Buka https://github.com/settings/tokens
2. Generate new token (classic atau fine-grained)
3. Centang scope: `repo` (full control) dan `workflow` (biar bisa kelola Actions)
4. Copy token, paste ke aplikasi saat login

## Catatan

- Token disimpan lokal di HP (SharedPreferences), tidak dikirim kemana-mana selain ke api.github.com.
- Edit file di aplikasi ini cocok untuk file teks (kode, markdown, config, dll). File binary (gambar, dll) sebaiknya di-upload lewat tombol Upload, bukan dibuka di editor teks.
- Untuk trigger workflow run manual dari app, workflow yang bersangkutan harus punya trigger `workflow_dispatch` di file YAML-nya (workflow `build-apk.yml` di sini sudah punya).
