# APK Decompile via GitHub Actions

Workflow ini decompile APK milik sendiri jadi:
- **Java source (approximate)** — pakai [JADX](https://github.com/skylot/jadx)
- **Smali + resource asli (XML, layout, AndroidManifest.xml)** — pakai [apktool](https://apktool.org/)

## Cara pakai

1. **Taruh file APK** kamu di folder `apk/`, misal `apk/app.apk`
2. **Commit & push** ke repo:
   ```bash
   git add apk/app.apk
   git commit -m "add apk for decompile"
   git push
   ```
   Workflow otomatis jalan begitu ada file `.apk` baru di folder `apk/`.

   Atau jalankan manual:
   - Buka tab **Actions** di repo GitHub kamu
   - Pilih workflow **"Decompile APK"**
   - Klik **"Run workflow"**
   - Isi `apk_path` kalau nama filenya beda dari `apk/app.apk`

3. **Tunggu proses selesai** (biasanya 1-3 menit tergantung ukuran APK)

4. **Download hasil**:
   - Buka run yang baru selesai di tab Actions
   - Scroll ke bagian **Artifacts**
   - Download `decompiled-source.zip`

## Isi hasil decompile

```
output/
├── java-source/       <- kode Java hasil JADX (approximate, bukan 100% sama persis)
└── apktool-decode/    <- smali code + resource asli (layout XML, values, Manifest)
```

## Catatan penting

- Hasil JADX **bukan source code 100% identik** dengan aslinya — nama variabel lokal, komentar, dan struktur tertentu bisa hilang/berubah, terutama kalau APK di-obfuscate (ProGuard/R8)
- Kalau APK di-obfuscate, nama class/method bakal muncul acak (`a.b.c`, dst) — logic tetap kebaca tapi lebih susah ditelusuri
- APK yang di-upload ke repo akan tersimpan di **git history** — kalau repo public atau APK sensitif, gunakan **private repo**
- Pastikan APK yang di-decompile itu **milik kamu sendiri** (hasil build sendiri)

## Kalau APK-nya sudah tidak ada sama sekali

Kalau APK juga sudah hilang (nggak ada file-nya di mana pun), cek dulu:
- Apakah masih ter-install di HP/emulator lama → bisa di-extract pakai `adb pull` atau aplikasi seperti APK Extractor
- Cek folder build lokal (`app/build/outputs/apk/`) siapa tau masih ada sisa build lama
- Cek history upload kalau pernah di-submit ke Play Console (bisa download App Bundle/APK dari sana)
