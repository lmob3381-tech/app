#ifndef OUI_TABLE_H
#define OUI_TABLE_H

#include <Arduino.h>

// Tabel OUI ringkas (24-bit pertama MAC address -> nama vendor).
// Tidak lengkap secara sengaja (sesuai kontrak: "cukup vendor umum").
// Format key: 3 byte pertama MAC, huruf besar, tanpa pemisah, contoh "AABBCC".
struct OuiEntry {
  const char* prefix; // 6 hex char, uppercase
  const char* vendor;
};

static const OuiEntry OUI_TABLE[] = {
  // Samsung
  {"CC07AB", "Samsung Electronics"},
  {"8C7712", "Samsung Electronics"},
  {"5C0A5B", "Samsung Electronics"},
  {"D0176A", "Samsung Electronics"},
  {"3C5AB4", "Samsung Electronics"},
  // Xiaomi
  {"F0B429", "Xiaomi"},
  {"78111B", "Xiaomi"},
  {"286C07", "Xiaomi"},
  {"64CC2E", "Xiaomi"},
  // Apple
  {"F0189E", "Apple"},
  {"A45E60", "Apple"},
  {"3C0754", "Apple"},
  {"D8BB2C", "Apple"},
  {"F41BA1", "Apple"},
  {"AC87A3", "Apple"},
  // TP-Link
  {"F4F26D", "TP-Link"},
  {"50C7BF", "TP-Link"},
  {"AC15A2", "TP-Link"},
  {"14CC20", "TP-Link"},
  // Espressif (perangkat ESP32/ESP8266 lain di jaringan)
  {"246F28", "Espressif"},
  {"30AEA4", "Espressif"},
  {"7C9EBD", "Espressif"},
  {"A020A6", "Espressif"},
  {"84F3EB", "Espressif"},
  // Huawei
  {"F87B7A", "Huawei"},
  {"00E0FC", "Huawei"},
  // Realtek (banyak dipakai router/IoT murah)
  {"525400", "Realtek"},
  // Amazon (Echo, Fire TV)
  {"F0272D", "Amazon"},
  {"74C246", "Amazon"},
  // Google (Chromecast, Nest)
  {"F4F5D8", "Google"},
  {"54609F", "Google"},
  // Xiaomi router
  {"34CE00", "Xiaomi"},
};

static const size_t OUI_TABLE_SIZE = sizeof(OUI_TABLE) / sizeof(OUI_TABLE[0]);

// Ambil vendor dari MAC address lengkap, format "AA:BB:CC:DD:EE:FF".
// Return nullptr kalau tidak ketemu (caller kirim JSON null).
inline const char* lookupVendor(const String& macUpper) {
  if (macUpper.length() < 8) return nullptr;

  // Ambil 3 byte pertama tanpa titik dua: "AA:BB:CC" -> "AABBCC"
  String prefix = "";
  prefix += macUpper.substring(0, 2);
  prefix += macUpper.substring(3, 5);
  prefix += macUpper.substring(6, 8);

  for (size_t i = 0; i < OUI_TABLE_SIZE; i++) {
    // Hanya bandingkan entri yang valid 6 karakter
    if (strlen(OUI_TABLE[i].prefix) == 6 && prefix.equalsIgnoreCase(OUI_TABLE[i].prefix)) {
      return OUI_TABLE[i].vendor;
    }
  }
  return nullptr;
}

#endif // OUI_TABLE_H
