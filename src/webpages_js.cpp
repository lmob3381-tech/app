#include "webpages.h"

const char APP_JS[] PROGMEM = R"JSDOC(
(function(){
  'use strict';

  // ---------- TAB NAVIGATION ----------
  const tabBtns = document.querySelectorAll('.tab-btn');
  const panels = document.querySelectorAll('.tab-panel');
  tabBtns.forEach(btn=>{
    btn.addEventListener('click', ()=>{
      tabBtns.forEach(b=>b.classList.remove('active'));
      panels.forEach(p=>p.classList.remove('active'));
      btn.classList.add('active');
      document.getElementById('tab-'+btn.dataset.tab).classList.add('active');
    });
  });

  function toast(msg){
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(()=>t.classList.remove('show'), 2200);
  }

  // ---------- WEBSOCKET LIVE STATE ----------
  let ws;
  function connectWS(){
    const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    ws = new WebSocket(proto + location.host + '/ws');
    ws.onopen = ()=>{ ws.send('ping_state'); };
    ws.onmessage = (evt)=>{
      try{
        const data = JSON.parse(evt.data);
        if(data.type === 'state') updateLiveState(data);
      }catch(e){}
    };
    ws.onclose = ()=> setTimeout(connectWS, 2000);
  }
  connectWS();

  function updateLiveState(d){
    document.getElementById('heapPill').textContent = 'Heap: ' + Math.round(d.heap/1024) + ' KB | Uptime: ' + d.uptime + 's';

    // clients tab
    document.getElementById('clientCountBadge').textContent = d.clientCount + ' client';
    const cTbody = document.querySelector('#clientTable tbody');
    cTbody.innerHTML = '';
    (d.clients || []).forEach(c=>{
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${c.mac}</td><td>${c.vendor}</td><td>${c.ip}</td><td>${c.rssi} dBm</td><td>${c.since}s</td>`;
      cTbody.appendChild(tr);
    });

    // beacon status
    const bStatus = document.getElementById('beaconStatus');
    bStatus.textContent = d.beaconActive
      ? `Status: AKTIF - broadcasting ${d.fakeSSIDCount} SSID palsu secara bergantian`
      : 'Status: berhenti';

    // sniffer stats
    document.getElementById('statTotalPacket').textContent = d.totalPackets;
    document.getElementById('statDeauth').textContent = d.deauthCount;
    document.getElementById('statChannel').textContent = d.snifferChannel;
    const warnEl = document.getElementById('deauthWarning');
    warnEl.style.display = d.deauthCount > 20 ? 'block' : 'none';

    // channel density bars (dari data scan terakhir jika ada)
    if(d.channelDensity){
      renderChannelBars(d.channelDensity);
    }
  }

  function renderChannelBars(densities){
    const wrap = document.getElementById('channelBars');
    const max = Math.max(1, ...densities);
    wrap.innerHTML = '';
    densities.forEach((v, idx)=>{
      const ch = idx+1;
      const col = document.createElement('div');
      col.className = 'bar-col';
      const h = Math.round((v/max)*90)+2;
      col.innerHTML = `<div class="bar-fill" style="height:${h}px"></div><span class="bar-label">CH${ch}</span>`;
      wrap.appendChild(col);
    });
  }

  // ---------- SCANNER ----------
  function rssiToPercent(rssi){
    let p = (rssi + 100) * 2; // -100dBm=0%, -50dBm=100%
    return Math.max(0, Math.min(100, p));
  }

  async function doScan(){
    const hint = document.getElementById('scanHint');
    hint.textContent = 'Sedang scanning... (2-4 detik)';
    try{
      const res = await fetch('/api/scan');
      const data = await res.json();
      const tbody = document.querySelector('#scanTable tbody');
      tbody.innerHTML = '';
      data.networks.sort((a,b)=>b.rssi-a.rssi).forEach(n=>{
        const pct = rssiToPercent(n.rssi);
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${n.ssid}</td><td>${n.bssid}</td><td>${n.rssi} dBm</td>
          <td>${n.channel}</td><td>${n.auth}</td><td>${n.vendor}</td>
          <td><span class="signal-bar"><span class="signal-fill" style="width:${pct}%"></span></span></td>`;
        tbody.appendChild(tr);
      });
      hint.textContent = data.count + ' jaringan ditemukan.';
      toast('Scan selesai: ' + data.count + ' SSID');
    }catch(e){
      hint.textContent = 'Gagal scan: ' + e;
    }
  }
  document.getElementById('btnScan').addEventListener('click', doScan);

  // ---------- BEACON / MULTI SSID ----------
  document.getElementById('btnBeaconStart').addEventListener('click', async ()=>{
    const raw = document.getElementById('ssidListInput').value;
    const list = raw.split('\n').map(s=>s.trim()).filter(s=>s.length>0).slice(0,15);
    if(list.length === 0){ toast('Isi minimal 1 SSID'); return; }
    await fetch('/api/beacon/start', {
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ssids:list})
    });
    toast('Broadcast ' + list.length + ' SSID dimulai');
  });
  document.getElementById('btnBeaconStop').addEventListener('click', async ()=>{
    await fetch('/api/beacon/stop', {method:'POST'});
    toast('Broadcast dihentikan');
  });

  // ---------- SNIFFER ----------
  document.getElementById('btnSnifferStart').addEventListener('click', async ()=>{
    await fetch('/api/sniffer/start', {method:'POST'});
    toast('Monitor dimulai');
  });
  document.getElementById('btnSnifferStop').addEventListener('click', async ()=>{
    await fetch('/api/sniffer/stop', {method:'POST'});
    toast('Monitor dihentikan');
  });

  // ---------- PING ----------
  document.getElementById('btnPing').addEventListener('click', async ()=>{
    const host = document.getElementById('pingHost').value || '8.8.8.8';
    const count = parseInt(document.getElementById('pingCount').value) || 4;
    const resBox = document.getElementById('pingResult');
    resBox.textContent = 'Melakukan ping ke ' + host + '...';
    await fetch('/api/ping', {
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body: JSON.stringify({host, count})
    });
    pollPingResult();
  });

  function pollPingResult(){
    let attempts = 0;
    const iv = setInterval(async ()=>{
      attempts++;
      const res = await fetch('/api/ping/result');
      const d = await res.json();
      if(!d.running && d.hasResult){
        clearInterval(iv);
        document.getElementById('pingResult').textContent =
          `Target   : ${d.target}\n` +
          `Terkirim : ${d.sent}\n` +
          `Diterima : ${d.received}\n` +
          `Hilang   : ${d.lost}\n` +
          `Min/Avg/Max : ${d.minMs.toFixed(1)} / ${d.avgMs.toFixed(1)} / ${d.maxMs.toFixed(1)} ms`;
      } else if(attempts > 40){
        clearInterval(iv);
        document.getElementById('pingResult').textContent = 'Timeout menunggu hasil ping.';
      }
    }, 500);
  }

  // ---------- DEVICE INFO ----------
  async function loadInfo(){
    try{
      const res = await fetch('/api/info');
      const d = await res.json();
      document.getElementById('subtitleInfo').textContent =
        'FW v' + d.fw_version + ' | ' + d.chip_model + ' | ' + d.ap_ip;
      const rows = [
        ['Firmware Version', d.fw_version],
        ['Chip Model', d.chip_model],
        ['Chip Revision', d.chip_rev],
        ['CPU Cores', d.cores],
        ['CPU Frequency', d.cpu_freq_mhz + ' MHz'],
        ['Flash Size', Math.round(d.flash_size/1024/1024) + ' MB'],
        ['Free Heap', Math.round(d.free_heap/1024) + ' KB'],
        ['SDK Version', d.sdk_version],
        ['AP MAC Address', d.mac_ap],
        ['AP IP Address', d.ap_ip],
        ['SSID Dashboard', d.ssid_main],
        ['Max Fake SSID', d.max_fake_ssid],
      ];
      const table = document.getElementById('infoTable');
      table.innerHTML = rows.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td></tr>`).join('');
    }catch(e){
      document.getElementById('subtitleInfo').textContent = 'gagal memuat info';
    }
  }
  loadInfo();

  // initial channel bars placeholder
  renderChannelBars(new Array(13).fill(0));
})();
)JSDOC";
