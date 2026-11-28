const fs = require("node:fs");
const crypto = require("node:crypto");
const {
  VALID_CATEGORIES,
  VALID_STATUSES,
  safeEqualHexOrUtf8,
  hashSha256,
} = require("../services/bugReportService");

const MAX_REQUEST_BYTES = 12 * 1024 * 1024; // 12 MB cap for base64 attachments + JSON

function readJsonWithLimit(req, maxBytes = MAX_REQUEST_BYTES) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    let settled = false;
    let exceeded = false;

    const fail = (err) => {
      if (settled) return;
      settled = true;
      reject(err);
    };

    const makeTooLargeError = () => {
      const limitLabel =
        maxBytes >= 1024 * 1024
          ? `${Math.round(maxBytes / (1024 * 1024))} MB`
          : `${Math.round(maxBytes / 1024)} KB`;
      const err = new Error(`Request exceeds ${limitLabel} limit`);
      err.status = 413;
      err.code = "PAYLOAD_TOO_LARGE";
      return err;
    };

    const declaredLength = Number(req.headers["content-length"] || 0);
    if (Number.isFinite(declaredLength) && declaredLength > maxBytes) {
      exceeded = true;
    }

    req.on("data", (chunk) => {
      if (settled) return;
      total += chunk.length;
      if (exceeded || total > maxBytes) {
        exceeded = true;
        chunks.length = 0;
        if (total > maxBytes * 4) {
          req.resume();
          const err = makeTooLargeError();
          err.closeConnection = true;
          fail(err);
        }
        return;
      }
      chunks.push(chunk);
    });

    req.on("end", () => {
      if (settled) return;
      if (exceeded) {
        fail(makeTooLargeError());
        return;
      }
      try {
        const text = Buffer.concat(chunks).toString("utf8");
        settled = true;
        resolve(text ? JSON.parse(text) : {});
      } catch (_) {
        const err = new Error("Malformed JSON payload");
        err.status = 400;
        err.code = "INVALID_JSON";
        fail(err);
      }
    });

    req.on("aborted", () => {
      fail(Object.assign(new Error("Request aborted"), { status: 400, code: "REQUEST_ABORTED" }));
    });
    req.on("error", fail);
  });
}

function sendJson(res, status, data, extraHeaders = {}) {
  const payload = JSON.stringify(data, null, 2);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "X-Content-Type-Options": "nosniff",
    "Cache-Control": "no-store",
    ...extraHeaders,
  });
  res.end(payload);
}

function sendHtml(res, status, html) {
  res.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Content-Length": Buffer.byteLength(html),
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Cache-Control": "no-store",
    "Content-Security-Policy":
      "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data: blob:;",
  });
  res.end(html);
}

function createRateLimiter({ windowMs, max }) {
  const buckets = new Map();
  return function check(key) {
    const now = Date.now();
    const record = buckets.get(key);
    if (!record || now - record.windowStart > windowMs) {
      buckets.set(key, { windowStart: now, count: 1 });
      return { allowed: true, retryAfterSec: 0 };
    }
    if (record.count >= max) {
      const retryAfterSec = Math.max(1, Math.ceil((record.windowStart + windowMs - now) / 1000));
      return { allowed: false, retryAfterSec };
    }
    record.count += 1;
    return { allowed: true, retryAfterSec: 0 };
  };
}

function parseCookies(cookieHeader) {
  const map = {};
  if (!cookieHeader) return map;
  for (const part of String(cookieHeader).split(";")) {
    const idx = part.indexOf("=");
    if (idx > 0) {
      const k = part.slice(0, idx).trim();
      const v = part.slice(idx + 1).trim();
      map[k] = decodeURIComponent(v);
    }
  }
  return map;
}

function getClientIp(req) {
  const xff = req.headers["x-forwarded-for"];
  if (typeof xff === "string" && xff.trim()) {
    return xff.split(",")[0].trim();
  }
  return req.socket?.remoteAddress || "unknown";
}

