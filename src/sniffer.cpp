#include "sniffer.h"
#include <WiFi.h>
#include <esp_wifi.h>

// variabel dari main.cpp yang diupdate sniffer ini
extern uint32_t deauthCount;
extern uint32_t totalPacketCount;
extern uint32_t packetsPerChannel[14];
extern uint8_t currentSnifferChannel;

// 802.11 management frame subtype
#define WIFI_MGMT_SUBTYPE_DEAUTH    0x0C
#define WIFI_MGMT_SUBTYPE_DISASSOC  0x0A

typedef struct __attribute__((packed)) {
  uint16_t frame_ctrl;
  uint16_t duration;
  uint8_t addr1[6];
  uint8_t addr2[6];
  uint8_t addr3[6];
  uint16_t seq_ctrl;
} wifi_ieee80211_mac_hdr_t;

typedef struct __attribute__((packed)) {
  wifi_ieee80211_mac_hdr_t hdr;
  uint8_t payload[0];
} wifi_ieee80211_packet_t;

static void IRAM_ATTR snifferCallback(void *buf, wifi_promiscuous_pkt_type_t type) {
  totalPacketCount++;

  if (currentSnifferChannel >= 1 && currentSnifferChannel <= 13) {
    packetsPerChannel[currentSnifferChannel]++;
  }

  if (type != WIFI_PKT_MGMT) return;

  const wifi_promiscuous_pkt_t *ppkt = (wifi_promiscuous_pkt_t*)buf;
  const wifi_ieee80211_packet_t *ipkt = (wifi_ieee80211_packet_t*)ppkt->payload;
  const wifi_ieee80211_mac_hdr_t *hdr = &ipkt->hdr;

  uint8_t frameSubType = (hdr->frame_ctrl >> 4) & 0x0F;
  uint8_t frameType = (hdr->frame_ctrl >> 2) & 0x03;

  // type 0 = management frame
  if (frameType == 0 &&
      (frameSubType == WIFI_MGMT_SUBTYPE_DEAUTH ||
       frameSubType == WIFI_MGMT_SUBTYPE_DISASSOC)) {
    deauthCount++;
  }
}

void snifferInit() {
  // nothing to pre-init
}

void snifferStart() {
  esp_wifi_set_promiscuous(true);
  esp_wifi_set_promiscuous_rx_cb(&snifferCallback);
  esp_wifi_set_channel(currentSnifferChannel, WIFI_SECOND_CHAN_NONE);
}

void snifferStop() {
  esp_wifi_set_promiscuous(false);
}
