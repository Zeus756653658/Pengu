const { escapeHtml } = require("../services/adminAuth");

const PENGU_FAVICON_DATA_URI =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='16' fill='%23131929'/%3E%3Cellipse cx='32' cy='35' rx='17' ry='21' fill='%238B5CF6'/%3E%3Cellipse cx='32' cy='38' rx='11' ry='15' fill='%23F8FAFC'/%3E%3Ccircle cx='26' cy='24' r='2.5' fill='%230B0F19'/%3E%3Ccircle cx='38' cy='24' r='2.5' fill='%230B0F19'/%3E%3Cpolygon points='28,28 36,28 32,33' fill='%23F59E0B'/%3E%3C/svg%3E";

function renderConsoleLoginHtml(errorMsg = "", nextTarget = "/console") {
  const errHtml = errorMsg
    ? `<div class="err" role="alert">${escapeHtml(errorMsg)}</div>`
    : "";
  const safeNext = escapeHtml(nextTarget || "/console");
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0,viewport-fit=cover" />
<meta name="theme-color" content="#0B0F19" />
<meta name="mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<link rel="icon" href="${PENGU_FAVICON_DATA_URI}" />
<title>Pengu Developer WebApp Console — Sign In</title>
<style>
  :root { --bg:#0B0F19; --card:#131929; --soft:#1A2238; --border:#232C43; --text:#F8FAFC; --muted:#94A3B8; --violet:#8B5CF6; --cyan:#06B6D4; --coral:#F87171; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { background:radial-gradient(circle at top right, rgba(139,92,246,0.16), transparent 45%), var(--bg); color:var(--text); font-family:"Segoe UI",Roboto,system-ui,-apple-system,sans-serif; min-height:100dvh; display:grid; place-items:center; padding:20px; }
  .card { width:100%; max-width:430px; background:var(--card); border:1px solid var(--border); border-radius:22px; padding:26px; display:flex; flex-direction:column; gap:16px; box-shadow:0 20px 50px rgba(0,0,0,0.45); }
  .brand { display:flex; align-items:center; gap:12px; }
  .brand img { width:44px; height:44px; border-radius:12px; border:1px solid var(--border); }
  .eyebrow { font-size:11px; font-weight:800; letter-spacing:0.08em; color:var(--cyan); text-transform:uppercase; }
  h1 { font-size:21px; font-weight:800; line-height:1.25; }
  p { font-size:13.5px; line-height:1.5; color:var(--muted); }
  label { display:block; font-size:12px; font-weight:700; color:var(--muted); margin-bottom:7px; }
  .input-wrap { position:relative; display:flex; align-items:center; }
  input[type="password"], input[type="text"] { width:100%; height:48px; border-radius:13px; border:1px solid var(--border); background:#0B0F19; color:var(--text); padding:0 48px 0 14px; font-size:16px; outline:none; }
  input:focus { border-color:var(--violet); }
  .toggle-vis { position:absolute; right:8px; height:34px; padding:0 10px; border-radius:9px; border:1px solid var(--border); background:var(--soft); color:var(--muted); font-size:12px; font-weight:700; cursor:pointer; }
  .submit-btn { width:100%; height:50px; border-radius:14px; border:none; background:linear-gradient(135deg, var(--violet), #6D28D9); color:#fff; font-size:15px; font-weight:700; cursor:pointer; }
  .err { background:rgba(248,113,113,0.14); border:1px solid rgba(248,113,113,0.35); color:var(--coral); padding:11px 13px; border-radius:12px; font-size:13px; }
  .hint { font-size:12px; color:var(--muted); background:var(--soft); border:1px solid var(--border); border-radius:12px; padding:10px 12px; line-height:1.45; }
  code { font-family:Consolas,monospace; color:#C4B5FD; }
</style>
</head>
<body>
  <form class="card" method="POST" action="/api/admin/login">
    <input type="hidden" name="next" value="${safeNext}" />
    <div class="brand">
      <img src="${PENGU_FAVICON_DATA_URI}" alt="Pengu" />
      <div>
        <div class="eyebrow">Pengu WebApp Console</div>
        <h1>Dev Rayan Sign In</h1>
      </div>
    </div>
    <p>Unified desktop &amp; mobile control panel for Bug Reports, Closed Beta approvals, and APK releases.</p>
    ${errHtml}
    <div>
      <label for="token">Administrator Token</label>
      <div class="input-wrap">
        <input id="token" name="token" type="password" autocomplete="current-password" required placeholder="Enter PENGU_ADMIN_TOKEN" />
        <button class="toggle-vis" type="button" id="toggleVis">Show</button>
      </div>
    </div>
    <button class="submit-btn" type="submit">Open WebApp Console</button>
    <div class="hint">Tip: Token aapke Railway project ke <code>Variables &rarr; PENGU_ADMIN_TOKEN</code> me mil jayega. Mobile par Chrome menu se <strong>Add to Home screen</strong> bhi kar sakte hain.</div>
  </form>
<script>
document.getElementById('toggleVis').addEventListener('click', function () {
  var inp = document.getElementById('token');
  var show = inp.type === 'password';
  inp.type = show ? 'text' : 'password';
  this.textContent = show ? 'Hide' : 'Show';
});
</script>
</body>
</html>`;
}

function renderConsoleAppHtml({
  initialTab = "overview",
  validCategories = [],
  validStatuses = [],
  validChannels = ["stable", "beta", "closed", "archived"],
} = {}) {
  const safeInitialTab = ["overview", "bugs", "beta", "releases"].includes(initialTab)
    ? initialTab
    : "overview";

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0,viewport-fit=cover" />
<meta name="theme-color" content="#0B0F19" />
<meta name="mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-title" content="Pengu Console" />
<link rel="icon" href="${PENGU_FAVICON_DATA_URI}" />
<title>Pengu Developer WebApp Console</title>
<style>
  :root {
    --bg:#0B0F19; --bg-elev:#0F1525; --card:#131929; --soft:#1A2238; --border:#232C43;
    --text:#F8FAFC; --muted:#94A3B8; --violet:#8B5CF6; --violet-soft:rgba(139,92,246,0.16);
    --cyan:#06B6D4; --cyan-soft:rgba(6,182,212,0.15);
    --emerald:#10B981; --emerald-soft:rgba(16,185,129,0.15);
    --amber:#F59E0B; --amber-soft:rgba(245,158,11,0.16);
    --coral:#F87171; --coral-soft:rgba(248,113,113,0.16);
  }
  * { box-sizing:border-box; margin:0; padding:0; }
  html, body { background:var(--bg); color:var(--text); font-family:"Segoe UI",Roboto,system-ui,-apple-system,sans-serif; min-height:100dvh; overflow-x:hidden; }
  button, input, select, textarea { font-family:inherit; }

  .shell { display:grid; grid-template-columns:256px minmax(0, 1fr); min-height:100dvh; }
  .sidebar {
    background:var(--bg-elev); border-right:1px solid var(--border); padding:20px 16px;
    display:flex; flex-direction:column; gap:18px; position:sticky; top:0; height:100dvh;
  }
  .brand { display:flex; align-items:center; gap:11px; padding:4px 6px; }
  .brand img { width:40px; height:40px; border-radius:12px; border:1px solid var(--border); }
  .brand-title { font-size:16px; font-weight:800; letter-spacing:-0.01em; }
  .brand-sub { font-size:11px; color:var(--cyan); font-weight:700; text-transform:uppercase; letter-spacing:0.06em; }

  .nav-list { display:flex; flex-direction:column; gap:6px; flex:1; }
  .nav-btn {
    width:100%; display:flex; align-items:center; justify-content:space-between; gap:10px;
    min-height:44px; padding:10px 13px; border-radius:13px; border:1px solid transparent;
    background:transparent; color:var(--muted); font-size:14px; font-weight:700; cursor:pointer; text-align:left;
  }
  .nav-btn:hover { background:var(--soft); color:var(--text); }
  .nav-btn.active { background:var(--violet-soft); border-color:rgba(139,92,246,0.45); color:#E9D5FF; }
  .pill { font-size:11px; font-weight:800; padding:2px 8px; border-radius:999px; background:var(--soft); color:var(--text); border:1px solid var(--border); }
  .pill.warn { background:var(--amber-soft); color:var(--amber); border-color:rgba(245,158,11,0.35); }

  .side-footer { border-top:1px solid var(--border); padding-top:14px; display:flex; flex-direction:column; gap:8px; }
  .status-dot { display:inline-block; width:8px; height:8px; border-radius:50%; background:var(--emerald); margin-right:6px; box-shadow:0 0 8px var(--emerald); }

  .main { display:flex; flex-direction:column; min-width:0; padding:22px 24px 36px; max-width:1420px; width:100%; margin:0 auto; }
  .mobile-bar { display:none; }
  .bottom-nav { display:none; }

  .page-head { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:12px; margin-bottom:18px; }
  .page-head h2 { font-size:22px; font-weight:800; }
  .sub { font-size:13px; color:var(--muted); margin-top:2px; }

  .btn {
    min-height:42px; padding:0 15px; border-radius:12px; border:1px solid transparent;
    background:var(--violet); color:#fff; font-size:13.5px; font-weight:700; cursor:pointer;
    display:inline-flex; align-items:center; justify-content:center; gap:7px; text-decoration:none;
  }
  .btn:disabled { opacity:0.55; cursor:not-allowed; }
  .btn.sec { background:var(--soft); border-color:var(--border); color:var(--text); }
  .btn.cyan { background:var(--cyan-soft); border-color:rgba(6,182,212,0.4); color:#67E8F9; }
  .btn.ok { background:var(--emerald-soft); border-color:rgba(16,185,129,0.4); color:#6EE7B7; }
  .btn.danger { background:var(--coral-soft); border-color:rgba(248,113,113,0.4); color:var(--coral); }
  .btn.sm { min-height:36px; padding:0 11px; font-size:12.5px; border-radius:10px; }

  input[type="text"], input[type="number"], input[type="url"], input[type="search"], select, textarea {
    width:100%; background:#0B0F19; border:1px solid var(--border); border-radius:12px;
    color:var(--text); padding:10px 12px; font-size:14px; outline:none; min-height:42px;
  }
  input:focus, select:focus, textarea:focus { border-color:var(--violet); }
  textarea { resize:vertical; min-height:86px; }
  label.fld { display:block; font-size:11.5px; font-weight:700; color:var(--muted); text-transform:uppercase; letter-spacing:0.05em; margin:10px 0 6px; }

  .tab { display:none; flex-direction:column; gap:16px; }
  .tab.active { display:flex; }

  .kpi-grid { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:12px; }
  .card { background:var(--card); border:1px solid var(--border); border-radius:18px; padding:16px; display:flex; flex-direction:column; gap:10px; min-width:0; }
  .kpi-num { font-size:28px; font-weight:800; line-height:1.1; }
  .kpi-label { font-size:12px; font-weight:700; color:var(--muted); text-transform:uppercase; letter-spacing:0.04em; }

  .grid-2 { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:16px; align-items:start; }
  .toolbar { display:grid; grid-template-columns:2fr 1fr 1fr 1fr auto; gap:10px; }
  .bugs-split { display:grid; grid-template-columns:1.05fr 1fr; gap:16px; align-items:start; }

  .item-list { display:flex; flex-direction:column; gap:9px; }
  .row {
    border:1px solid var(--border); border-radius:14px; padding:13px; background:var(--soft);
    display:flex; flex-direction:column; gap:7px; cursor:pointer; transition:border-color 0.12s ease;
  }
  .row:hover { border-color:#3B4A70; }
  .row.active { border-color:var(--violet); box-shadow:0 0 0 1px rgba(139,92,246,0.35); }
  .top { display:flex; justify-content:space-between; align-items:center; gap:8px; flex-wrap:wrap; }
  .mono { font-family:Consolas,monospace; font-size:12px; color:var(--muted); word-break:break-word; }

  .badge { font-size:11px; font-weight:800; padding:3px 9px; border-radius:999px; background:var(--violet-soft); color:#C4B5FD; border:1px solid rgba(139,92,246,0.35); white-space:nowrap; }
  .badge.Open, .badge.beta { background:var(--cyan-soft); color:#67E8F9; border-color:rgba(6,182,212,0.35); }
  .badge.Fixed, .badge.active, .badge.stable { background:var(--emerald-soft); color:var(--emerald); border-color:rgba(16,185,129,0.35); }
  .badge.Rejected, .badge.disabled { background:var(--coral-soft); color:var(--coral); border-color:rgba(248,113,113,0.35); }
  .badge.Reviewing, .badge.InProgress, .badge.pending, .badge.closed { background:var(--amber-soft); color:var(--amber); border-color:rgba(245,158,11,0.35); }
  .badge.archived { background:var(--soft); color:var(--muted); border-color:var(--border); }

  pre { background:#0B0F19; border:1px solid var(--border); border-radius:12px; padding:10px; overflow:auto; max-height:200px; font-size:11.5px; color:var(--muted); white-space:pre-wrap; word-break:break-word; }
  img.att { width:108px; height:108px; border-radius:11px; border:1px solid var(--border); object-fit:cover; cursor:zoom-in; }

  .switch-row { display:flex; justify-content:space-between; align-items:center; gap:12px; padding:11px 12px; background:var(--soft); border:1px solid var(--border); border-radius:13px; }
  .switch-row input[type="checkbox"] { width:22px; height:22px; accent-color:var(--violet); cursor:pointer; flex-shrink:0; }

  .progress-wrap { width:100%; height:10px; background:#0B0F19; border:1px solid var(--border); border-radius:999px; overflow:hidden; display:none; }
  .progress-fill { height:100%; width:0%; background:linear-gradient(90deg, var(--cyan), var(--violet)); transition:width 0.15s ease; }

  .mobile-back { display:none; }
  .toast {
    position:fixed; right:20px; bottom:20px; z-index:90; max-width:min(380px, calc(100vw - 32px));
    background:var(--card); border:1px solid var(--violet); border-radius:14px; padding:12px 15px;
    font-size:13.5px; font-weight:600; box-shadow:0 12px 32px rgba(0,0,0,0.45); display:none;
  }
  .toast.err { border-color:var(--coral); color:#FECACA; }
  .lightbox {
    position:fixed; inset:0; background:rgba(5,8,15,0.92); z-index:100; display:none;
    flex-direction:column; align-items:center; justify-content:center; padding:16px; gap:14px;
  }
  .lightbox img { max-width:95vw; max-height:80dvh; border-radius:12px; border:1px solid var(--border); }

  @media (max-width: 1120px) {
    .kpi-grid { grid-template-columns:repeat(2, minmax(0, 1fr)); }
    .toolbar { grid-template-columns:1fr 1fr; }
    .toolbar input { grid-column:1 / -1; }
    .grid-2 { grid-template-columns:1fr; }
  }

  @media (max-width: 900px) {
    .shell { grid-template-columns:1fr; }
    .sidebar { display:none; }
    .mobile-bar {
      position:sticky; top:0; z-index:40; background:rgba(15,21,37,0.94); backdrop-filter:blur(10px);
      border-bottom:1px solid var(--border); padding:10px 14px;
      display:flex; align-items:center; justify-content:space-between; gap:10px;
    }
    .mobile-bar .brand { padding:0; }
    .mobile-bar .brand img { width:34px; height:34px; }
    .main { padding:14px 14px calc(86px + env(safe-area-inset-bottom)); }
    .kpi-grid { grid-template-columns:repeat(2, minmax(0, 1fr)); gap:10px; }
    .kpi-num { font-size:23px; }
    .bugs-split { grid-template-columns:1fr; }
    .bugs-split.has-selection #bugsListPanel { display:none; }
    .bugs-split:not(.has-selection) #bugDetailPanel { display:none; }
    .mobile-back { display:inline-flex; margin-bottom:4px; }
    input[type="text"], input[type="number"], input[type="url"], input[type="search"], select, textarea { font-size:16px; }
    .toast { left:16px; right:16px; bottom:calc(78px + env(safe-area-inset-bottom)); max-width:none; }
    .bottom-nav {
      position:fixed; left:0; right:0; bottom:0; z-index:50;
      background:rgba(15,21,37,0.96); backdrop-filter:blur(12px);
      border-top:1px solid var(--border);
      display:grid; grid-template-columns:repeat(4, 1fr);
      padding:6px 6px calc(6px + env(safe-area-inset-bottom)); gap:4px;
    }
    .bnav-btn {
      border:none; background:transparent; color:var(--muted); border-radius:12px;
      min-height:50px; padding:5px 4px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px;
      font-size:11px; font-weight:700; cursor:pointer;
    }
    .bnav-btn.active { background:var(--violet-soft); color:#E9D5FF; }
  }
</style>
</head>
<body>

<div class="mobile-bar">
  <div class="brand">
    <img src="${PENGU_FAVICON_DATA_URI}" alt="Pengu" />
    <div>
      <div class="brand-title">Pengu Console</div>
      <div class="brand-sub"><span class="status-dot"></span>Railway Live</div>
    </div>
  </div>
  <div style="display:flex;gap:8px">
    <button class="btn sec sm" type="button" id="mobileRefreshBtn">Sync</button>
    <form method="POST" action="/api/admin/logout"><button class="btn sec sm" type="submit">Out</button></form>
  </div>
</div>

<div class="shell">
  <aside class="sidebar">
    <div class="brand">
      <img src="${PENGU_FAVICON_DATA_URI}" alt="Pengu" />
      <div>
        <div class="brand-title">Pengu Console</div>
        <div class="brand-sub">Dev Rayan Admin</div>
      </div>
    </div>
    <nav class="nav-list" id="sideNav">
      <button class="nav-btn" type="button" data-tab="overview"><span>Overview</span><span class="pill">Live</span></button>
      <button class="nav-btn" type="button" data-tab="bugs"><span>Bug Reports</span><span class="pill warn" id="navBugsBadge">0</span></button>
      <button class="nav-btn" type="button" data-tab="beta"><span>Beta &amp; Testers</span><span class="pill warn" id="navBetaBadge">0</span></button>
      <button class="nav-btn" type="button" data-tab="releases"><span>Releases &amp; APKs</span><span class="pill" id="navRelBadge">0</span></button>
    </nav>
    <div class="side-footer">
      <div class="mono"><span class="status-dot"></span>Railway Production</div>
      <button class="btn sec sm" type="button" id="copyUrlSideBtn">Copy App Server URL</button>
      <form method="POST" action="/api/admin/logout"><button class="btn danger sm" style="width:100%" type="submit">Sign Out</button></form>
    </div>
  </aside>

  <main class="main">
    <!-- TAB 1: OVERVIEW -->
    <section class="tab" id="tab-overview">
      <div class="page-head">
        <div>
          <h2>Developer WebApp Console</h2>
          <div class="sub">Windows Desktop &amp; Mobile control center — Pengu app clean rehta hai, saare admin controls yahan hain.</div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <button class="btn sec" type="button" id="copyUrlBtn">Copy Server URL</button>
          <button class="btn" type="button" id="refreshAllBtn">Refresh Data</button>
        </div>
      </div>

      <div class="kpi-grid">
        <div class="card"><div class="kpi-label">Open Bug Reports</div><div class="kpi-num" id="kpiOpenBugs">0</div><div class="mono" id="kpiTotalBugs">0 total</div></div>
        <div class="card"><div class="kpi-label">Active Beta Testers</div><div class="kpi-num" id="kpiActiveTesters">0</div><div class="mono" id="kpiClosedSlots">Closed: 0 / 100</div></div>
        <div class="card"><div class="kpi-label">Closed Beta Queue</div><div class="kpi-num" id="kpiPendingClosed">0</div><div class="mono">Awaiting Dev approval</div></div>
        <div class="card"><div class="kpi-label">APK Downloads</div><div class="kpi-num" id="kpiDownloads">0</div><div class="mono" id="kpiReleaseCount">0 releases published</div></div>
      </div>

      <div class="grid-2">
        <div class="card">
          <strong>Quick Developer Actions</strong>
          <div style="display:flex;gap:10px;flex-wrap:wrap">
            <button class="btn cyan" type="button" data-jump="bugs">Inspect Bug Reports</button>
            <button class="btn ok" type="button" data-jump="beta">Approve Closed Beta Queue</button>
            <button class="btn" type="button" data-jump="releases">Upload / Publish APK</button>
          </div>
          <div class="mono" id="serverMetaLine">Loading Railway runtime metadata...</div>
        </div>
        <div class="card">
          <strong>Pengu App Connection URL</strong>
          <p class="sub">Is HTTPS URL ko Pengu Android App ke <strong>Report a Bug &rarr; Server Settings</strong> ya <strong>My Testing Program</strong> me daalein:</p>
          <pre id="originPreview"></pre>
        </div>
      </div>
    </section>

    <!-- TAB 2: BUG REPORTS -->
    <section class="tab" id="tab-bugs">
      <div class="page-head">
        <div>
          <h2>Bug &amp; Crash Reports</h2>
          <div class="sub">Screenshots dekhein, diagnostics inspect karein, aur status / developer reply bhejein.</div>
        </div>
        <button class="btn sec" type="button" id="bugsRefreshBtn">Reload Bugs</button>
      </div>

      <div class="card">
        <div class="toolbar">
          <input type="search" id="qBug" placeholder="Search ID (BR-...), title, or description..." />
          <select id="catBug"><option value="All">All Categories</option></select>
          <select id="stBug"><option value="All">All Statuses</option></select>
          <select id="verBug"><option value="All">All Versions</option></select>
          <button class="btn" type="button" id="filterBugsBtn">Filter</button>
        </div>
      </div>

      <div class="bugs-split" id="bugsSplit">
        <div class="card" id="bugsListPanel">
          <div class="top"><strong>Submitted Reports</strong><span id="bugsMeta" class="mono">0 total</span></div>
          <div class="item-list" id="bugsList"></div>
          <div class="top" style="margin-top:6px">
            <button class="btn sec sm" type="button" id="prevPageBtn">Prev</button>
            <span class="mono" id="pageMeta">Page 1 / 1</span>
            <button class="btn sec sm" type="button" id="nextPageBtn">Next</button>
          </div>
        </div>

        <div class="card" id="bugDetailPanel">
          <button class="btn sec sm mobile-back" type="button" id="mobileBugBackBtn">&larr; Back to Bug List</button>
          <div id="bugDetailBody"><p class="mono">Select a bug report from the list to inspect diagnostics, screenshots, and send a reply.</p></div>
        </div>
      </div>
    </section>

    <!-- TAB 3: BETA PROGRAM & TESTERS -->
    <section class="tab" id="tab-beta">
      <div class="page-head">
        <div>
          <h2>Beta Program &amp; Tester Access</h2>
          <div class="sub">Open/Closed Beta toggles, seat limits, VIP invite codes, aur Closed Beta approvals.</div>
        </div>
        <button class="btn sec" type="button" id="betaRefreshBtn">Reload Beta Data</button>
      </div>

      <div class="grid-2">
        <div class="card">
          <strong>Beta Program Settings</strong>
          <label class="switch-row">
            <span><strong>Open Beta Enabled</strong><br><span class="mono">Koi bhi bina approval ke join kar sakta hai</span></span>
            <input type="checkbox" id="cfgOpenBeta" />
          </label>
          <label class="switch-row">
            <span><strong>Closed Beta Enabled</strong><br><span class="mono">Invite code ya Dev Rayan approval zaroori</span></span>
            <input type="checkbox" id="cfgClosedBeta" />
          </label>
          <div>
            <label class="fld" for="cfgCapacity">Closed Beta Seat Capacity</label>
            <input type="number" id="cfgCapacity" min="1" max="100000" />
          </div>
          <div>
            <label class="fld" for="cfgInvites">Closed Beta Invite Codes (comma or new line)</label>
            <textarea id="cfgInvites" rows="2" placeholder="PENGU-BETA-VIP"></textarea>
          </div>
          <div>
            <label class="fld" for="cfgKnownBugs">Known Bugs Shown Inside App (one per line)</label>
            <textarea id="cfgKnownBugs" rows="2" placeholder="Optional known issues list..."></textarea>
          </div>
          <div>
            <label class="fld" for="cfgExpFeatures">Experimental Features (one per line)</label>
            <textarea id="cfgExpFeatures" rows="2" placeholder="Early access features..."></textarea>
          </div>
          <button class="btn" type="button" id="saveBetaCfgBtn">Save Beta Config</button>
        </div>

        <div class="card">
          <div class="top">
            <strong>Testers &amp; Closed Beta Requests</strong>
            <span class="badge closed" id="testerSlotsBadge">0 / 100 Closed</span>
          </div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
            <select id="testerFilterStatus">
              <option value="all">All Statuses</option>
              <option value="pending">Pending Approval</option>
              <option value="active">Active Testers</option>
            </select>
            <input type="search" id="testerSearch" placeholder="Device ID / Model..." />
          </div>
          <div class="item-list" id="testersList"></div>
        </div>
      </div>
    </section>

    <!-- TAB 4: RELEASES & APKS -->
    <section class="tab" id="tab-releases">
      <div class="page-head">
        <div>
          <h2>Releases &amp; APK Distribution</h2>
          <div class="sub">Direct APK upload karein (Railway volume par) ya HTTPS URL publish karein, promote aur disable karein.</div>
        </div>
        <button class="btn sec" type="button" id="relRefreshBtn">Reload Releases</button>
      </div>

      <div class="grid-2">
        <div class="card">
          <strong>Publish New Release</strong>
          <div>
            <label class="fld" for="apkFileInput">Option A: Upload APK File Directly (max 120 MB)</label>
            <input type="file" id="apkFileInput" accept=".apk,application/vnd.android.package-archive" style="color:var(--muted);font-size:13px" />
            <div class="progress-wrap" id="apkProgressWrap" style="margin-top:8px"><div class="progress-fill" id="apkProgressFill"></div></div>
            <div class="mono" id="apkUploadInfo" style="margin-top:6px"></div>
          </div>

          <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
            <div>
              <label class="fld" for="relVersion">Version</label>
              <input type="text" id="relVersion" placeholder="1.3.0-beta.1" />
            </div>
            <div>
              <label class="fld" for="relChannel">Release Channel</label>
              <select id="relChannel"></select>
            </div>
          </div>

          <div>
            <label class="fld" for="relApkUrl">Option B / Auto-filled APK HTTPS URL</label>
            <input type="url" id="relApkUrl" placeholder="https://pengu-beta-backend-production.up.railway.app/apks/..." />
          </div>
          <div>
            <label class="fld" for="relSha">SHA-256 Checksum (auto-computed on upload)</label>
            <input type="text" id="relSha" placeholder="64-char hex SHA-256" />
          </div>
          <div>
            <label class="fld" for="relNotes">What's New / Release Notes (Markdown supported)</label>
            <textarea id="relNotes" rows="4" placeholder="## What's New&#10;- Experimental feature..."></textarea>
          </div>
          <label class="switch-row">
            <span><strong>Publish Enabled Immediately</strong></span>
            <input type="checkbox" id="relEnabled" checked />
          </label>
          <button class="btn" type="button" id="publishRelBtn">Publish Release Build</button>
        </div>

        <div class="card">
          <div class="top">
            <strong>Published Builds</strong>
            <select id="relFilterChannel" style="width:auto;min-width:130px;min-height:36px">
              <option value="all">All Channels</option>
            </select>
          </div>
          <div class="item-list" id="releasesList"></div>
        </div>
      </div>
    </section>
  </main>
</div>

<nav class="bottom-nav" id="bottomNav">
  <button class="bnav-btn" type="button" data-tab="overview"><span>Overview</span></button>
  <button class="bnav-btn" type="button" data-tab="bugs"><span>Bugs (<span id="mobBugsCount">0</span>)</span></button>
  <button class="bnav-btn" type="button" data-tab="beta"><span>Beta (<span id="mobBetaCount">0</span>)</span></button>
  <button class="bnav-btn" type="button" data-tab="releases"><span>Releases</span></button>
</nav>

<div class="toast" id="toast"></div>
<div class="lightbox" id="lightbox">
  <img id="lightboxImg" alt="Full Bug Screenshot" />
  <div style="display:flex;gap:10px">
    <a class="btn cyan" id="lightboxDownload" target="_blank" rel="noopener">Open Original</a>
    <button class="btn sec" type="button" id="lightboxClose">Close</button>
  </div>
</div>

<script>
const CATEGORIES = ${JSON.stringify(validCategories)};
const STATUSES = ${JSON.stringify(validStatuses)};
const CHANNELS = ${JSON.stringify(validChannels)};
let activeTab = ${JSON.stringify(safeInitialTab)};
let currentBugId = null;
let bugPage = 1;
let bugTotalPages = 1;

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, function(c) {
    return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
  });
}

function showToast(msg, isErr) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'toast' + (isErr ? ' err' : '');
  el.style.display = 'block';
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(function() { el.style.display = 'none'; }, 3400);
}

async function apiJson(url, opts) {
  const res = await fetch(url, opts || {});
  if (res.status === 401) {
    location.href = '/console';
    throw new Error('Session expired');
  }
  const data = await res.json().catch(function() { return {}; });
  if (!res.ok) throw new Error(data.error || ('Request failed (' + res.status + ')'));
  return data;
}

function switchTab(tab) {
  activeTab = tab;
  document.querySelectorAll('.tab').forEach(function(el) {
    el.classList.toggle('active', el.id === 'tab-' + tab);
  });
  document.querySelectorAll('[data-tab]').forEach(function(btn) {
    btn.classList.toggle('active', btn.dataset.tab === tab);
  });
  if (window.innerWidth <= 900) window.scrollTo(0, 0);
  if (tab === 'overview') loadOverview();
  if (tab === 'bugs') loadBugs(bugPage);
  if (tab === 'beta') loadBetaAll();
  if (tab === 'releases') loadReleases();
}

function initSelectOptions() {
  const cat = document.getElementById('catBug');
  const st = document.getElementById('stBug');
  const relCh = document.getElementById('relChannel');
  const relFilter = document.getElementById('relFilterChannel');
  CATEGORIES.forEach(function(c) { cat.add(new Option(c, c)); });
  STATUSES.forEach(function(s) { st.add(new Option(s, s)); });
  CHANNELS.forEach(function(ch) {
    relCh.add(new Option(ch.toUpperCase(), ch));
    relFilter.add(new Option(ch.toUpperCase(), ch));
  });
  relCh.value = 'beta';
  document.getElementById('originPreview').textContent = location.origin;
}

async function copyOriginUrl() {
  try {
    await navigator.clipboard.writeText(location.origin);
    showToast('Server URL copied: ' + location.origin);
  } catch (_) {
    showToast('URL: ' + location.origin);
  }
}

async function loadOverview() {
  try {
    const ov = await apiJson('/api/admin/overview');
    document.getElementById('kpiOpenBugs').textContent = String(ov.bugs.open);
    document.getElementById('kpiTotalBugs').textContent = ov.bugs.total + ' total · ' + ov.bugs.fixed + ' fixed';
    document.getElementById('kpiActiveTesters').textContent = String(ov.beta.openActive + ov.beta.closedActive);
    document.getElementById('kpiClosedSlots').textContent = 'Closed: ' + ov.beta.closedActive + ' / ' + ov.beta.closedCapacity;
    document.getElementById('kpiPendingClosed').textContent = String(ov.beta.closedPending);
    document.getElementById('kpiDownloads').textContent = String(ov.releases.totalDownloads);
    document.getElementById('kpiReleaseCount').textContent = ov.releases.total + ' builds (' + ov.releases.beta + ' beta, ' + ov.releases.stable + ' stable)';
    document.getElementById('serverMetaLine').textContent =
      'Backend v' + ov.server.version + ' · ' + ov.server.nodeVersion + ' · Storage: ' + ov.server.dataDir;

    document.getElementById('navBugsBadge').textContent = String(ov.bugs.open);
    document.getElementById('navBetaBadge').textContent = String(ov.beta.closedPending);
    document.getElementById('navRelBadge').textContent = String(ov.releases.total);
    document.getElementById('mobBugsCount').textContent = String(ov.bugs.open);
    document.getElementById('mobBetaCount').textContent = String(ov.beta.closedPending);

    fillBetaConfigForm(ov.config, ov.beta.closedActive);
  } catch (err) {
    showToast(err.message, true);
  }
}

function fillBetaConfigForm(cfg, closedActive) {
  if (!cfg) return;
  document.getElementById('cfgOpenBeta').checked = !!cfg.openBetaEnabled;
  document.getElementById('cfgClosedBeta').checked = !!cfg.closedBetaEnabled;
  document.getElementById('cfgCapacity').value = String(cfg.closedCapacity || 100);
  document.getElementById('cfgInvites').value = (cfg.inviteCodes || []).join(', ');
  document.getElementById('cfgKnownBugs').value = (cfg.knownBugs || []).join('\\n');
  document.getElementById('cfgExpFeatures').value = (cfg.experimentalFeatures || []).join('\\n');
  document.getElementById('testerSlotsBadge').textContent = closedActive + ' / ' + cfg.closedCapacity + ' Closed';
}

async function loadBugs(page) {
  try {
    bugPage = page || 1;
    const params = new URLSearchParams({
      search: document.getElementById('qBug').value,
      category: document.getElementById('catBug').value,
      status: document.getElementById('stBug').value,
      version: document.getElementById('verBug').value,
      page: String(bugPage),
      limit: '20'
    });
    const data = await apiJson('/api/admin/bug-reports?' + params.toString());
    bugTotalPages = data.pagination.totalPages || 1;
    document.getElementById('bugsMeta').textContent = data.pagination.total + ' total';
    document.getElementById('pageMeta').textContent = 'Page ' + data.pagination.page + ' / ' + bugTotalPages;
    document.getElementById('prevPageBtn').disabled = bugPage <= 1;
    document.getElementById('nextPageBtn').disabled = bugPage >= bugTotalPages;

    const verSel = document.getElementById('verBug');
    const prevVer = verSel.value;
    verSel.innerHTML = '<option value="All">All Versions</option>';
    (data.availableVersions || []).forEach(function(v) { verSel.add(new Option(v, v)); });
    if ([].slice.call(verSel.options).some(function(o) { return o.value === prevVer; })) verSel.value = prevVer;

    const listEl = document.getElementById('bugsList');
    if (!data.reports.length) {
      listEl.innerHTML = '<div class="mono">No bug reports match the current filters.</div>';
      return;
    }
    listEl.innerHTML = data.reports.map(function(r) {
      const sClass = r.status.replace(/\\s+/g, '');
      const act = r.id === currentBugId ? ' active' : '';
      return '<div class="row' + act + '" data-bug-id="' + esc(r.id) + '">' +
        '<div class="top"><span class="mono">' + esc(r.id) + ' &middot; v' + esc(r.appVersion) + '</span>' +
        '<span class="badge ' + esc(sClass) + '">' + esc(r.status) + '</span></div>' +
        '<strong>' + esc(r.title) + '</strong>' +
        '<span class="mono">' + esc(r.category) + ' &middot; ' + esc(r.attachmentCount) + ' screenshot(s) &middot; ' + esc(String(r.createdAt || '').slice(0, 10)) + '</span>' +
      '</div>';
    }).join('');
  } catch (err) {
    showToast(err.message, true);
  }
}

async function openBugDetail(id) {
  try {
    currentBugId = id;
    document.getElementById('bugsSplit').classList.add('has-selection');
    if (window.innerWidth <= 900) window.scrollTo(0, 0);
    document.querySelectorAll('#bugsList .row').forEach(function(el) {
      el.classList.toggle('active', el.dataset.bugId === id);
    });
    const r = await apiJson('/api/admin/bug-reports/' + encodeURIComponent(id));
    const atts = (r.attachments || []).map(function(a) {
      const url = '/api/admin/bug-reports/' + encodeURIComponent(r.id) + '/attachments/' + encodeURIComponent(a.id);
      return '<img class="att" src="' + esc(url) + '" data-lightbox="' + esc(url) + '" alt="Bug Screenshot" />';
    }).join('');
    const opts = STATUSES.map(function(s) {
      return '<option value="' + esc(s) + '"' + (s === r.status ? ' selected' : '') + '>' + esc(s) + '</option>';
    }).join('');

    document.getElementById('bugDetailBody').innerHTML =
      '<div class="top"><strong>' + esc(r.title) + '</strong><span class="mono">' + esc(r.id) + '</span></div>' +
      '<div class="mono" style="margin:6px 0 10px">' + esc(r.category) + ' &middot; Created ' + esc(r.createdAt) + '</div>' +
      '<p style="font-size:14px;line-height:1.5;margin-bottom:10px">' + esc(r.description) + '</p>' +
      (r.stepsToReproduce ? '<label class="fld">Steps to Reproduce</label><pre>' + esc(r.stepsToReproduce) + '</pre>' : '') +
      (r.expectedBehavior ? '<label class="fld">Expected Behavior</label><pre>' + esc(r.expectedBehavior) + '</pre>' : '') +
      (r.actualBehavior ? '<label class="fld">Actual Behavior</label><pre>' + esc(r.actualBehavior) + '</pre>' : '') +
      (atts ? '<label class="fld">Screenshots (Tap to Zoom)</label><div style="display:flex;gap:8px;flex-wrap:wrap">' + atts + '</div>' : '') +
      (r.diagnostics ? '<label class="fld">Sanitized Diagnostics</label><pre>' + esc(JSON.stringify(r.diagnostics, null, 2)) + '</pre>' : '') +
      '<label class="fld" for="editBugStatus">Status</label><select id="editBugStatus">' + opts + '</select>' +
      '<label class="fld" for="editBugReply">Developer Response (Synced to User App)</label>' +
      '<textarea id="editBugReply" rows="3" placeholder="Write fix status or guidance...">' + esc(r.developerResponse || '') + '</textarea>' +
      '<div style="display:flex;gap:10px;flex-wrap:wrap;margin-top:12px">' +
        '<button class="btn" type="button" id="saveBugBtn">Update Status &amp; Reply</button>' +
        '<button class="btn danger" type="button" id="delBugBtn">Delete Report</button>' +
      '</div>';
  } catch (err) {
    showToast(err.message, true);
  }
}

async function saveBugUpdate() {
  if (!currentBugId) return;
  try {
    await apiJson('/api/admin/bug-reports/' + encodeURIComponent(currentBugId) + '/status', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: document.getElementById('editBugStatus').value,
        developerResponse: document.getElementById('editBugReply').value
      })
    });
    showToast('Bug report updated & synced');
    await Promise.all([loadBugs(bugPage), openBugDetail(currentBugId), loadOverview()]);
  } catch (err) {
    showToast(err.message, true);
  }
}

async function deleteSelectedBug() {
  if (!currentBugId || !confirm('Permanently delete ' + currentBugId + '?')) return;
  try {
    await apiJson('/api/admin/bug-reports/' + encodeURIComponent(currentBugId), { method: 'DELETE' });
    currentBugId = null;
    document.getElementById('bugsSplit').classList.remove('has-selection');
    document.getElementById('bugDetailBody').innerHTML = '<div class="mono">Report deleted.</div>';
    showToast('Report deleted');
    await Promise.all([loadBugs(bugPage), loadOverview()]);
  } catch (err) {
    showToast(err.message, true);
  }
}

async function loadBetaAll() {
  try {
    await loadOverview();
    const params = new URLSearchParams({
      status: document.getElementById('testerFilterStatus').value,
      search: document.getElementById('testerSearch').value
    });
    const data = await apiJson('/api/admin/beta/testers?' + params.toString());
    document.getElementById('testerSlotsBadge').textContent = data.closedActiveCount + ' / ' + data.closedCapacity + ' Closed';
    const listEl = document.getElementById('testersList');
    if (!data.testers.length) {
      listEl.innerHTML = '<div class="mono">No enrolled beta testers yet.</div>';
      return;
    }
    listEl.innerHTML = data.testers.map(function(t) {
      const canApprove = t.enrollmentType === 'closed' && t.status !== 'active';
      return '<div class="row" style="cursor:default">' +
        '<div class="top"><strong>' + esc(t.deviceModel || 'Android Device') + '</strong>' +
        '<div style="display:flex;gap:6px"><span class="badge ' + esc(t.enrollmentType) + '">' + esc(t.enrollmentType.toUpperCase()) + '</span>' +
        '<span class="badge ' + esc(t.status) + '">' + esc(t.status) + '</span></div></div>' +
        '<div class="mono">ID: ' + esc(t.deviceId) + (t.inviteCodeUsed ? ' &middot; Invite: ' + esc(t.inviteCodeUsed) : '') + '</div>' +
        '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:4px">' +
          (canApprove ? '<button class="btn ok sm" type="button" data-tester-approve="' + esc(t.deviceId) + '">Approve Closed Beta</button>' : '') +
          '<button class="btn danger sm" type="button" data-tester-remove="' + esc(t.deviceId) + '">Remove Tester</button>' +
        '</div>' +
      '</div>';
    }).join('');
  } catch (err) {
    showToast(err.message, true);
  }
}

async function saveBetaConfig() {
  try {
    const payload = {
      openBetaEnabled: document.getElementById('cfgOpenBeta').checked,
      closedBetaEnabled: document.getElementById('cfgClosedBeta').checked,
      closedCapacity: Number(document.getElementById('cfgCapacity').value || 100),
      inviteCodes: document.getElementById('cfgInvites').value,
      knownBugs: document.getElementById('cfgKnownBugs').value,
      experimentalFeatures: document.getElementById('cfgExpFeatures').value
    };
    await apiJson('/api/admin/beta/config', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    showToast('Beta Program settings saved');
    await loadBetaAll();
  } catch (err) {
    showToast(err.message, true);
  }
}

async function loadReleases() {
  try {
    const ch = document.getElementById('relFilterChannel').value;
    const data = await apiJson('/api/admin/releases?channel=' + encodeURIComponent(ch));
    const listEl = document.getElementById('releasesList');
    if (!data.releases.length) {
      listEl.innerHTML = '<div class="mono">No releases published in this filter yet.</div>';
      return;
    }
    listEl.innerHTML = data.releases.map(function(r) {
      const canPromote = r.channel === 'beta' || r.channel === 'closed';
      return '<div class="row" style="cursor:default">' +
        '<div class="top"><strong>Pengu v' + esc(r.version) + '</strong>' +
        '<div style="display:flex;gap:6px"><span class="badge ' + esc(r.channel) + '">' + esc(r.channel.toUpperCase()) + '</span>' +
        '<span class="badge ' + (r.enabled === false ? 'disabled' : 'Fixed') + '">' + (r.enabled === false ? 'DISABLED' : 'LIVE') + '</span></div></div>' +
        '<div class="mono">Downloads: ' + esc(r.downloadCount || 0) + ' &middot; ' + esc(String(r.publishedAt || '').slice(0, 10)) + '</div>' +
        (r.sha256 ? '<div class="mono">SHA-256: ' + esc(r.sha256.slice(0, 16)) + '...</div>' : '') +
        '<pre style="max-height:90px">' + esc(r.releaseNotes) + '</pre>' +
        '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
          '<button class="btn sec sm" type="button" data-rel-toggle="' + esc(r.id) + '" data-enabled="' + (r.enabled === false ? '1' : '0') + '">' + (r.enabled === false ? 'Enable' : 'Disable') + '</button>' +
          (canPromote ? '<button class="btn ok sm" type="button" data-rel-promote="' + esc(r.id) + '">Promote to Stable</button>' : '') +
          '<button class="btn danger sm" type="button" data-rel-delete="' + esc(r.id) + '">Delete</button>' +
        '</div>' +
      '</div>';
    }).join('');
  } catch (err) {
    showToast(err.message, true);
  }
}

function uploadSelectedApk(file) {
  if (!file) return;
  const wrap = document.getElementById('apkProgressWrap');
  const fill = document.getElementById('apkProgressFill');
  const info = document.getElementById('apkUploadInfo');
  wrap.style.display = 'block';
  fill.style.width = '5%';
  info.textContent = 'Uploading ' + file.name + '...';

  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/admin/releases/upload-apk');
  xhr.setRequestHeader('Content-Type', 'application/vnd.android.package-archive');
  xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
  xhr.upload.onprogress = function(ev) {
    if (ev.lengthComputable) {
      const pct = Math.round((ev.loaded / ev.total) * 100);
      fill.style.width = pct + '%';
      info.textContent = 'Uploading: ' + pct + '%';
    }
  };
  xhr.onload = function() {
    try {
      const data = JSON.parse(xhr.responseText || '{}');
      if (xhr.status >= 200 && xhr.status < 300) {
        fill.style.width = '100%';
        document.getElementById('relApkUrl').value = data.apkUrl;
        document.getElementById('relSha').value = data.sha256;
        info.textContent = 'Uploaded (' + (data.sizeBytes / (1024 * 1024)).toFixed(2) + ' MB) · SHA-256 auto-filled';
        showToast('APK uploaded & SHA-256 calculated');
      } else {
        info.textContent = data.error || 'Upload failed';
        showToast(data.error || 'APK upload failed', true);
      }
    } catch (_) {
      showToast('Unexpected upload response', true);
    }
  };
  xhr.onerror = function() { showToast('Network error during APK upload', true); };
  xhr.send(file);
}

async function publishRelease() {
  try {
    const payload = {
      version: document.getElementById('relVersion').value,
      channel: document.getElementById('relChannel').value,
      apkUrl: document.getElementById('relApkUrl').value,
      sha256: document.getElementById('relSha').value,
      releaseNotes: document.getElementById('relNotes').value,
      enabled: document.getElementById('relEnabled').checked
    };
    await apiJson('/api/admin/releases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    document.getElementById('relVersion').value = '';
    document.getElementById('relApkUrl').value = '';
    document.getElementById('relSha').value = '';
    document.getElementById('relNotes').value = '';
    document.getElementById('apkUploadInfo').textContent = '';
    showToast('Release published live!');
    await Promise.all([loadReleases(), loadOverview()]);
  } catch (err) {
    showToast(err.message, true);
  }
}

document.addEventListener('click', async function(ev) {
  const nav = ev.target.closest('[data-tab]');
  if (nav) { switchTab(nav.dataset.tab); return; }

  const jump = ev.target.closest('[data-jump]');
  if (jump) { switchTab(jump.dataset.jump); return; }

  const bugRow = ev.target.closest('[data-bug-id]');
  if (bugRow) { openBugDetail(bugRow.dataset.bugId); return; }

  const lbImg = ev.target.closest('[data-lightbox]');
  if (lbImg) {
    document.getElementById('lightboxImg').src = lbImg.dataset.lightbox;
    document.getElementById('lightboxDownload').href = lbImg.dataset.lightbox;
    document.getElementById('lightbox').style.display = 'flex';
    return;
  }

  if (ev.target.id === 'saveBugBtn') { saveBugUpdate(); return; }
  if (ev.target.id === 'delBugBtn') { deleteSelectedBug(); return; }

  const approveBtn = ev.target.closest('[data-tester-approve]');
  if (approveBtn) {
    try {
      await apiJson('/api/admin/beta/testers/' + encodeURIComponent(approveBtn.dataset.testerApprove), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'active' })
      });
      showToast('Closed beta tester approved');
      await loadBetaAll();
    } catch (err) { showToast(err.message, true); }
    return;
  }

  const remBtn = ev.target.closest('[data-tester-remove]');
  if (remBtn) {
    const devId = remBtn.dataset.testerRemove;
    if (!confirm('Remove beta tester ' + devId + '?')) return;
    try {
      await apiJson('/api/admin/beta/testers/' + encodeURIComponent(devId), { method: 'DELETE' });
      showToast('Tester removed');
      await loadBetaAll();
    } catch (err) { showToast(err.message, true); }
    return;
  }

  const togBtn = ev.target.closest('[data-rel-toggle]');
  if (togBtn) {
    try {
      await apiJson('/api/admin/releases/' + encodeURIComponent(togBtn.dataset.relToggle), {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled: togBtn.dataset.enabled === '1' })
      });
      showToast('Release visibility updated');
      await Promise.all([loadReleases(), loadOverview()]);
    } catch (err) { showToast(err.message, true); }
    return;
  }

  const proBtn = ev.target.closest('[data-rel-promote]');
  if (proBtn) {
    try {
      await apiJson('/api/admin/releases/' + encodeURIComponent(proBtn.dataset.relPromote) + '/promote', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}'
      });
      showToast('Build promoted to Stable!');
      await Promise.all([loadReleases(), loadOverview()]);
    } catch (err) { showToast(err.message, true); }
    return;
  }

  const delRelBtn = ev.target.closest('[data-rel-delete]');
  if (delRelBtn) {
    if (!confirm('Delete this release build?')) return;
    try {
      await apiJson('/api/admin/releases/' + encodeURIComponent(delRelBtn.dataset.relDelete), { method: 'DELETE' });
      showToast('Release deleted');
      await Promise.all([loadReleases(), loadOverview()]);
    } catch (err) { showToast(err.message, true); }
    return;
  }
});

document.getElementById('copyUrlBtn').addEventListener('click', copyOriginUrl);
document.getElementById('copyUrlSideBtn').addEventListener('click', copyOriginUrl);
document.getElementById('refreshAllBtn').addEventListener('click', loadOverview);
document.getElementById('mobileRefreshBtn').addEventListener('click', function() { switchTab(activeTab); });
document.getElementById('bugsRefreshBtn').addEventListener('click', function() { loadBugs(bugPage); });
document.getElementById('filterBugsBtn').addEventListener('click', function() { loadBugs(1); });
document.getElementById('prevPageBtn').addEventListener('click', function() { if (bugPage > 1) loadBugs(bugPage - 1); });
document.getElementById('nextPageBtn').addEventListener('click', function() { if (bugPage < bugTotalPages) loadBugs(bugPage + 1); });
document.getElementById('mobileBugBackBtn').addEventListener('click', function() {
  document.getElementById('bugsSplit').classList.remove('has-selection');
});
document.getElementById('betaRefreshBtn').addEventListener('click', loadBetaAll);
document.getElementById('saveBetaCfgBtn').addEventListener('click', saveBetaConfig);
document.getElementById('testerFilterStatus').addEventListener('change', loadBetaAll);
document.getElementById('testerSearch').addEventListener('input', loadBetaAll);
document.getElementById('relRefreshBtn').addEventListener('click', loadReleases);
document.getElementById('relFilterChannel').addEventListener('change', loadReleases);
document.getElementById('publishRelBtn').addEventListener('click', publishRelease);
document.getElementById('apkFileInput').addEventListener('change', function(e) {
  if (e.target.files && e.target.files[0]) uploadSelectedApk(e.target.files[0]);
});
document.getElementById('lightboxClose').addEventListener('click', function() {
  document.getElementById('lightbox').style.display = 'none';
});

initSelectOptions();
switchTab(activeTab);
</script>
</body>
</html>`;
}

module.exports = {
  renderConsoleLoginHtml,
  renderConsoleAppHtml,
};