function renderAdminLoginHtml(errorMsg = "") {
  const errHtml = errorMsg
    ? `<div class="err" role="alert">${escapeHtml(errorMsg)}</div>`
    : "";
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0" />
<title>Pengu Developer Bug Dashboard — Sign In</title>
<style>
  :root { --bg:#0B0F19; --card:#131929; --border:#232C43; --text:#F8FAFC; --muted:#94A3B8; --violet:#8B5CF6; --coral:#F87171; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { background:var(--bg); color:var(--text); font-family:"Segoe UI",Roboto,system-ui,sans-serif; min-height:100vh; display:grid; place-items:center; padding:24px; }
  .card { width:100%; max-width:410px; background:var(--card); border:1px solid var(--border); border-radius:20px; padding:28px; display:flex; flex-direction:column; gap:16px; }
  .eyebrow { font-size:11px; font-weight:800; letter-spacing:0.08em; color:var(--violet); text-transform:uppercase; }
  h1 { font-size:21px; font-weight:800; }
  p { font-size:13px; line-height:1.5; color:var(--muted); }
  label { display:block; font-size:12px; font-weight:600; color:var(--muted); margin-bottom:6px; }
  input { width:100%; height:46px; border-radius:12px; border:1px solid var(--border); background:#0B0F19; color:var(--text); padding:0 14px; font-size:14px; }
  button { width:100%; height:48px; border-radius:14px; border:none; background:var(--violet); color:#fff; font-size:14px; font-weight:700; cursor:pointer; }
  .err { background:rgba(248,113,113,0.14); border:1px solid rgba(248,113,113,0.35); color:var(--coral); padding:10px 12px; border-radius:12px; font-size:12.5px; }
</style>
</head>
<body>
  <form class="card" method="POST" action="/api/admin/login">
    <div class="eyebrow">Pengu Developer Security Gate</div>
    <h1>Bug Report Admin Console</h1>
    <p>Server-side authenticated access only. Sign in with your configured administrator token.</p>
    ${errHtml}
    <div>
      <label for="token">Administrator Token</label>
      <input id="token" name="token" type="password" autocomplete="current-password" required placeholder="PENGU_ADMIN_TOKEN" />
    </div>
    <button type="submit">Authenticate &amp; Open Dashboard</button>
  </form>
</body>
</html>`;
}

function renderAdminDashboardHtml() {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0" />
<title>Pengu Developer Bug Management</title>
<style>
  :root { --bg:#0B0F19; --card:#131929; --soft:#1A2238; --border:#232C43; --text:#F8FAFC; --muted:#94A3B8; --violet:#8B5CF6; --cyan:#06B6D4; --emerald:#10B981; --amber:#F59E0B; --coral:#F87171; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { background:var(--bg); color:var(--text); font-family:"Segoe UI",Roboto,system-ui,sans-serif; padding:24px; max-width:1360px; margin:0 auto; }
  .hdr { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:12px; margin-bottom:20px; }
  .eyebrow { font-size:11px; font-weight:800; letter-spacing:0.08em; color:var(--violet); text-transform:uppercase; }
  h1 { font-size:22px; font-weight:800; }
  .toolbar { display:grid; grid-template-columns:2fr 1fr 1fr 1fr auto; gap:10px; margin-bottom:16px; }
  input, select, textarea { background:var(--card); border:1px solid var(--border); border-radius:12px; color:var(--text); padding:10px 12px; font-size:13px; }
  button { height:42px; padding:0 16px; border-radius:12px; border:none; background:var(--violet); color:#fff; font-weight:700; font-size:13px; cursor:pointer; }
  button.sec { background:var(--soft); border:1px solid var(--border); color:var(--text); }
  button.del { background:rgba(248,113,113,0.16); border:1px solid rgba(248,113,113,0.4); color:var(--coral); }
  .layout { display:grid; grid-template-columns:1.2fr 1fr; gap:16px; align-items:start; }
  .panel { background:var(--card); border:1px solid var(--border); border-radius:18px; padding:16px; display:flex; flex-direction:column; gap:10px; }
  .row { border:1px solid var(--border); border-radius:14px; padding:13px; cursor:pointer; background:var(--soft); display:flex; flex-direction:column; gap:6px; }
  .row.active { border-color:var(--violet); }
  .top { display:flex; justify-content:space-between; align-items:center; gap:8px; }
  .mono { font-family:Consolas,monospace; font-size:11.5px; color:var(--muted); }
  .badge { font-size:11px; font-weight:800; padding:3px 9px; border-radius:999px; background:rgba(139,92,246,0.16); color:#C4B5FD; border:1px solid rgba(139,92,246,0.35); }
  .badge.Fixed { background:rgba(16,185,129,0.14); color:var(--emerald); border-color:rgba(16,185,129,0.35); }
  .badge.Rejected { background:rgba(248,113,113,0.14); color:var(--coral); border-color:rgba(248,113,113,0.35); }
  .badge.Reviewing, .badge.InProgress { background:rgba(245,158,11,0.14); color:var(--amber); border-color:rgba(245,158,11,0.35); }
  pre { background:#0B0F19; border:1px solid var(--border); border-radius:12px; padding:10px; overflow:auto; max-height:180px; font-size:11px; color:var(--muted); white-space:pre-wrap; }
  img.att { max-width:140px; max-height:140px; border-radius:10px; border:1px solid var(--border); object-fit:cover; }
  @media (max-width:960px) { .toolbar, .layout { grid-template-columns:1fr; } }
</style>
</head>
<body>
  <div class="hdr">
    <div><div class="eyebrow">Pengu Support Ops</div><h1>Bug Report Management Dashboard</h1></div>
    <form method="POST" action="/api/admin/logout"><button class="sec" type="submit">Sign Out</button></form>
  </div>
  <div class="toolbar">
    <input id="q" placeholder="Search by report ID, title, or description..." />
    <select id="cat"><option value="All">All Categories</option></select>
    <select id="st"><option value="All">All Statuses</option></select>
    <select id="ver"><option value="All">All Versions</option></select>
    <button onclick="loadList(1)">Filter</button>
  </div>
  <div class="layout">
    <div class="panel">
      <div class="top"><strong>Submitted Bug Reports</strong><span id="meta" class="mono"></span></div>
      <div id="list" style="display:flex;flex-direction:column;gap:8px"></div>
    </div>
    <div class="panel" id="detail"><p class="mono">Select a report from the list to inspect diagnostics and update status.</p></div>
  </div>
<script>
const CATEGORIES = ${JSON.stringify(VALID_CATEGORIES)};
const STATUSES = ${JSON.stringify(VALID_STATUSES)};
for (const c of CATEGORIES) document.getElementById('cat').add(new Option(c, c));
for (const s of STATUSES) document.getElementById('st').add(new Option(s, s));

let selectedId = null;
async function loadList(page=1) {
  const params = new URLSearchParams({
    search: document.getElementById('q').value,
    category: document.getElementById('cat').value,
    status: document.getElementById('st').value,
    version: document.getElementById('ver').value,
    page: String(page),
    limit: '25'
  });
  const res = await fetch('/api/admin/bug-reports?' + params.toString());
  if (res.status === 401) { location.href = '/admin/bug-reports'; return; }
  const data = await res.json();
  document.getElementById('meta').textContent = data.pagination.total + ' total';
  const verSel = document.getElementById('ver');
  const prevVer = verSel.value;
  verSel.innerHTML = '<option value="All">All Versions</option>';
  for (const v of (data.availableVersions || [])) verSel.add(new Option(v, v));
  if ([...verSel.options].some(o => o.value === prevVer)) verSel.value = prevVer;

  const listEl = document.getElementById('list');
  listEl.innerHTML = '';
  if (!data.reports.length) { listEl.innerHTML = '<div class="mono">No matching bug reports.</div>'; return; }
  for (const r of data.reports) {
    const el = document.createElement('div');
    el.className = 'row' + (r.id === selectedId ? ' active' : '');
    el.onclick = () => openDetail(r.id);
    const sClass = r.status.replace(/\s+/g, '');
    el.innerHTML = '<div class="top"><span class="mono">' + esc(r.id) + ' &middot; v' + esc(r.appVersion) + '</span><span class="badge ' + sClass + '">' + esc(r.status) + '</span></div>'
      + '<strong>' + esc(r.title) + '</strong>'
      + '<span class="mono">' + esc(r.category) + ' &middot; ' + esc(r.createdAt.slice(0,10)) + '</span>';
    listEl.appendChild(el);
  }
}

async function openDetail(id) {
  selectedId = id;
  const res = await fetch('/api/admin/bug-reports/' + encodeURIComponent(id));
  if (!res.ok) return;
  const r = await res.json();
  const detail = document.getElementById('detail');
  const atts = (r.attachments || []).map(a =>
    '<a href="/api/admin/bug-reports/' + encodeURIComponent(r.id) + '/attachments/' + encodeURIComponent(a.id) + '" target="_blank"><img class="att" src="/api/admin/bug-reports/' + encodeURIComponent(r.id) + '/attachments/' + encodeURIComponent(a.id) + '" alt="Screenshot" /></a>'
  ).join('');
  detail.innerHTML =
    '<div class="top"><strong>' + esc(r.title) + '</strong><span class="mono">' + esc(r.id) + '</span></div>'
    + '<div class="mono">' + esc(r.category) + ' &middot; Created ' + esc(r.createdAt) + '</div>'
    + '<p style="font-size:13px">' + esc(r.description) + '</p>'
    + (r.stepsToReproduce ? '<div><div class="mono">STEPS</div><pre>' + esc(r.stepsToReproduce) + '</pre></div>' : '')
    + (r.expectedBehavior ? '<div><div class="mono">EXPECTED</div><pre>' + esc(r.expectedBehavior) + '</pre></div>' : '')
    + (r.actualBehavior ? '<div><div class="mono">ACTUAL</div><pre>' + esc(r.actualBehavior) + '</pre></div>' : '')
    + (atts ? '<div style="display:flex;gap:8px;flex-wrap:wrap">' + atts + '</div>' : '')
    + (r.diagnostics ? '<div><div class="mono">DIAGNOSTICS</div><pre>' + esc(JSON.stringify(r.diagnostics, null, 2)) + '</pre></div>' : '')
    + '<label class="mono">STATUS</label><select id="newSt">' + STATUSES.map(s => '<option ' + (s===r.status?'selected':'') + '>' + esc(s) + '</option>').join('') + '</select>'
    + '<label class="mono">DEVELOPER RESPONSE</label><textarea id="reply" rows="3" placeholder="Optional response visible to tester...">' + esc(r.developerResponse || '') + '</textarea>'
    + '<div style="display:flex;gap:10px"><button onclick="saveStatus()">Update Status</button><button class="del" onclick="deleteReport()">Delete Spam</button></div>';
}

async function saveStatus() {
  if (!selectedId) return;
  const res = await fetch('/api/admin/bug-reports/' + encodeURIComponent(selectedId) + '/status', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status: document.getElementById('newSt').value, developerResponse: document.getElementById('reply').value })
  });
  if (res.ok) { await loadList(); await openDetail(selectedId); }
}

async function deleteReport() {
  if (!selectedId || !confirm('Permanently delete report ' + selectedId + '?')) return;
  const res = await fetch('/api/admin/bug-reports/' + encodeURIComponent(selectedId), { method: 'DELETE' });
  if (res.ok) { selectedId = null; document.getElementById('detail').innerHTML = '<div class="mono">Report deleted.</div>'; await loadList(); }
}

function esc(s) { return String(s || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
loadList(1);
</script>
</body>
</html>`;
}

function escapeHtml(s) {
  return String(s || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function createBugReportHandler({ service, adminToken, sessionSecret }) {
  const configuredToken = adminToken || process.env.PENGU_ADMIN_TOKEN || "";
  const hmacSecret = sessionSecret || process.env.PENGU_SESSION_SECRET || crypto.randomBytes(32).toString("hex");
  const submitLimiter = createRateLimiter({ windowMs: 15 * 60 * 1000, max: 15 });
  const readLimiter = createRateLimiter({ windowMs: 60 * 1000, max: 60 });
  const loginLimiter = createRateLimiter({ windowMs: 15 * 60 * 1000, max: 20 });

  function signSessionToken() {
    const exp = Date.now() + 8 * 60 * 60 * 1000;
    const payload = `admin.${exp}`;
    const sig = crypto.createHmac("sha256", hmacSecret).update(payload).digest("hex");
    return `${payload}.${sig}`;
  }

  function verifySessionToken(cookieVal) {
    if (!configuredToken || !cookieVal) return false;
    const parts = String(cookieVal).split(".");
    if (parts.length !== 3 || parts[0] !== "admin") return false;
    const exp = Number(parts[1]);
    if (!Number.isFinite(exp) || Date.now() > exp) return false;
    const expectedSig = crypto.createHmac("sha256", hmacSecret).update(`${parts[0]}.${parts[1]}`).digest("hex");
    return safeEqualHexOrUtf8(parts[2], expectedSig);
  }

  function isAuthorizedAdmin(req) {
    if (!configuredToken) return false;
    const auth = req.headers["authorization"] || "";
    if (auth.startsWith("Bearer ")) {
      const token = auth.slice(7).trim();
      if (safeEqualHexOrUtf8(token, configuredToken)) return true;
    }
    const cookies = parseCookies(req.headers["cookie"]);
    return verifySessionToken(cookies.pengu_admin_session);
  }

  function rejectIfUnauth(req, res) {
    if (!configuredToken) {
      sendJson(res, 503, { error: "Administrator authentication is not configured on server", code: "ADMIN_UNCONFIGURED" });
      return true;
    }
    if (!isAuthorizedAdmin(req)) {
      sendJson(res, 401, { error: "Unauthorized administrator access", code: "UNAUTHORIZED" });
      return true;
    }
    return false;
  }

  return async function handleBugReportRequest(req, res, pathname, reqUrl) {
    const method = req.method || "GET";
    const ip = getClientIp(req);

    // ---- Public Endpoints ----
    if (method === "POST" && pathname === "/api/bug-reports") {
      const rate = submitLimiter(ip);
      if (!rate.allowed) {
        sendJson(
          res,
          429,
          { error: "Too many bug report submissions. Please wait before retrying.", code: "RATE_LIMITED" },
          { "Retry-After": String(rate.retryAfterSec) }
        );
        return true;
      }
      const body = await readJsonWithLimit(req, MAX_REQUEST_BYTES);
      const created = await service.createReport(body);
      sendJson(res, 201, created);
      return true;
    }

    const statusMatch = pathname.match(/^\/api\/bug-reports\/([^/]+)\/status$/);
    if (method === "GET" && statusMatch) {
      const rate = readLimiter(ip);
      if (!rate.allowed) {
        sendJson(res, 429, { error: "Rate limit exceeded", code: "RATE_LIMITED" });
        return true;
      }
      const id = decodeURIComponent(statusMatch[1]);
      const authHeader = req.headers["authorization"] || "";
      const trackingToken =
        req.headers["x-tracking-token"] ||
        (authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "");
      const out = service.getPublicStatus(id, trackingToken);
      sendJson(res, out.status, out.status === 200 ? out.data : { error: out.error });
      return true;
    }

    const pubAttMatch = pathname.match(/^\/api\/bug-reports\/([^/]+)\/attachments\/([^/]+)$/);
    if (method === "GET" && pubAttMatch) {
      const reportId = decodeURIComponent(pubAttMatch[1]);
      const attId = decodeURIComponent(pubAttMatch[2]);
      const token = req.headers["x-tracking-token"] || "";
      const out = service.resolveAttachment(reportId, attId, { isAdmin: false, trackingToken: token });
      if (out.status !== 200) {
        sendJson(res, out.status, { error: out.error });
        return true;
      }
      res.writeHead(200, {
        "Content-Type": out.attachment.mimeType,
        "Content-Length": out.attachment.sizeBytes,
        "Content-Security-Policy": "sandbox",
        "X-Content-Type-Options": "nosniff",
        "Content-Disposition": `attachment; filename="${out.attachment.storedName}"`,
      });
      fs.createReadStream(out.filePath).pipe(res);
      return true;
    }

    // ---- Admin Session Dashboard ----
    if (method === "GET" && pathname === "/admin/bug-reports") {
      if (!configuredToken) {
        sendHtml(res, 503, renderAdminLoginHtml("PENGU_ADMIN_TOKEN is not set on server."));
        return true;
      }
      if (!isAuthorizedAdmin(req)) {
        sendHtml(res, 200, renderAdminLoginHtml());
        return true;
      }
      sendHtml(res, 200, renderAdminDashboardHtml());
      return true;
    }

    if (method === "POST" && pathname === "/api/admin/login") {
      const rate = loginLimiter(ip);
      if (!rate.allowed) {
        sendHtml(res, 429, renderAdminLoginHtml("Too many login attempts. Wait 15 minutes."));
        return true;
      }
      const chunks = [];
      for await (const c of req) chunks.push(c);
      const raw = Buffer.concat(chunks).toString("utf8");
      const ct = req.headers["content-type"] || "";
      const token = ct.includes("application/json")
        ? String((JSON.parse(raw || "{}").token || ""))
        : new URLSearchParams(raw).get("token") || "";

      if (!configuredToken || !safeEqualHexOrUtf8(token.trim(), configuredToken)) {
        if (ct.includes("application/json")) {
          sendJson(res, 401, { error: "Invalid administrator token" });
        } else {
          sendHtml(res, 401, renderAdminLoginHtml("Invalid administrator token."));
        }
        return true;
      }
      const isHttpsReq = req.socket?.encrypted || req.headers["x-forwarded-proto"] === "https";
      const secureFlag = isHttpsReq ? "; Secure" : "";
      const sessionCookie = `pengu_admin_session=${encodeURIComponent(signSessionToken())}; HttpOnly; SameSite=Strict; Path=/${secureFlag}`;
      if (ct.includes("application/json")) {
        sendJson(res, 200, { authenticated: true }, { "Set-Cookie": sessionCookie });
      } else {
        res.writeHead(302, { Location: "/admin/bug-reports", "Set-Cookie": sessionCookie });
        res.end();
      }
      return true;
    }

    if (method === "POST" && pathname === "/api/admin/logout") {
      res.writeHead(302, {
        Location: "/admin/bug-reports",
        "Set-Cookie": "pengu_admin_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0",
      });
      res.end();
      return true;
    }

    // ---- Admin API Endpoints ----
    if (method === "GET" && pathname === "/api/admin/bug-reports") {
      if (rejectIfUnauth(req, res)) return true;
      const result = service.listAdminReports({
        search: reqUrl.searchParams.get("search") || "",
        category: reqUrl.searchParams.get("category") || "All",
        status: reqUrl.searchParams.get("status") || "All",
        version: reqUrl.searchParams.get("version") || "All",
        page: reqUrl.searchParams.get("page") || "1",
        limit: reqUrl.searchParams.get("limit") || "20",
      });
      sendJson(res, 200, result);
      return true;
    }

    const adminAttMatch = pathname.match(/^\/api\/admin\/bug-reports\/([^/]+)\/attachments\/([^/]+)$/);
    if (method === "GET" && adminAttMatch) {
      if (rejectIfUnauth(req, res)) return true;
      const out = service.resolveAttachment(
        decodeURIComponent(adminAttMatch[1]),
        decodeURIComponent(adminAttMatch[2]),
        { isAdmin: true }
      );
      if (out.status !== 200) {
        sendJson(res, out.status, { error: out.error });
        return true;
      }
      res.writeHead(200, {
        "Content-Type": out.attachment.mimeType,
        "Content-Length": out.attachment.sizeBytes,
        "Content-Security-Policy": "sandbox",
        "X-Content-Type-Options": "nosniff",
      });
      fs.createReadStream(out.filePath).pipe(res);
      return true;
    }

    const adminStatusMatch = pathname.match(/^\/api\/admin\/bug-reports\/([^/]+)\/status$/);
    if (method === "PATCH" && adminStatusMatch) {
      if (rejectIfUnauth(req, res)) return true;
      const id = decodeURIComponent(adminStatusMatch[1]);
      const body = await readJsonWithLimit(req, 64 * 1024);
      const updated = await service.updateAdminReportStatus(id, body.status, body.developerResponse);
      sendJson(res, 200, updated);
      return true;
    }

    const adminIdMatch = pathname.match(/^\/api\/admin\/bug-reports\/([^/]+)$/);
    if (method === "GET" && adminIdMatch) {
      if (rejectIfUnauth(req, res)) return true;
      const item = service.getAdminReportById(decodeURIComponent(adminIdMatch[1]));
      if (!item) {
        sendJson(res, 404, { error: "Bug report not found" });
        return true;
      }
      sendJson(res, 200, item);
      return true;
    }

    if (method === "DELETE" && adminIdMatch) {
      if (rejectIfUnauth(req, res)) return true;
      const out = await service.deleteAdminReport(decodeURIComponent(adminIdMatch[1]));
      sendJson(res, 200, out);
      return true;
    }

    return false;
  };
}

module.exports = { createBugReportHandler, readJsonWithLimit, sendJson };
