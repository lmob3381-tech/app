#include "beacon.h"
#include <WiFi.h>
#include <esp_wifi.h>

// Template raw 802.11 beacon frame.
// Byte offset penting:
//  10-15 : source address (MAC pengirim / BSSID)
//  16-21 : BSSID
//  36    : awal SSID tag (tag number 0)
//  37    : SSID length
//  38..  : SSID bytes
static uint8_t beaconPacket[128] = {
  0x80, 0x00,                          // Frame Control: beacon
  0x00, 0x00,                          // Duration
  0xff, 0xff, 0xff, 0xff, 0xff, 0xff,  // Destination: broadcast
  0x02, 0x00, 0x00, 0x00, 0x00, 0x00,  // Source address (akan diisi random)
  0x02, 0x00, 0x00, 0x00, 0x00, 0x00,  // BSSID (sama dgn source)
  0x00, 0x00,                          // Seq/frag number

  // Fixed parameters (12 bytes)
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Timestamp
  0x64, 0x00,                          // Beacon interval (100 TU)
  0x01, 0x04,                          // Capability info (ESS + short preamble)

  // Tag: SSID (diisi dinamis)
  0x00, 0x00,                          // Tag number 0 (SSID), length placeholder

  // sisanya (rate, channel dll) ditambahkan setelah SSID secara dinamis
};

#define MAX_SSIDS 15
static String ssidList[MAX_SSIDS];
static int ssidCount = 0;
static int currentIdx = 0;
static bool active = false;
static unsigned long lastSend = 0;
static const unsigned long SEND_INTERVAL_MS = 120; // jeda antar beacon (per SSID)
static uint8_t beaconChannel = 1;

void beaconInit() {
  // channel akan di-set saat start
}

void beaconSetSSIDList(String *list, int count) {
  ssidCount = min(count, MAX_SSIDS);
  for (int i = 0; i < ssidCount; i++) {
    ssidList[i] = list[i];
  }
  currentIdx = 0;
}

static void sendBeaconFor(int idx) {
  if (idx < 0 || idx >= ssidCount) return;

  String ssid = ssidList[idx];
  int ssidLen = ssid.length();
  if (ssidLen > 32) ssidLen = 32;

  // buat MAC acak tapi konsisten per index (locally administered)
  uint8_t mac[6];
  mac[0] = 0x02; // locally administered bit
  mac[1] = 0x1A;
  mac[2] = 0xE5;
  mac[3] = (uint8_t)(idx * 17 + 3);
  mac[4] = (uint8_t)(idx * 29 + 7);
  mac[5] = (uint8_t)(idx * 41 + 11);

  memcpy(&beaconPacket[10], mac, 6); // source
  memcpy(&beaconPacket[16], mac, 6); // bssid

  // isi SSID tag
  beaconPacket[36] = 0x00;           // tag number: SSID
  beaconPacket[37] = ssidLen;        // tag length
  memcpy(&beaconPacket[38], ssid.c_str(), ssidLen);

  int offset = 38 + ssidLen;

  // Supported rates tag
  uint8_t rates[] = {0x01, 0x08, 0x82, 0x84, 0x8b, 0x96, 0x24, 0x30, 0x48, 0x6c};
  memcpy(&beaconPacket[offset], rates, sizeof(rates));
  offset += sizeof(rates);

  // DS Parameter Set (channel)
  beaconPacket[offset++] = 0x03; // tag: DS param
  beaconPacket[offset++] = 0x01; // length
  beaconPacket[offset++] = beaconChannel;

  int packetLen = offset;

  esp_wifi_80211_tx(WIFI_IF_AP, beaconPacket, packetLen, false);
}

void beaconStart() {
  if (ssidCount == 0) return;
  active = true;
  currentIdx = 0;
  lastSend = 0;
}

void beaconStop() {
  active = false;
}

void beaconTick() {
  if (!active || ssidCount == 0) return;
  unsigned long now = millis();
  if (now - lastSend < SEND_INTERVAL_MS) return;
  lastSend = now;

  sendBeaconFor(currentIdx);
  currentIdx = (currentIdx + 1) % ssidCount;

  // sesekali gonta-ganti channel biar sebaran mirip AP asli (opsional,
  // dinonaktifkan default supaya tidak konflik dgn sniffer channel hop)
}
