package com.routermanager.xpw300

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONArray

/**
 * Router: HSAirPo XPW300 (GPON/XPON ONU)
 * Alamat default: http://192.168.1.1/start.ghtml
 * Login default : admin / admin
 *
 * Aplikasi ini membungkus antarmuka web router bawaan di dalam WebView,
 * lalu menambahkan tombol aksi cepat yang menyuntikkan JavaScript ke
 * halaman untuk: login otomatis, membaca daftar perangkat terhubung,
 * menyalakan/mematikan SSID 1-4, dan membantu mengatur prioritas
 * (QoS) untuk perangkat tertentu.
 *
 * CATATAN PENTING:
 * Otomasi ini bekerja dengan "menebak" elemen HTML berdasarkan teks
 * label yang terlihat di layar (bukan berdasarkan id/name asli, karena
 * kita tidak punya akses ke source code halaman aslinya). Untuk aksi
 * yang aman & bisa dibatalkan (login, baca data, nyala/mati SSID),
 * otomasi dibuat penuh otomatis. Untuk aksi yang berisiko membuat
 * konfigurasi rusak (menambah rule QoS baru), aplikasi hanya akan
 * MENGISIKAN form-nya lalu meminta kamu menekan tombol "Add"/"Submit"
 * terakhir secara manual di WebView yang tetap terlihat di layar.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val handler = Handler(Looper.getMainLooper())

    private val routerBase = "http://192.168.1.1"
    private val startUrl = "http://192.168.1.1/start.ghtml"
    private val defaultUser = "admin"
    private val defaultPass = "admin"

    private var loginAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        setupWebView()
        setupButtons()

        webView.loadUrl(routerBase)
        swipeRefresh.setOnRefreshListener {
            loginAttempted = false
            webView.loadUrl(routerBase)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                tvStatus.text = "Terhubung"
                // Coba login otomatis setiap kali halaman selesai dimuat.
                // Skrip ini aman dipanggil berulang: kalau bukan halaman
                // login, skrip tidak melakukan apa-apa.
                tryAutoLogin()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                tvStatus.text = "Tidak terhubung"
                Toast.makeText(
                    this@MainActivity,
                    "Gagal terhubung ke $routerBase. Pastikan HP tersambung ke WiFi router ini.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnHome).setOnClickListener {
            webView.loadUrl(startUrl)
        }
        findViewById<Button>(R.id.btnRelogin).setOnClickListener {
            loginAttempted = false
            webView.loadUrl(routerBase)
            Toast.makeText(this, "Mencoba login ulang...", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnUsers).setOnClickListener { openUserList() }
        findViewById<Button>(R.id.btnSsid1).setOnClickListener { toggleSsid("SSID1") }
        findViewById<Button>(R.id.btnSsid2).setOnClickListener { toggleSsid("SSID2") }
        findViewById<Button>(R.id.btnSsid3).setOnClickListener { toggleSsid("SSID3") }
        findViewById<Button>(R.id.btnSsid4).setOnClickListener { toggleSsid("SSID4") }
        findViewById<Button>(R.id.btnPriority).setOnClickListener { openPriorityFlow() }
        findViewById<Button>(R.id.btnWifiSettings).setOnClickListener {
            navigateMenu(listOf("Network", "WLAN Radio2.4G", "Basic")) {}
        }
    }

    // ---------------------------------------------------------------
    // AUTO LOGIN
    // ---------------------------------------------------------------
    private fun tryAutoLogin() {
        val js = """
            (function(){
              try{
                var pass = document.querySelector('input[type=password]');
                if(!pass) return 'NOLOGIN';
                var form = pass.form || document;
                var texts = form.querySelectorAll('input[type=text]');
                if(texts.length>0){ texts[0].value='$defaultUser'; }
                pass.value='$defaultPass';
                var btn = form.querySelector('input[type=button], input[type=submit], button');
                if(!btn){
                  var all = document.querySelectorAll('input,button');
                  for(var i=0;i<all.length;i++){
                    var v=((all[i].value||'')+(all[i].textContent||'')).toLowerCase();
                    if(v.indexOf('login')>=0){ btn = all[i]; break; }
                  }
                }
                if(btn){ btn.click(); return 'CLICKED'; }
                if(form.submit){ form.submit(); return 'SUBMITTED'; }
                return 'NOBUTTON';
              }catch(e){ return 'ERR:'+e.message; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val clean = result?.trim('"') ?: ""
            if (clean == "CLICKED" || clean == "SUBMITTED") {
                loginAttempted = true
                tvStatus.text = "Login otomatis..."
            }
        }
    }

    // ---------------------------------------------------------------
    // NAVIGASI MENU (klik berjenjang berdasarkan teks label menu)
    // ---------------------------------------------------------------
    private fun clickByLabel(label: String, callback: (Boolean) -> Unit) {
        val escaped = label.replace("'", "\\'")
        val js = """
            (function(){
              var label = '$escaped';
              var nodes = document.querySelectorAll('a,li,span,div,td,option');
              for(var i=0;i<nodes.length;i++){
                if(nodes[i].children.length>0) continue;
                var t = (nodes[i].textContent||'').trim();
                if(t===label || t==='+'+label || t==='-'+label){
                  nodes[i].click();
                  return 'true';
                }
              }
              return 'false';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            callback(result?.trim('"') == "true")
        }
    }

    /**
     * Klik menu secara berurutan, misalnya:
     * navigateMenu(listOf("Network","QoS","Rule Type")) { ... }
     * akan klik "Network", tunggu, klik "QoS", tunggu, klik "Rule Type".
     */
    private fun navigateMenu(labels: List<String>, attemptsLeft: Int = 15, onFinished: () -> Unit) {
        if (labels.isEmpty()) {
            onFinished()
            return
        }
        val current = labels[0]
        val remaining = labels.drop(1)

        fun attempt(triesLeft: Int) {
            clickByLabel(current) { success ->
                if (success) {
                    handler.postDelayed({ navigateMenu(remaining, 15, onFinished) }, 450)
                } else if (triesLeft > 0) {
                    handler.postDelayed({ attempt(triesLeft - 1) }, 300)
                } else {
                    Toast.makeText(
                        this,
                        "Menu \"$current\" tidak ditemukan di halaman. Silakan buka manual di bawah.",
                        Toast.LENGTH_LONG
                    ).show()
                    onFinished()
                }
            }
        }
        attempt(attemptsLeft)
    }

    // ---------------------------------------------------------------
    // CEK USER (baca tabel DHCP Server -> Allocated Address)
    // ---------------------------------------------------------------
    private fun openUserList() {
        Toast.makeText(this, "Membuka daftar perangkat...", Toast.LENGTH_SHORT).show()
        navigateMenu(listOf("Network", "LAN Address Setting", "DHCP Server")) {
            handler.postDelayed({ scrapeUserTable() }, 500)
        }
    }

    private fun scrapeUserTable() {
        val js = """
            (function(){
              try{
                var tables = document.querySelectorAll('table');
                var best = null, bestRows = 0;
                for(var t=0;t<tables.length;t++){
                  var rows = tables[t].querySelectorAll('tr');
                  if(rows.length > bestRows){ bestRows = rows.length; best = tables[t]; }
                }
                if(!best) return '[]';
                var rows = best.querySelectorAll('tr');
                var data = [];
                for(var i=0;i<rows.length;i++){
                  var cells = rows[i].querySelectorAll('td');
                  if(cells.length>=4){
                    data.push({
                      mac: (cells[0].textContent||'').trim(),
                      ip: (cells[1].textContent||'').trim(),
                      sisa: (cells[2].textContent||'').trim(),
                      nama: (cells[3].textContent||'').trim(),
                      port: cells.length>=5 ? (cells[4].textContent||'').trim() : ''
                    });
                  }
                }
                return JSON.stringify(data);
              }catch(e){ return '[]'; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawResult ->
            val jsonStr = unescapeJs(rawResult)
            val list = mutableListOf<DeviceInfo>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val mac = o.optString("mac")
                    if (mac.isBlank() || mac.equals("MAC Address", true)) continue
                    list.add(
                        DeviceInfo(
                            mac = mac,
                            ip = o.optString("ip"),
                            remaining = o.optString("sisa"),
                            hostName = o.optString("nama"),
                            port = o.optString("port")
                        )
                    )
                }
            } catch (e: Exception) {
                // biarkan list kosong, akan ditampilkan pesan di dialog
            }
            showUserListDialog(list)
        }
    }

    private fun showUserListDialog(devices: List<DeviceInfo>) {
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Perangkat Terhubung")
                .setMessage(
                    "Tidak ada data yang bisa dibaca otomatis. Silakan lihat langsung " +
                        "di layar WebView pada menu Network > LAN Address Setting > DHCP Server."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val items = devices.map {
            "${it.hostName.ifBlank { "(tanpa nama)" }}\n${it.ip}  •  ${it.mac}  •  sisa ${it.remaining}s"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Perangkat Terhubung (${devices.size})")
            .setItems(items) { _, _ -> }
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ---------------------------------------------------------------
    // NYALAKAN / MATIKAN SSID (1-4)
    // ---------------------------------------------------------------
    private fun toggleSsid(ssidToken: String) {
        Toast.makeText(this, "Membuka pengaturan $ssidToken...", Toast.LENGTH_SHORT).show()
        navigateMenu(listOf("Network", "WLAN Radio2.4G", "SSID Settings")) {
            handler.postDelayed({ selectSsidDropdown(ssidToken) }, 500)
        }
    }

    private fun selectSsidDropdown(ssidToken: String) {
        val js = """
            (function(){
              try{
                var selects = document.querySelectorAll('select');
                for(var i=0;i<selects.length;i++){
                  var opts = selects[i].options;
                  for(var j=0;j<opts.length;j++){
                    if(opts[j].value==='$ssidToken' || opts[j].text==='$ssidToken'){
                      selects[i].selectedIndex = j;
                      var ev = new Event('change', {bubbles:true});
                      selects[i].dispatchEvent(ev);
                      return 'true';
                    }
                  }
                }
                return 'false';
              }catch(e){ return 'false'; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val ok = result?.trim('"') == "true"
            if (!ok) {
                Toast.makeText(
                    this,
                    "$ssidToken tidak tersedia di router ini (router mungkin hanya punya SSID1 & SSID2).",
                    Toast.LENGTH_LONG
                ).show()
                return@evaluateJavascript
            }
            handler.postDelayed({ flipEnableCheckboxAndConfirm(ssidToken) }, 500)
        }
    }

    private fun flipEnableCheckboxAndConfirm(ssidToken: String) {
        val js = """
            (function(){
              try{
                var target = null;
                var all = document.querySelectorAll('*');
                for(var i=0;i<all.length;i++){
                  if(all[i].children.length===0 && (all[i].textContent||'').trim()==='SSIDEnable'){
                    var row = all[i].closest('tr') || all[i].parentElement;
                    var cb = row ? row.querySelector('input[type=checkbox]') : null;
                    if(cb){ target = cb; break; }
                  }
                }
                if(!target){
                  var boxes = document.querySelectorAll('input[type=checkbox]');
                  if(boxes.length>=2) target = boxes[1];
                  else if(boxes.length===1) target = boxes[0];
                }
                if(!target) return 'NOTFOUND';
                return target.checked ? 'ON' : 'OFF';
              }catch(e){ return 'ERR'; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val state = result?.trim('"') ?: "ERR"
            if (state == "NOTFOUND" || state == "ERR") {
                Toast.makeText(
                    this,
                    "Tidak menemukan kotak centang SSIDEnable. Silakan atur manual di layar.",
                    Toast.LENGTH_LONG
                ).show()
                return@evaluateJavascript
            }
            val turningOn = state == "OFF"
            AlertDialog.Builder(this)
                .setTitle(ssidToken)
                .setMessage(
                    if (turningOn) "$ssidToken saat ini MATI. Nyalakan sekarang?"
                    else "$ssidToken saat ini NYALA. Matikan sekarang?"
                )
                .setPositiveButton(if (turningOn) "Nyalakan" else "Matikan") { _, _ ->
                    clickCheckboxAndSubmit(ssidToken)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun clickCheckboxAndSubmit(ssidToken: String) {
        val clickJs = """
            (function(){
              try{
                var target = null;
                var all = document.querySelectorAll('*');
                for(var i=0;i<all.length;i++){
                  if(all[i].children.length===0 && (all[i].textContent||'').trim()==='SSIDEnable'){
                    var row = all[i].closest('tr') || all[i].parentElement;
                    var cb = row ? row.querySelector('input[type=checkbox]') : null;
                    if(cb){ target = cb; break; }
                  }
                }
                if(!target){
                  var boxes = document.querySelectorAll('input[type=checkbox]');
                  if(boxes.length>=2) target = boxes[1];
                  else if(boxes.length===1) target = boxes[0];
                }
                if(!target) return 'false';
                target.click();
                return 'true';
              }catch(e){ return 'false'; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(clickJs) { clicked ->
            if (clicked?.trim('"') != "true") {
                Toast.makeText(this, "Gagal mengubah $ssidToken.", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            handler.postDelayed({
                clickSubmitButton { submitted ->
                    val msg = if (submitted) "$ssidToken berhasil disimpan."
                              else "Pengaturan diubah, tapi tombol Submit tidak ditemukan otomatis. Tekan Submit di layar."
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }, 300)
        }
    }

    private fun clickSubmitButton(callback: (Boolean) -> Unit) {
        val js = """
            (function(){
              var btns = document.querySelectorAll('input[type=button],input[type=submit],button');
              for(var i=0;i<btns.length;i++){
                var v=((btns[i].value||'')+(btns[i].textContent||'')).trim();
                if(v==='Submit'){ btns[i].click(); return 'true'; }
              }
              return 'false';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            callback(result?.trim('"') == "true")
        }
    }

    // ---------------------------------------------------------------
    // PRIORITAS USER (QoS) - alur semi-otomatis
    // ---------------------------------------------------------------
    private fun openPriorityFlow() {
        AlertDialog.Builder(this)
            .setTitle("Atur Prioritas User (QoS)")
            .setMessage(
                "Langkah:\n" +
                    "1. Aplikasi akan membuka halaman QoS > Rule Type dan mengisi MAC perangkat.\n" +
                    "2. Kamu tekan tombol \"Add\" di layar untuk membuat aturan tersebut (untuk keamanan, langkah terakhir ini sengaja tidak diotomatiskan penuh).\n" +
                    "3. Aplikasi lalu membuka QoS > Rule Setting dan mengisi level prioritas pilihanmu, tinggal tekan \"Add\" sekali lagi.\n\n" +
                    "Masukkan alamat MAC perangkat yang mau diatur prioritasnya (lihat di menu Cek User)."
            )
            .setPositiveButton("Lanjut") { _, _ -> askMacThenPriority() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun askMacThenPriority() {
        val input = android.widget.EditText(this)
        input.hint = "contoh: AA:BB:CC:DD:EE:FF"
        AlertDialog.Builder(this)
            .setTitle("MAC Address Perangkat")
            .setView(input)
            .setPositiveButton("Berikutnya") { _, _ ->
                val mac = input.text.toString().trim()
                if (mac.isBlank()) {
                    Toast.makeText(this, "MAC address tidak boleh kosong.", Toast.LENGTH_SHORT).show()
                } else {
                    askPriorityLevel(mac)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun askPriorityLevel(mac: String) {
        val levels = arrayOf(
            "1 - Tertinggi (Prioritas utama)",
            "2", "3", "4", "5", "6", "7",
            "8 - Terendah (Default)"
        )
        AlertDialog.Builder(this)
            .setTitle("Pilih Tingkat Prioritas")
            .setItems(levels) { _, which ->
                val queueLevel = which + 1
                startRuleTypeFlow(mac, queueLevel)
            }
            .show()
    }

    private fun startRuleTypeFlow(mac: String, queueLevel: Int) {
        Toast.makeText(this, "Membuka QoS > Rule Type...", Toast.LENGTH_SHORT).show()
        navigateMenu(listOf("Network", "QoS", "Rule Type")) {
            handler.postDelayed({ fillRuleTypeForm(mac, queueLevel) }, 500)
        }
    }

    private fun fillRuleTypeForm(mac: String, queueLevel: Int) {
        val escapedMac = mac.replace("'", "")
        val js = """
            (function(){
              try{
                // Set dropdown Type ke 'Source MAC'
                var typeSelect = null;
                var selects = document.querySelectorAll('select');
                for(var i=0;i<selects.length;i++){
                  var opts = selects[i].options;
                  for(var j=0;j<opts.length;j++){
                    if((opts[j].text||'').toLowerCase().indexOf('source mac')>=0){
                      typeSelect = selects[i];
                      selects[i].selectedIndex = j;
                      selects[i].dispatchEvent(new Event('change', {bubbles:true}));
                      break;
                    }
                  }
                  if(typeSelect) break;
                }
                // Isi kolom Minimum & Maximum dengan MAC yang sama
                var textInputs = document.querySelectorAll('input[type=text]');
                if(textInputs.length>=2){
                  textInputs[textInputs.length-2].value = '$escapedMac';
                  textInputs[textInputs.length-1].value = '$escapedMac';
                }
                return 'true';
              }catch(e){ return 'false'; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val ok = result?.trim('"') == "true"
            val msg = if (ok)
                "Form sudah diisi dengan MAC $mac. Silakan periksa dan tekan tombol \"Add\" di layar."
            else
                "Gagal mengisi form otomatis. Silakan isi manual: Type = Source MAC, Minimum & Maximum = $mac."
            AlertDialog.Builder(this)
                .setTitle("Langkah 1: Buat Rule Type")
                .setMessage(msg)
                .setPositiveButton("Sudah saya tekan Add, Lanjut") { _, _ ->
                    startRuleSettingFlow(queueLevel)
                }
                .setNegativeButton("Nanti saja", null)
                .show()
        }
    }

    private fun startRuleSettingFlow(queueLevel: Int) {
        Toast.makeText(this, "Membuka QoS > Rule Setting...", Toast.LENGTH_SHORT).show()
        navigateMenu(listOf("Network", "QoS", "Rule Setting")) {
            handler.postDelayed({ fillRuleSettingForm(queueLevel) }, 500)
        }
    }

    private fun fillRuleSettingForm(queueLevel: Int) {
        val js = """
            (function(){
              try{
                var selects = document.querySelectorAll('select');
                var setAny = false;
                for(var i=0;i<selects.length;i++){
                  var opts = selects[i].options;
                  for(var j=0;j<opts.length;j++){
                    if((opts[j].value==='$queueLevel' || opts[j].text.trim()==='$queueLevel')){
                      selects[i].selectedIndex = j;
                      selects[i].dispatchEvent(new Event('change', {bubbles:true}));
                      setAny = true;
                    }
                  }
                }
                return setAny ? 'true' : 'false';
              }catch(e){ return 'false'; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            AlertDialog.Builder(this)
                .setTitle("Langkah 2: Hubungkan ke Prioritas")
                .setMessage(
                    "Pilih \"Traffic Rule\" yang barusan kamu buat pada dropdown di layar, " +
                        "pastikan \"Queue Rule\" bernilai $queueLevel, lalu tekan tombol \"Add\" " +
                        "untuk menyimpan. Selesai!"
                )
                .setPositiveButton("Mengerti", null)
                .show()
        }
    }

    // ---------------------------------------------------------------
    // UTIL
    // ---------------------------------------------------------------
    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        var s = raw
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
