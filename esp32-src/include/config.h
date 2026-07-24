#pragma once

// ===================== KONFIGURASI =====================

// --- WiFi Access Point (yang dipancarkan ESP32 ke user) ---
#define AP_SSID        "Free-WiFi-Splash"
#define AP_PASSWORD    ""              // kosongkan "" untuk open network, atau isi min 8 char
#define AP_CHANNEL     1
#define AP_MAX_CONN    8

// --- WiFi Source / Upstream (WiFi rumah/kantor yang punya internet asli) ---
#define STA_SSID       "NAMA_WIFI_SOURCE_ANDA"
#define STA_PASSWORD   "PASSWORD_WIFI_SOURCE_ANDA"

// --- Splash Page ---
#define SPLASH_TITLE      "Selamat Datang"
#define SPLASH_SUBTITLE   "Silakan dengarkan pesan singkat kami sebelum lanjut"
#define AUDIO_FILENAME    "/welcome.mp3"   // file MP3 di LittleFS, disajikan lewat HTTP ke HP user

// --- Timing ---
#define PORTAL_SESSION_TIMEOUT_MS   (30UL * 60UL * 1000UL) // 30 menit akses internet per device setelah klik lanjut
