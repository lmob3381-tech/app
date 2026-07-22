/*
 * ESP32 Network Tool
 * - WiFi scanner (SSID, RSSI, channel, enkripsi)
 * - Deploy / konfigurasi Access Point sendiri
 * - Scan jaringan lokal (ping sweep + port scan ringan, mirip nmap sederhana)
 * - Kontrol semua lewat web dashboard (diakses via IP AP ESP32)
 *
 * PENTING: Gunakan hanya pada jaringan / perangkat milik sendiri
 * atau yang sudah ada izin eksplisit untuk diuji.
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ESP32Ping.h>

WebServer server(80);

// ---------------- State ----------------
String apSSID = "ESP32-NetTool";
String apPASS = "12345678";

String staSSID = "";
bool staConnected = false;

unsigned long bootTime;

// ---------------- HTML ----------------
const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ESP32 Net Tool</title>
<style>
 body{font-family:sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:16px}
 h1{font-size:1.2rem}
 .tabs{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap}
 .tab{padding:8px 12px;background:#1e293b;border-radius:6px;cursor:pointer}
 .tab.active{background:#2563eb}
 .panel{display:none}
 .panel.active{display:block}
 table{width:100%;border-collapse:collapse;font-size:0.85rem}
 th,td{padding:6px;border-bottom:1px solid #334155;text-align:left}
 button{background:#2563eb;color:#fff;border:none;padding:8px 12px;border-radius:6px;cursor:pointer}
 input{padding:6px;border-radius:4px;border:1px solid #334155;background:#0f172a;color:#fff;margin:4px 0;width:90%}
 label{display:block;font-size:0.85rem;margin-top:8px}
 .status-box{background:#1e293b;padding:10px;border-radius:8px;margin-bottom:12px;font-size:0.85rem}
</style>
</head>
<body>
<h1>ESP32 Network Tool</h1>
<div class="status-box" id="status">Loading status...</div>
<div class="tabs">
 <div class="tab active" data-tab="wifiscan">WiFi Scan</div>
 <div class="tab" data-tab="apconfig">AP Config</div>
 <div class="tab" data-tab="netscan">Network Scan</div>
</div>

<div class="panel active" id="wifiscan">
 <button onclick="scanWifi()">Scan WiFi</button>
 <table id="wifiTable"><thead><tr><th>SSID</th><th>RSSI</th><th>Ch</th><th>Enc</th><th>BSSID</th><th></th></tr></thead><tbody></tbody></table>
 <h3>Connect ke jaringan</h3>
 <label>SSID</label><input id="connSsid">
 <label>Password</label><input id="connPass" type="password">
 <button onclick="connectWifi()">Connect</button>
 <div id="connResult"></div>
</div>

<div class="panel" id="apconfig">
 <label>AP SSID</label><input id="apSsid" value="ESP32-NetTool">
 <label>AP Password (min 8 char, kosongkan utk open)</label><input id="apPass" type="password">
 <label>Channel (1-13)</label><input id="apChannel" value="1">
 <label><input type="checkbox" id="apHidden" style="width:auto"> Hidden SSID</label>
 <button onclick="applyAP()">Apply AP Config</button>
 <div id="apResult"></div>
</div>

<div class="panel" id="netscan">
 <p>Scan perangkat aktif di subnet /24 tempat ESP32 berada. Bisa memakan waktu 1-3 menit.</p>
 <button onclick="scanNet()">Scan Network</button>
 <div id="netProgress"></div>
 <table id="netTable"><thead><tr><th>IP</th><th>Open Ports</th></tr></thead><tbody></tbody></table>
</div>

<script>
function showTab(name){
 document.querySelectorAll('.tab').forEach(t=>t.classList.toggle('active', t.dataset.tab===name));
 document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('active', p.id===name));
}
document.querySelectorAll('.tab').forEach(t=>t.onclick=()=>showTab(t.dataset.tab));

async function loadStatus(){
 try{
  const r = await fetch('/status'); const d = await r.json();
  let s = `AP: ${d.apSSID} (${d.apIP}) | `;
  s += d.staConnected ? `STA: ${d.staSSID} (${d.staIP}, RSSI ${d.rssi}dBm)` : 'STA: belum terhubung';
  s += ` | Uptime: ${d.uptime}s | Heap: ${Math.round(d.freeHeap/1024)}KB`;
  document.getElementById('status').textContent = s;
 }catch(e){}
}
loadStatus(); setInterval(loadStatus, 5000);

async function scanWifi(){
 const tbody = document.querySelector('#wifiTable tbody');
 tbody.innerHTML = '<tr><td colspan=6>Scanning...</td></tr>';
 const r = await fetch('/scan'); const list = await r.json();
 tbody.innerHTML = '';
 list.sort((a,b)=>b.rssi-a.rssi);
 list.forEach(n=>{
  const tr = document.createElement('tr');
  const safeSsid = (n.ssid||'').replace(/'/g,"");
  tr.innerHTML = `<td>${n.ssid||'(hidden)'}</td><td>${n.rssi}</td><td>${n.channel}</td><td>${n.enc}</td><td>${n.bssid}</td>
   <td><button onclick="document.getElementById('connSsid').value='${safeSsid}'">Use</button></td>`;
  tbody.appendChild(tr);
 });
}

async function connectWifi(){
 const ssid = document.getElementById('connSsid').value;
 const pass = document.getElementById('connPass').value;
 document.getElementById('connResult').textContent = 'Connecting...';
 const r = await fetch('/connect', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:`ssid=${encodeURIComponent(ssid)}&pass=${encodeURIComponent(pass)}`});
 const d = await r.json();
 document.getElementById('connResult').textContent = d.connected ? `Connected! IP: ${d.ip}` : 'Connection failed';
 loadStatus();
}

async function applyAP(){
 const ssid = document.getElementById('apSsid').value;
 const pass = document.getElementById('apPass').value;
 const ch = document.getElementById('apChannel').value;
 const hidden = document.getElementById('apHidden').checked ? '1' : '0';
 document.getElementById('apResult').textContent = 'Applying...';
 const r = await fetch('/apconfig', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:`ssid=${encodeURIComponent(ssid)}&pass=${encodeURIComponent(pass)}&channel=${ch}&hidden=${hidden}`});
 const d = await r.json();
 document.getElementById('apResult').textContent = d.ok ? `AP updated. IP: ${d.ip}` : 'Gagal update AP';
}

async function scanNet(){
 document.getElementById('netProgress').textContent = 'Scanning network, mohon tunggu...';
 const tbody = document.querySelector('#netTable tbody');
 tbody.innerHTML = '';
 const r = await fetch('/netscan'); const list = await r.json();
 document.getElementById('netProgress').textContent = `Selesai. ${list.length} host aktif ditemukan.`;
 list.forEach(h=>{
  const tr = document.createElement('tr');
  tr.innerHTML = `<td>${h.ip}</td><td>${h.ports.join(', ')||'-'}</td>`;
  tbody.appendChild(tr);
 });
}
</script>
</body>
</html>
)rawliteral";

// ---------------- Helpers ----------------
String encTypeStr(wifi_auth_mode_t enc) {
  switch (enc) {
    case WIFI_AUTH_OPEN: return "Open";
    case WIFI_AUTH_WEP: return "WEP";
    case WIFI_AUTH_WPA_PSK: return "WPA";
    case WIFI_AUTH_WPA2_PSK: return "WPA2";
    case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2";
    case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2-Ent";
    case WIFI_AUTH_WPA3_PSK: return "WPA3";
    case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2/WPA3";
    default: return "Unknown";
  }
}

// ---------------- Handlers ----------------
void handleRoot() {
  server.send_P(200, "text/html", INDEX_HTML);
}

void handleScan() {
  int n = WiFi.scanNetworks(false, true);
  String json = "[";
  for (int i = 0; i < n; i++) {
    if (i) json += ",";
    json += "{";
    json += "\"ssid\":\"" + WiFi.SSID(i) + "\",";
    json += "\"rssi\":" + String(WiFi.RSSI(i)) + ",";
    json += "\"channel\":" + String(WiFi.channel(i)) + ",";
    json += "\"bssid\":\"" + WiFi.BSSIDstr(i) + "\",";
    json += "\"enc\":\"" + encTypeStr(WiFi.encryptionType(i)) + "\"";
    json += "}";
  }
  json += "]";
  WiFi.scanDelete();
  server.send(200, "application/json", json);
}

void handleConnect() {
  if (!server.hasArg("ssid")) {
    server.send(400, "application/json", "{\"error\":\"missing ssid\"}");
    return;
  }
  staSSID = server.arg("ssid");
  String pass = server.arg("pass");
  WiFi.begin(staSSID.c_str(), pass.c_str());
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
    delay(200);
  }
  staConnected = (WiFi.status() == WL_CONNECTED);
  String resp = "{\"connected\":" + String(staConnected ? "true" : "false");
  if (staConnected) resp += ",\"ip\":\"" + WiFi.localIP().toString() + "\"";
  resp += "}";
  server.send(200, "application/json", resp);
}

void handleAPConfig() {
  if (!server.hasArg("ssid")) {
    server.send(400, "application/json", "{\"error\":\"missing ssid\"}");
    return;
  }
  apSSID = server.arg("ssid");
  apPASS = server.arg("pass");
  int ch = server.hasArg("channel") ? server.arg("channel").toInt() : 1;
  if (ch < 1 || ch > 13) ch = 1;
  bool hidden = server.hasArg("hidden") && server.arg("hidden") == "1";

  bool ok;
  if (apPASS.length() >= 8) {
    ok = WiFi.softAP(apSSID.c_str(), apPASS.c_str(), ch, hidden);
  } else {
    ok = WiFi.softAP(apSSID.c_str(), NULL, ch, hidden);
  }
  String resp = "{\"ok\":" + String(ok ? "true" : "false") +
                ",\"ip\":\"" + WiFi.softAPIP().toString() + "\"}";
  server.send(200, "application/json", resp);
}

void handleNetScan() {
  IPAddress localIP = staConnected ? WiFi.localIP() : WiFi.softAPIP();
  String json = "[";
  bool first = true;
  const int ports[] = {21, 22, 23, 25, 53, 80, 110, 139, 143, 443, 445, 3389, 8080};
  const int numPorts = sizeof(ports) / sizeof(ports[0]);

  for (int host = 1; host < 255; host++) {
    IPAddress target(localIP[0], localIP[1], localIP[2], host);
    if (target == localIP) continue;

    bool alive = Ping.ping(target, 1);
    if (alive) {
      if (!first) json += ",";
      first = false;
      json += "{\"ip\":\"" + target.toString() + "\",\"ports\":[";
      bool firstPort = true;
      for (int i = 0; i < numPorts; i++) {
        WiFiClient client;
        if (client.connect(target, ports[i], 150)) {
          if (!firstPort) json += ",";
          json += String(ports[i]);
          firstPort = false;
          client.stop();
        }
      }
      json += "]}";
    }
    yield(); // jaga watchdog & responsivitas WiFi stack
  }
  json += "]";
  server.send(200, "application/json", json);
}

void handleStatus() {
  String json = "{";
  json += "\"apSSID\":\"" + apSSID + "\",";
  json += "\"apIP\":\"" + WiFi.softAPIP().toString() + "\",";
  json += "\"staConnected\":" + String(staConnected ? "true" : "false") + ",";
  if (staConnected) {
    json += "\"staIP\":\"" + WiFi.localIP().toString() + "\",";
    json += "\"staSSID\":\"" + staSSID + "\",";
    json += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  }
  json += "\"uptime\":" + String((millis() - bootTime) / 1000) + ",";
  json += "\"freeHeap\":" + String(ESP.getFreeHeap());
  json += "}";
  server.send(200, "application/json", json);
}

// ---------------- Setup / Loop ----------------
void setup() {
  Serial.begin(115200);
  bootTime = millis();

  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP(apSSID.c_str(), apPASS.c_str());
  delay(200);
  Serial.print("AP IP: ");
  Serial.println(WiFi.softAPIP());

  server.on("/", handleRoot);
  server.on("/scan", handleScan);
  server.on("/connect", HTTP_POST, handleConnect);
  server.on("/apconfig", HTTP_POST, handleAPConfig);
  server.on("/netscan", handleNetScan);
  server.on("/status", handleStatus);
  server.begin();
}

void loop() {
  server.handleClient();
}
