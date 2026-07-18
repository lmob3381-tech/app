# AdGuard Browser

Browser Android sederhana (Kotlin) yang me-resolve semua request lewat
**AdGuard DNS-over-HTTPS** (`dns.adguard.com`), bukan DNS bawaan jaringan/operator.

## Cara kerja DNS-nya

`DohManager.kt` bikin `OkHttpClient` yang DNS resolver-nya diganti pakai
`okhttp3.dnsoverhttps.DnsOverHttps`, diarahkan ke endpoint AdGuard:

- Default (ad & tracker blocking): `https://dns.adguard.com/dns-query`
- Family Protection: `https://family.adguard-dns.com/dns-query`
- Non-filtering: `https://unfiltered.adguard-dns.com/dns-query`

Semua request `GET` dari WebView di-intercept di `MainActivity.shouldInterceptRequest()`
lalu di-fetch lewat client AdGuard tadi. Kalau gagal, otomatis fallback ke WebView
default biar browsing tetap jalan.

## Build lewat Termux (ikutin alur standar kamu)

```bash
cd ~/AdGuardBrowser
git add -A
git commit -m "init adguard browser"
git push origin main
gh workflow run android-build.yml
gh run watch
gh run download
cp app-debug-apk/app-debug.apk /sdcard/Download/AdGuardBrowser.apk
```

Install dari File Manager > Download > `AdGuardBrowser.apk`.

## Menu di dalam app

- **Toggle Proteksi AdGuard DNS** — nyalain/matiin fitur intercept DoH
- **Pilih Mode AdGuard DNS** — ganti antara Default / Family / Non-filtering
- **Test Resolusi DNS** — resolve `example.com` lewat AdGuard, buat buktiin DNS-nya kepakai
- **Bookmarks / Tambah Bookmark** — simpan halaman favorit
