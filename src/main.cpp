/*
 * ESP32 Network Tool
 * - WiFi scanner (SSID, RSSI, channel, enkripsi)
 * - Deploy / konfigurasi Access Point sendiri
 * - Daftar perangkat yang terhubung ke AP ESP32 (MAC, IP, vendor)
 * - Scan jaringan lokal: ping sweep + port scan + MAC/vendor (mirip nmap ringan)
 * - Kontrol semua lewat web dashboard bergaya console/terminal
 *
 * PENTING: Gunakan hanya pada jaringan / perangkat milik sendiri
 * atau yang sudah ada izin eksplisit untuk diuji.
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ESP32Ping.h>
#include "esp_wifi.h"
#include "esp_netif.h"
#include "lwip/etharp.h"
#include "lwip/netif.h"

WebServer server(80);

// ---------------- State ----------------
String apSSID = "ESP32-NetTool";
String apPASS = "12345678";

String staSSID = "";
bool staConnected = false;

unsigned long bootTime;

// ---------------- OUI vendor table (best-effort, offline) ----------------
struct OuiEntry { const char* prefix; const char* vendor; };
static const OuiEntry ouiTable[] = {
  {"3C:5A:B4","Google"}, {"F4:F5:D8","Google"}, {"18:B4:30","Nest/Google"},
  {"A4:77:33","Espressif"}, {"24:0A:C4","Espressif"}, {"AC:67:B2","Espressif"}, {"7C:9E:BD","Espressif"},
  {"DC:A6:32","Raspberry Pi"}, {"B8:27:EB","Raspberry Pi"}, {"E4:5F:01","Raspberry Pi"}, {"D8:3A:DD","Raspberry Pi"},
  {"F0:18:98","Apple"}, {"AC:BC:32","Apple"}, {"3C:07:54","Apple"}, {"F4:5C:89","Apple"}, {"D0:81:7A","Apple"}, {"A4:83:E7","Apple"},
  {"64:16:66","Amazon"}, {"44:65:0D","Amazon"}, {"F0:27:2D","Amazon"}, {"68:37:E9","Amazon"},
  {"AC:63:BE","Samsung"}, {"5C:0A:5B","Samsung"}, {"E8:50:8B","Samsung"}, {"88:32:9B","Samsung"}, {"CC:07:AB","Samsung"},
  {"A0:CE:C8","Xiaomi"}, {"78:11:DC","Xiaomi"}, {"64:B4:73","Xiaomi"}, {"50:8F:4C","Xiaomi"}, {"28:6C:07","Xiaomi"},
  {"B0:BE:76","TP-Link"}, {"50:C7:BF","TP-Link"}, {"C4:6E:1F","TP-Link"}, {"98:DA:C4","Tenda"},
  {"00:1D:0F","Cisco"}, {"00:26:99","Cisco"}, {"D8:9E:F3","Huawei"}, {"00:E0:FC","Huawei"}, {"48:8F:5A","Huawei"},
  {"3C:52:82","Sonos"}, {"5C:AA:FD","Sonos"}, {"18:69:D8","Ring"}, {"00:0C:29","VMware"}, {"08:00:27","VirtualBox"},
};
static const int ouiTableSize = sizeof(ouiTable) / sizeof(ouiTable[0]);

String macToStr(const uint8_t* mac) {
  char buf[18];
  sprintf(buf, "%02X:%02X:%02X:%02X:%02X:%02X", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
  return String(buf);
}

String ouiLookup(const String& macUpper) {
  String prefix = macUpper.substring(0, 8); // "XX:XX:XX"
  for (int i = 0; i < ouiTableSize; i++) {
    if (prefix.equalsIgnoreCase(ouiTable[i].prefix)) return String(ouiTable[i].vendor);
  }
  return "Unknown";
}

// best-effort MAC lookup via lwIP ARP cache (works for hosts already contacted, e.g. after ping)
bool getMacForIp(IPAddress ip, uint8_t* macOut) {
  ip4_addr_t target;
  target.addr = (uint32_t)ip;
  struct eth_addr* ethRet = nullptr;
  const ip4_addr_t* ipRet = nullptr;
  struct netif* nif = netif_default;
  if (!nif) return false;
  err_t err = etharp_find_addr(nif, &target, &ethRet, &ipRet);
  if (err >= 0 && ethRet != nullptr) {
    memcpy(macOut, ethRet->addr, 6);
    return true;
  }
  return false;
}

// ---------------- HTML ----------------
const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>esp32-net-tool</title>
<style>
:root{
  --bg:#0a0c08; --bg-panel:#12150d; --bg-elevated:#171b11;
  --amber:#ffb300; --amber-dim:#8a6200; --amber-glow:rgba(255,179,0,0.25);
  --green:#6fd68a; --red:#ff5f5f; --text:#dfe2d0; --text-dim:#767c6a; --border:#262b1c;
}
*{box-sizing:border-box}
html,body{margin:0;padding:0}
body{
  background:var(--bg);
  background-image:radial-gradient(circle at 50% 0%, rgba(255,179,0,0.06), transparent 60%);
  color:var(--text);
  font-family:ui-monospace,"Cascadia Code","JetBrains Mono","SFMono-Regular",Menlo,Consolas,monospace;
  font-size:14px; min-height:100vh; padding-bottom:40px;
}
body::before{
  content:""; position:fixed; inset:0; pointer-events:none; z-index:9999;
  background:repeating-linear-gradient(to bottom, rgba(255,255,255,0.015) 0px, rgba(255,255,255,0.015) 1px, transparent 1px, transparent 3px);
  mix-blend-mode:overlay;
}
header.console{
  position:sticky; top:0; z-index:10;
  background:rgba(10,12,8,0.92); backdrop-filter:blur(6px);
  border-bottom:1px solid var(--border);
  padding:10px 16px; display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:10px;
}
.brand{display:flex; align-items:center; gap:10px}
.brand .mark{width:10px;height:10px;border-radius:50%;background:var(--amber);box-shadow:0 0 8px var(--amber-glow);animation:pulse 2.4s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
.brand h1{font-size:14px;letter-spacing:.08em;text-transform:uppercase;margin:0;color:var(--amber)}
.brand .sub{color:var(--text-dim);font-size:11px}
.readouts{display:flex;gap:16px;flex-wrap:wrap;font-size:11px;color:var(--text-dim);align-items:center}
.readouts b{color:var(--text);font-weight:600}
.scope{width:120px;height:28px}
main{max-width:960px;margin:0 auto;padding:16px}
.tabs{display:flex;gap:6px;margin-bottom:16px;flex-wrap:wrap}
.tab{background:transparent;border:1px solid var(--border);color:var(--text-dim);padding:8px 14px;font-family:inherit;font-size:12px;letter-spacing:.05em;text-transform:uppercase;cursor:pointer;border-radius:3px;transition:.15s}
.tab:hover{color:var(--text);border-color:var(--amber-dim)}
.tab.active{color:var(--amber);border-color:var(--amber);box-shadow:inset 0 0 12px var(--amber-glow)}
.panel{display:none;background:var(--bg-panel);border:1px solid var(--border);border-radius:6px;padding:18px}
.panel.active{display:block;animation:fadein .2s ease}
@keyframes fadein{from{opacity:0;transform:translateY(3px)}to{opacity:1;transform:none}}
.panel h2{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--amber);margin:0 0 4px}
.panel .hint{color:var(--text-dim);font-size:12px;margin:0 0 14px}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:end;margin-bottom:14px}
.field{display:flex;flex-direction:column;gap:4px;flex:1;min-width:140px}
.field label{font-size:11px;color:var(--text-dim);text-transform:uppercase;letter-spacing:.05em}
input[type=text],input[type=password],input[type=number]{background:var(--bg);border:1px solid var(--border);color:var(--text);padding:8px 10px;font-family:inherit;font-size:13px;border-radius:3px;width:100%}
input:focus{outline:none;border-color:var(--amber);box-shadow:0 0 0 2px var(--amber-glow)}
.btn{background:transparent;border:1px solid var(--amber-dim);color:var(--amber);padding:8px 16px;font-family:inherit;font-size:12px;letter-spacing:.06em;text-transform:uppercase;cursor:pointer;border-radius:3px;transition:.15s;white-space:nowrap;display:inline-flex;align-items:center;gap:8px}
.btn:hover{background:var(--amber-glow);border-color:var(--amber)}
.btn.ghost{border-color:var(--border);color:var(--text-dim)}
table{width:100%;border-collapse:collapse;font-size:12.5px;margin-top:6px}
th{text-align:left;color:var(--text-dim);font-weight:500;text-transform:uppercase;font-size:10.5px;letter-spacing:.05em;padding:8px 6px;border-bottom:1px solid var(--border)}
td{padding:8px 6px;border-bottom:1px solid rgba(255,255,255,0.03)}
tr:hover td{background:rgba(255,179,0,0.03)}
.bars{display:inline-flex;gap:2px;align-items:flex-end;height:14px}
.bars span{width:3px;background:var(--border)}
.bars span.on{background:var(--amber)}
.bars span:nth-child(1){height:4px}.bars span:nth-child(2){height:7px}.bars span:nth-child(3){height:10px}.bars span:nth-child(4){height:14px}
.badge{display:inline-block;padding:2px 8px;border-radius:20px;font-size:10.5px;border:1px solid var(--border);color:var(--text-dim)}
.badge.open{color:var(--green);border-color:rgba(111,214,138,.4)}
.badge.enc{color:var(--amber);border-color:var(--amber-dim)}
.radar{width:14px;height:14px;border-radius:50%;background:conic-gradient(var(--amber),transparent 70%);animation:spin 1s linear infinite;display:none}
.radar.on{display:inline-block}
@keyframes spin{to{transform:rotate(360deg)}}
@media (prefers-reduced-motion:reduce){.radar{animation:none}.brand .mark{animation:none}}
.log{max-height:160px;overflow-y:auto;font-size:11.5px;color:var(--text-dim);border-top:1px solid var(--border);margin-top:16px;padding-top:10px}
.log div{padding:3px 0}
.log time{color:var(--amber-dim);margin-right:8px}
.toast-wrap{position:fixed;bottom:16px;right:16px;display:flex;flex-direction:column;gap:8px;z-index:999}
.toast{background:var(--bg-elevated);border:1px solid var(--amber-dim);color:var(--text);padding:10px 14px;border-radius:4px;font-size:12px;box-shadow:0 4px 20px rgba(0,0,0,.4);animation:slidein .2s ease;max-width:280px}
.toast.error{border-color:var(--red)}
@keyframes slidein{from{opacity:0;transform:translateX(10px)}to{opacity:1;transform:none}}
.empty{color:var(--text-dim);font-size:12px;padding:20px 0;text-align:center}
footer{text-align:center;color:var(--text-dim);font-size:10.5px;margin-top:24px;letter-spacing:.05em}
@media (max-width:600px){.readouts{display:none}main{padding:10px}}
</style>
</head>
<body>
<div class="toast-wrap" id="toasts"></div>
<header class="console">
  <div class="brand">
    <span class="mark"></span>
    <div>
      <h1>esp32-net-tool</h1>
      <div class="sub">wifi recon &amp; local scan console</div>
    </div>
  </div>
  <div class="readouts">
    <div>AP <b id="roApSsid">-</b> <span id="roApIp"></span></div>
    <div>STA <b id="roSta">-</b></div>
    <div>CLIENTS <b id="roClients">0</b></div>
    <div>UPTIME <b id="roUptime">0s</b></div>
    <div>HEAP <b id="roHeap">0KB</b></div>
    <canvas id="scope" class="scope" width="120" height="28"></canvas>
  </div>
</header>

<main>
 <div class="tabs">
   <button class="tab active" data-tab="wifiscan">WiFi Scan</button>
   <button class="tab" data-tab="apclients">AP Clients</button>
   <button class="tab" data-tab="netscan">Network Scan</button>
   <button class="tab" data-tab="apconfig">AP Config</button>
 </div>

 <section class="panel active" id="wifiscan">
   <h2>Nearby Networks</h2>
   <p class="hint">Deteksi access point di sekitar: sinyal, channel, dan jenis enkripsi.</p>
   <div class="row"><button class="btn" onclick="scanWifi()">Scan <span class="radar" id="radarWifi"></span></button></div>
   <table>
     <thead><tr><th>Signal</th><th>SSID</th><th>dBm</th><th>Ch</th><th>Security</th><th>BSSID</th><th></th></tr></thead>
     <tbody id="wifiBody"><tr><td colspan="7" class="empty">Belum ada data. Klik Scan.</td></tr></tbody>
   </table>
   <div class="row" style="margin-top:16px">
     <div class="field"><label>SSID</label><input type="text" id="connSsid"></div>
     <div class="field"><label>Password</label><input type="password" id="connPass"></div>
     <button class="btn" onclick="connectWifi()">Connect</button>
   </div>
 </section>

 <section class="panel" id="apclients">
   <h2>Connected to This AP</h2>
   <p class="hint">Perangkat yang sedang terhubung ke hotspot ESP32 ini.</p>
   <div class="row"><button class="btn" onclick="scanApClients()">Refresh <span class="radar" id="radarAp"></span></button></div>
   <table><thead><tr><th>MAC</th><th>IP</th><th>Vendor (perkiraan)</th></tr></thead>
   <tbody id="apClientsBody"><tr><td colspan="3" class="empty">Belum ada data.</td></tr></tbody></table>
 </section>

 <section class="panel" id="netscan">
   <h2>Local Network Scan</h2>
   <p class="hint">Ping sweep + port scan ringan ke seluruh subnet (mirip nmap sederhana). Bisa makan waktu 1-3 menit.</p>
   <div class="row">
     <button class="btn" onclick="scanNet()">Scan Network <span class="radar" id="radarNet"></span></button>
     <button class="btn ghost" onclick="exportCsv()">Export CSV</button>
   </div>
   <div id="netProgress" class="hint"></div>
   <table><thead><tr><th>IP</th><th>MAC</th><th>Vendor</th><th>Latency</th><th>Open Ports</th></tr></thead>
   <tbody id="netBody"><tr><td colspan="5" class="empty">Belum ada data.</td></tr></tbody></table>
 </section>

 <section class="panel" id="apconfig">
   <h2>Access Point Configuration</h2>
   <p class="hint">Deploy / ubah hotspot ESP32 sesuai kebutuhan.</p>
   <div class="row">
     <div class="field"><label>SSID</label><input type="text" id="apSsid" value="ESP32-NetTool"></div>
     <div class="field"><label>Password (min 8, kosongkan = open)</label><input type="password" id="apPass"></div>
   </div>
   <div class="row">
     <div class="field"><label>Channel</label><input type="number" id="apChannel" value="1" min="1" max="13"></div>
     <div class="field" style="flex:0 0 auto"><label>&nbsp;</label><label style="text-transform:none;font-size:12px;color:var(--text)"><input type="checkbox" id="apHidden" style="width:auto;margin-right:6px">Hidden SSID</label></div>
   </div>
   <button class="btn" onclick="applyAP()">Apply Configuration</button>
 </section>

 <div class="log" id="log"></div>
</main>
<footer>esp32-net-tool &middot; gunakan hanya pada jaringan milik sendiri</footer>

<script>
const toastsEl = document.getElementById('toasts');
function toast(msg, isError){
  const el = document.createElement('div');
  el.className = 'toast' + (isError ? ' error' : '');
  el.textContent = msg;
  toastsEl.appendChild(el);
  setTimeout(()=>el.remove(), 4000);
}
const logEl = document.getElementById('log');
function logEvent(msg){
  const div = document.createElement('div');
  const t = new Date().toLocaleTimeString();
  div.innerHTML = `<time>${t}</time>${msg}`;
  logEl.prepend(div);
  while(logEl.children.length > 25) logEl.removeChild(logEl.lastChild);
}

document.querySelectorAll('.tab').forEach(t=>{
  t.onclick = () => {
    document.querySelectorAll('.tab').forEach(x=>x.classList.toggle('active', x===t));
    document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('active', p.id===t.dataset.tab));
  };
});

function bars(rssi){
  let level = rssi > -50 ? 4 : rssi > -60 ? 3 : rssi > -70 ? 2 : rssi > -80 ? 1 : 0;
  let html = '<span class="bars">';
  for(let i=1;i<=4;i++) html += `<span class="${i<=level?'on':''}"></span>`;
  return html + '</span>';
}

const rssiHistory = [];
const scopeCanvas = document.getElementById('scope');
const scopeCtx = scopeCanvas.getContext('2d');
function drawScope(){
  const w = scopeCanvas.width, h = scopeCanvas.height;
  scopeCtx.clearRect(0,0,w,h);
  scopeCtx.strokeStyle = '#ffb300';
  scopeCtx.lineWidth = 1;
  scopeCtx.beginPath();
  if(rssiHistory.length < 2){
    scopeCtx.moveTo(0,h/2); scopeCtx.lineTo(w,h/2);
  } else {
    const min=-90, max=-30;
    rssiHistory.forEach((v,i)=>{
      const x = (i/(rssiHistory.length-1))*w;
      const norm = Math.min(1,Math.max(0,(v-min)/(max-min)));
      const y = h - norm*h;
      i===0 ? scopeCtx.moveTo(x,y) : scopeCtx.lineTo(x,y);
    });
  }
  scopeCtx.stroke();
}

function fmtUptime(s){
  const h = Math.floor(s/3600), m = Math.floor((s%3600)/60), sec = s%60;
  return `${h}h${m}m${sec}s`;
}

async function loadStatus(){
  try{
    const r = await fetch('/status'); const d = await r.json();
    document.getElementById('roApSsid').textContent = d.apSSID;
    document.getElementById('roApIp').textContent = '('+d.apIP+')';
    document.getElementById('roSta').textContent = d.staConnected ? `${d.staSSID} ${d.rssi}dBm` : 'offline';
    document.getElementById('roClients').textContent = d.apClients;
    document.getElementById('roUptime').textContent = fmtUptime(d.uptime);
    document.getElementById('roHeap').textContent = Math.round(d.freeHeap/1024)+'KB';
    if(d.staConnected){
      rssiHistory.push(d.rssi);
      if(rssiHistory.length>30) rssiHistory.shift();
    }
    drawScope();
  }catch(e){}
}
loadStatus(); setInterval(loadStatus, 4000);

async function scanWifi(){
  document.getElementById('radarWifi').classList.add('on');
  const tbody = document.getElementById('wifiBody');
  tbody.innerHTML = '<tr><td colspan="7" class="empty">Scanning...</td></tr>';
  try{
    const r = await fetch('/scan'); const list = await r.json();
    list.sort((a,b)=>b.rssi-a.rssi);
    tbody.innerHTML = list.length ? '' : '<tr><td colspan="7" class="empty">Tidak ada jaringan ditemukan.</td></tr>';
    list.forEach(n=>{
      const tr = document.createElement('tr');
      const safeSsid = (n.ssid||'(hidden)').replace(/'/g,"");
      const encBadge = n.enc==='Open' ? `<span class="badge open">Open</span>` : `<span class="badge enc">${n.enc}</span>`;
      tr.innerHTML = `<td>${bars(n.rssi)}</td><td>${n.ssid||'(hidden)'}</td><td>${n.rssi}</td><td>${n.channel}</td><td>${encBadge}</td><td>${n.bssid}</td>
       <td><button class="btn ghost" onclick="document.getElementById('connSsid').value='${safeSsid}'">Use</button></td>`;
      tbody.appendChild(tr);
    });
    logEvent(`WiFi scan selesai: ${list.length} jaringan ditemukan`);
  }catch(e){ toast('Scan gagal', true); }
  document.getElementById('radarWifi').classList.remove('on');
}

async function connectWifi(){
  const ssid = document.getElementById('connSsid').value;
  const pass = document.getElementById('connPass').value;
  if(!ssid){ toast('SSID kosong', true); return; }
  toast(`Menghubungkan ke ${ssid}...`);
  try{
    const r = await fetch('/connect', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:`ssid=${encodeURIComponent(ssid)}&pass=${encodeURIComponent(pass)}`});
    const d = await r.json();
    if(d.connected){ toast(`Terhubung! IP: ${d.ip}`); logEvent(`STA connected to ${ssid} (${d.ip})`); }
    else { toast('Gagal terhubung', true); logEvent(`Gagal connect ke ${ssid}`); }
    loadStatus();
  }catch(e){ toast('Request gagal', true); }
}

async function applyAP(){
  const ssid = document.getElementById('apSsid').value;
  const pass = document.getElementById('apPass').value;
  const ch = document.getElementById('apChannel').value;
  const hidden = document.getElementById('apHidden').checked ? '1':'0';
  try{
    const r = await fetch('/apconfig', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:`ssid=${encodeURIComponent(ssid)}&pass=${encodeURIComponent(pass)}&channel=${ch}&hidden=${hidden}`});
    const d = await r.json();
    if(d.ok){ toast(`AP updated: ${ssid}`); logEvent(`AP config diubah -> ${ssid} (ch ${ch})`); }
    else toast('Gagal update AP', true);
    loadStatus();
  }catch(e){ toast('Request gagal', true); }
}

async function scanApClients(){
  document.getElementById('radarAp').classList.add('on');
  const tbody = document.getElementById('apClientsBody');
  try{
    const r = await fetch('/apclients'); const list = await r.json();
    tbody.innerHTML = list.length ? '' : '<tr><td colspan="3" class="empty">Tidak ada perangkat terhubung.</td></tr>';
    list.forEach(c=>{
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${c.mac}</td><td>${c.ip}</td><td>${c.vendor}</td>`;
      tbody.appendChild(tr);
    });
    logEvent(`AP clients refresh: ${list.length} perangkat`);
  }catch(e){ toast('Gagal ambil daftar klien', true); }
  document.getElementById('radarAp').classList.remove('on');
}

let lastNetResults = [];
async function scanNet(){
  document.getElementById('radarNet').classList.add('on');
  document.getElementById('netProgress').textContent = 'Scanning subnet, mohon tunggu...';
  const tbody = document.getElementById('netBody');
  tbody.innerHTML = '';
  try{
    const r = await fetch('/netscan'); const list = await r.json();
    lastNetResults = list;
    document.getElementById('netProgress').textContent = `Selesai. ${list.length} host aktif ditemukan.`;
    tbody.innerHTML = list.length ? '' : '<tr><td colspan="5" class="empty">Tidak ada host aktif.</td></tr>';
    list.forEach(h=>{
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${h.ip}</td><td>${h.mac||'-'}</td><td>${h.vendor||'-'}</td><td>${h.latency}ms</td><td>${h.ports.join(', ')||'-'}</td>`;
      tbody.appendChild(tr);
    });
    logEvent(`Network scan selesai: ${list.length} host aktif`);
  }catch(e){ toast('Network scan gagal', true); }
  document.getElementById('radarNet').classList.remove('on');
}

function exportCsv(){
  if(!lastNetResults.length){ toast('Belum ada data untuk di-export', true); return; }
  let csv = 'IP,MAC,Vendor,Latency(ms),OpenPorts\n';
  lastNetResults.forEach(h=>{
    csv += `${h.ip},${h.mac||''},${h.vendor||''},${h.latency},"${h.ports.join(' ')}"\n`;
  });
  const blob = new Blob([csv], {type:'text/csv'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = 'netscan.csv'; a.click();
  URL.revokeObjectURL(url);
}
</script>
</body>
</html>
)rawliteral";

// ---------------- WiFi helpers ----------------
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

void handleApClients() {
  wifi_sta_list_t wifiStaList;
  esp_netif_sta_list_t netifStaList;
  esp_wifi_ap_get_sta_list(&wifiStaList);
  esp_netif_get_sta_list(&wifiStaList, &netifStaList);

  String json = "[";
  for (int i = 0; i < netifStaList.num; i++) {
    if (i) json += ",";
    esp_netif_sta_info_t sta = netifStaList.sta[i];
    String mac = macToStr(sta.mac);
    IPAddress ip(sta.ip.addr);
    json += "{\"mac\":\"" + mac + "\",\"ip\":\"" + ip.toString() + "\",\"vendor\":\"" + ouiLookup(mac) + "\"}";
  }
  json += "]";
  server.send(200, "application/json", json);
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
      float latency = Ping.averageTime();

      uint8_t mac[6];
      bool hasMac = getMacForIp(target, mac);
      String macStr = hasMac ? macToStr(mac) : "";
      String vendor = hasMac ? ouiLookup(macStr) : "Unknown";

      if (!first) json += ",";
      first = false;
      json += "{\"ip\":\"" + target.toString() + "\",\"latency\":" + String(latency, 1) +
              ",\"mac\":\"" + macStr + "\",\"vendor\":\"" + vendor + "\",\"ports\":[";
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
  json += "\"apClients\":" + String(WiFi.softAPgetStationNum()) + ",";
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
  server.on("/apclients", handleApClients);
  server.on("/netscan", handleNetScan);
  server.on("/status", handleStatus);
  server.begin();
}

void loop() {
  server.handleClient();
}
