#pragma once

// ===================== KONFIGURASI =====================

// --- WiFi Access Point (yang dipancarkan ESP32 ke user) ---
#define AP_SSID        "Free-WiFi-Splash"
#define AP_PASSWORD    ""              // kosongkan "" untuk open network, atau isi min 8 char
#define AP_CHANNEL     1
#define AP_MAX_CONN    8

// --- Splash Page ---
#define SPLASH_TITLE      "Selamat Datang"
#define SPLASH_SUBTITLE   "Tekan tombol di bawah untuk mendengarkan pesan singkat kami"
#define AUDIO_FILENAME    "/welcome.mp3"   // file MP3 di LittleFS, disajikan lewat HTTP ke HP user
