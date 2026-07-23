#include "pingtool.h"
#include <lwip/inet.h>
#include <lwip/netdb.h>
#include <ping/ping_sock.h>
#include <ArduinoJson.h>

static esp_ping_handle_t pingHandle = NULL;
static bool pingRunning = false;
static bool pingHasResult = false;

struct PingStat {
  int sent = 0;
  int received = 0;
  int lost = 0;
  float minMs = 0;
  float maxMs = 0;
  float avgMs = 0;
  String target = "";
};
static PingStat stat;

// akumulator sementara
static uint32_t totalTimeMs = 0;
static uint32_t minTimeMs = 0xFFFFFFFF;
static uint32_t maxTimeMs = 0;

static void onPingSuccess(esp_ping_handle_t hdl, void *args) {
  uint32_t elapsed_time;
  esp_ping_get_profile(hdl, ESP_PING_PROF_TIMEGAP, &elapsed_time, sizeof(elapsed_time));
  stat.received++;
  totalTimeMs += elapsed_time;
  if (elapsed_time < minTimeMs) minTimeMs = elapsed_time;
  if (elapsed_time > maxTimeMs) maxTimeMs = elapsed_time;
}

static void onPingTimeout(esp_ping_handle_t hdl, void *args) {
  stat.lost++;
}

static void onPingEnd(esp_ping_handle_t hdl, void *args) {
  uint32_t transmitted, received;
  esp_ping_get_profile(hdl, ESP_PING_PROF_REQUEST, &transmitted, sizeof(transmitted));
  esp_ping_get_profile(hdl, ESP_PING_PROF_REPLY, &received, sizeof(received));
  stat.sent = transmitted;
  if (stat.received > 0) {
    stat.avgMs = (float)totalTimeMs / stat.received;
    stat.minMs = (float)minTimeMs;
    stat.maxMs = (float)maxTimeMs;
  }
  pingRunning = false;
  pingHasResult = true;
  esp_ping_delete_session(hdl);
  pingHandle = NULL;
}

void pingToolInit() {
  // nothing
}

void pingToolStart(String host, int count) {
  if (pingRunning) {
    if (pingHandle) {
      esp_ping_stop(pingHandle);
      esp_ping_delete_session(pingHandle);
      pingHandle = NULL;
    }
  }

  ip_addr_t targetAddr;
  struct hostent *he = gethostbyname(host.c_str());
  if (he == NULL) {
    stat = PingStat();
    stat.target = host + " (DNS lookup gagal)";
    pingHasResult = true;
    return;
  }
  memcpy(&targetAddr.u_addr.ip4.addr, he->h_addr_list[0], 4);
  targetAddr.type = IPADDR_TYPE_V4;

  stat = PingStat();
  stat.target = host;
  totalTimeMs = 0;
  minTimeMs = 0xFFFFFFFF;
  maxTimeMs = 0;

  esp_ping_config_t config = ESP_PING_DEFAULT_CONFIG();
  config.target_addr = targetAddr;
  config.count = count > 0 ? count : 4;
  config.interval_ms = 500;
  config.timeout_ms = 1000;
  config.data_size = 32;

  esp_ping_callbacks_t cbs;
  cbs.on_ping_success = onPingSuccess;
  cbs.on_ping_timeout = onPingTimeout;
  cbs.on_ping_end = onPingEnd;
  cbs.cb_args = NULL;

  esp_ping_new_session(&config, &cbs, &pingHandle);
  esp_ping_start(pingHandle);
  pingRunning = true;
  pingHasResult = false;
}

void pingToolLoop() {
  // ping berjalan async via callback IDF, tidak perlu polling manual
}

String pingToolGetResultJson() {
  DynamicJsonDocument doc(512);
  doc["running"] = pingRunning;
  doc["hasResult"] = pingHasResult;
  doc["target"] = stat.target;
  doc["sent"] = stat.sent;
  doc["received"] = stat.received;
  doc["lost"] = stat.lost;
  doc["minMs"] = stat.minMs;
  doc["maxMs"] = stat.maxMs;
  doc["avgMs"] = stat.avgMs;
  String out;
  serializeJson(doc, out);
  return out;
}
