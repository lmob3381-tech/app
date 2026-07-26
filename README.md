# Build pydantic-core untuk Termux (armv7 / 32-bit Android)

Repo ini pakai GitHub Actions buat cross-compile `pydantic-core` (yang butuh Rust)
supaya HP Termux 32-bit tidak perlu compile sendiri di HP.

## Cara pakai

1. Push folder ini ke repo GitHub baru (public atau private, bebas).
2. Buka tab **Actions** di repo tersebut.
3. Pilih workflow **"Build pydantic-core for Termux (armv7 Android)"**.
4. Klik **"Run workflow"**.
   - `pydantic_core_version`: kosongkan kalau mau versi terbaru dari branch `main`,
     atau isi contoh `2.23.4` kalau mau versi spesifik (harus ada tag `v2.23.4` di
     repo asli pydantic-core).
   - `python_version`: WAJIB SAMA dengan versi Python di Termux kamu.
     Cek dulu di Termux dengan:
     ```
     python --version
     ```
     Kalau keluar `Python 3.12.x`, isi `3.12`. Kalau `3.11.x`, isi `3.11`, dst.
5. Tunggu build selesai (~10-20 menit, kadang lebih cepat karena di server GitHub).
6. Setelah selesai (centang hijau), scroll ke bawah ke bagian **Artifacts**,
   download `pydantic-core-armv7-termux.zip`.

## Cara pakai hasil build-nya di Termux

1. Pindahkan file zip ke HP (lewat Google Drive, Telegram, dsb), lalu di Termux:
   ```bash
   termux-setup-storage   # kalau belum pernah
   cd ~
   cp /sdcard/Download/pydantic-core-armv7-termux.zip .
   unzip pydantic-core-armv7-termux.zip
   ```
2. Install file `.whl` yang dihasilkan:
   ```bash
   pip install pydantic_core-*.whl
   ```
3. Kalau sukses, lanjut install sisanya seperti biasa:
   ```bash
   pip install pydantic google-genai requests beautifulsoup4
   ```

## Kalau build gagal

- Cek log di tab Actions, biasanya errornya soal:
  - **Versi Python mismatch**: pastikan `python_version` di workflow sama persis
    dengan `python --version` di Termux.
  - **Tag versi tidak ada**: kalau isi `pydantic_core_version` tapi repo asli tidak
    punya tag itu, checkout akan gagal. Cek daftar tag di
    https://github.com/pydantic/pydantic-core/tags
  - **NDK/linker error**: biasanya versi NDK di workflow (`r25b`) sudah cukup umum,
    tapi kalau pydantic-core versi sangat baru butuh NDK lebih baru, ganti
    `ndk-version` di file `.github/workflows/build.yml`.

## Catatan

- Wheel hasil build ini HANYA untuk arsitektur **armv7 (32-bit)**. Kalau ternyata
  HP kamu sebenarnya 64-bit (`aarch64`), pakai repo siap pakai
  https://github.com/Eutalix/android-pydantic-core yang sudah sedia dua-duanya,
  jadi tidak perlu build sendiri kalau memang itu cocok.
- Build ini jalan di server GitHub (bukan di HP), jadi tidak makan resource HP
  sama sekali — hanya proses download hasil akhirnya saja yang ringan.
