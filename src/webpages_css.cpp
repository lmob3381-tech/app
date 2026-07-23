#include "webpages.h"

const char STYLE_CSS[] PROGMEM = R"CSSDOC(
:root{
  --bg:#0e1116;
  --card:#161b22;
  --card2:#1c2330;
  --border:#2a3140;
  --text:#e6edf3;
  --text-dim:#8b949e;
  --accent:#3ea6ff;
  --accent2:#58e6a0;
  --danger:#ff6b6b;
  --warn:#ffb454;
}
*{box-sizing:border-box;}
body{
  margin:0;
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
  background:var(--bg);
  color:var(--text);
  padding-bottom:40px;
}
.topbar{
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:16px;
  background:linear-gradient(135deg,#131722,#1a2233);
  border-bottom:1px solid var(--border);
  position:sticky;
  top:0;
  z-index:10;
}
.brand{display:flex;align-items:center;gap:10px;}
.logo{font-size:28px;}
.brand h1{font-size:16px;margin:0;}
.subtitle{font-size:11px;color:var(--text-dim);}
.stat-pill{
  font-size:11px;
  background:var(--card2);
  border:1px solid var(--border);
  padding:6px 10px;
  border-radius:20px;
  color:var(--accent2);
  white-space:nowrap;
}
.tabs{
  display:flex;
  overflow-x:auto;
  gap:4px;
  padding:10px 12px;
  background:#10141b;
  border-bottom:1px solid var(--border);
}
.tab-btn{
  flex:0 0 auto;
  background:transparent;
  border:1px solid var(--border);
  color:var(--text-dim);
  padding:8px 14px;
  border-radius:20px;
  font-size:13px;
  cursor:pointer;
  white-space:nowrap;
}
.tab-btn.active{
  background:var(--accent);
  color:#04101c;
  border-color:var(--accent);
  font-weight:600;
}
.tab-panel{display:none;padding:14px;max-width:900px;margin:0 auto;}
.tab-panel.active{display:block;}
.card{
  background:var(--card);
  border:1px solid var(--border);
  border-radius:14px;
  padding:16px;
  margin-bottom:14px;
}
.card-head{
  display:flex;
  align-items:center;
  justify-content:space-between;
  flex-wrap:wrap;
  gap:8px;
  margin-bottom:8px;
}
.card h2{font-size:15px;margin:0 0 8px 0;}
.hint{font-size:12px;color:var(--text-dim);line-height:1.5;}
.hint.warn{color:var(--warn);background:#2a2210;border:1px solid #4a3a10;padding:10px;border-radius:8px;}
.btn-row{display:flex;gap:8px;flex-wrap:wrap;}
.btn{
  border:none;
  padding:10px 16px;
  border-radius:10px;
  font-size:13px;
  font-weight:600;
  cursor:pointer;
  text-decoration:none;
  display:inline-block;
  text-align:center;
}
.btn.primary{background:var(--accent);color:#04101c;}
.btn.ghost{background:var(--card2);color:var(--text);border:1px solid var(--border);}
.btn.danger{background:var(--danger);color:#2b0505;}
.table-wrap{overflow-x:auto;margin-top:10px;}
table{width:100%;border-collapse:collapse;font-size:12px;}
th,td{padding:8px 10px;text-align:left;border-bottom:1px solid var(--border);white-space:nowrap;}
th{color:var(--text-dim);font-weight:600;font-size:11px;text-transform:uppercase;}
.signal-bar{
  display:inline-block;
  width:60px;
  height:8px;
  background:var(--card2);
  border-radius:4px;
  overflow:hidden;
  vertical-align:middle;
}
.signal-fill{height:100%;background:linear-gradient(90deg,var(--danger),var(--warn),var(--accent2));}
.bars{display:flex;align-items:flex-end;gap:6px;height:120px;margin-top:10px;}
.bar-col{flex:1;display:flex;flex-direction:column;align-items:center;gap:4px;}
.bar-fill{width:100%;background:var(--accent);border-radius:4px 4px 0 0;min-height:2px;}
.bar-label{font-size:10px;color:var(--text-dim);}
.badge{
  background:var(--card2);
  border:1px solid var(--border);
  padding:4px 10px;
  border-radius:20px;
  font-size:11px;
  color:var(--accent2);
}
textarea{
  width:100%;
  background:var(--card2);
  border:1px solid var(--border);
  color:var(--text);
  border-radius:10px;
  padding:10px;
  font-family:monospace;
  font-size:13px;
  resize:vertical;
  margin-bottom:10px;
}
.status-box{
  margin-top:10px;
  background:var(--card2);
  border:1px solid var(--border);
  border-radius:10px;
  padding:12px;
  font-size:12px;
  color:var(--text-dim);
  white-space:pre-wrap;
  font-family:monospace;
}
.grid-stats{
  display:grid;
  grid-template-columns:repeat(auto-fit,minmax(120px,1fr));
  gap:10px;
  margin-top:12px;
}
.stat-box{
  background:var(--card2);
  border:1px solid var(--border);
  border-radius:10px;
  padding:12px;
  text-align:center;
}
.stat-box.alert-box{border-color:#4a2020;}
.stat-label{display:block;font-size:10px;color:var(--text-dim);text-transform:uppercase;margin-bottom:6px;}
.stat-value{display:block;font-size:22px;font-weight:700;color:var(--accent2);}
.alert-box .stat-value{color:var(--danger);}
.form-row{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px;}
.form-row input{
  flex:1;
  min-width:140px;
  background:var(--card2);
  border:1px solid var(--border);
  color:var(--text);
  padding:10px;
  border-radius:10px;
  font-size:13px;
}
.kv-table{width:100%;font-size:12px;border-collapse:collapse;}
.kv-table tr td{padding:6px 4px;border-bottom:1px solid var(--border);}
.kv-table tr td:first-child{color:var(--text-dim);width:40%;}
.notes{font-size:12px;color:var(--text-dim);line-height:1.7;padding-left:18px;}
.toast{
  position:fixed;
  bottom:16px;
  left:50%;
  transform:translateX(-50%) translateY(100px);
  background:var(--card2);
  border:1px solid var(--border);
  padding:10px 18px;
  border-radius:20px;
  font-size:13px;
  transition:transform .25s ease;
  z-index:100;
}
.toast.show{transform:translateX(-50%) translateY(0);}
)CSSDOC";
