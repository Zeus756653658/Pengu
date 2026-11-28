const crypto = require("node:crypto");
const { safeEqualHexOrUtf8 } = require("./bugReportService");

const DEFAULT_JSON_LIMIT = 1024 * 1024;
const SESSION_COOKIE_NAME = "pengu_admin_session";
const SESSION_TTL_MS = 8 * 60 * 60 * 1000;

function makeLimitError(maxBytes, messagePrefix = "Request exceeds") {
  const limitLabel =
    maxBytes >= 1024 * 1024
      ? `${Math.round(maxBytes / (1024 * 1024))} MB`
      : `${Math.round(maxBytes / 1024)} KB`;
  const err = new Error(`${messagePrefix} ${limitLabel} limit`);
  err.status = 413;
  err.code = "PAYLOAD_TOO_LARGE";
  return err;
}

function readBufferedBodyWithLimit(req, maxBytes = DEFAULT_JSON_LIMIT) {
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
          const err = makeLimitError(maxBytes);
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
        fail(makeLimitError(maxBytes));
        return;
      }
      settled = true;
      resolve(Buffer.concat(chunks));
    });

    req.on("aborted", () => {
      fail(Object.assign(new Error("Request aborted"), { status: 400, code: "REQUEST_ABORTED" }));
    });
    req.on("error", fail);
  });
}

async function readJsonWithLimit(req, maxBytes = DEFAULT_JSON_LIMIT) {
  const buf = await readBufferedBodyWithLimit(req, maxBytes);
  try {
    const text = buf.toString("utf8");
    return text ? JSON.parse(text) : {};
  } catch (_) {
    const err = new Error("Malformed JSON payload");
    err.status = 400;
    err.code = "INVALID_JSON";
    throw err;
  }
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

function sendHtml(res, status, html, extraHeaders = {}) {
  res.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Content-Length": Buffer.byteLength(html),
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Cache-Control": "no-store",
    "Content-Security-Policy":
      "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; form-action 'self'; frame-ancestors 'none';",
    ...extraHeaders,
  });
  res.end(html);
}

function escapeHtml(s) {
  return String(s || "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]));
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
      try {
        map[k] = decodeURIComponent(v);
      } catch (_) {
        map[k] = v;
      }
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

function getPublicBaseUrl(req) {
  const protoHeader = req.headers["x-forwarded-proto"];
  const proto =
    typeof protoHeader === "string" && protoHeader.trim()
      ? protoHeader.split(",")[0].trim()
      : req.socket?.encrypted
        ? "https"
        : "http";
  const host = String(req.headers["x-forwarded-host"] || req.headers["host"] || "").trim();
  if (!host) return "";
  return `${proto}://${host}`;
}

function createAdminAuth({ adminToken, sessionSecret, renderLoginHtml }) {
  const configuredToken = adminToken || process.env.PENGU_ADMIN_TOKEN || "";
  const hmacSecret =
    sessionSecret || process.env.PENGU_SESSION_SECRET || crypto.randomBytes(32).toString("hex");
  const loginLimiter = createRateLimiter({ windowMs: 15 * 60 * 1000, max: 20 });

  function signSessionToken() {
    const exp = Date.now() + SESSION_TTL_MS;
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
    const expectedSig = crypto
      .createHmac("sha256", hmacSecret)
      .update(`${parts[0]}.${parts[1]}`)
      .digest("hex");
    return safeEqualHexOrUtf8(parts[2], expectedSig);
  }

  function isAuthorized(req) {
    if (!configuredToken) return false;
    const auth = req.headers["authorization"] || "";
    if (auth.startsWith("Bearer ")) {
      const token = auth.slice(7).trim();
      if (safeEqualHexOrUtf8(token, configuredToken)) return true;
    }
    const cookies = parseCookies(req.headers["cookie"]);
    return verifySessionToken(cookies[SESSION_COOKIE_NAME]);
  }

  function rejectIfUnauth(req, res) {
    if (!configuredToken) {
      sendJson(res, 503, {
        error: "Administrator authentication is not configured on server",
        code: "ADMIN_UNCONFIGURED",
      });
      return true;
    }
    if (!isAuthorized(req)) {
      sendJson(res, 401, { error: "Unauthorized administrator access", code: "UNAUTHORIZED" });
      return true;
    }
    return false;
  }

  function safeRedirectTarget(rawTarget) {
    const val = String(rawTarget || "").trim();
    if (val === "/console" || val === "/admin" || val === "/admin/bug-reports") {
      return val;
    }
    return "/console";
  }

  async function handleAuthRoutes(req, res, pathname) {
    const method = req.method || "GET";
    const ip = getClientIp(req);

    if (method === "POST" && pathname === "/api/admin/login") {
      const rate = loginLimiter(ip);
      if (!rate.allowed) {
        sendHtml(
          res,
          429,
          renderLoginHtml("Too many login attempts. Please wait 15 minutes."),
          { "Retry-After": String(rate.retryAfterSec) }
        );
        return true;
      }

      const raw = (await readBufferedBodyWithLimit(req, 64 * 1024)).toString("utf8");
      const ct = req.headers["content-type"] || "";
      let token = "";
      let redirectTo = "/console";
      if (ct.includes("application/json")) {
        try {
          const parsed = JSON.parse(raw || "{}");
          token = String(parsed.token || "");
          redirectTo = safeRedirectTarget(parsed.redirectTo);
        } catch (_) {
          sendJson(res, 400, { error: "Malformed login JSON body", code: "INVALID_JSON" });
          return true;
        }
      } else {
        const params = new URLSearchParams(raw);
        token = params.get("token") || "";
        redirectTo = safeRedirectTarget(params.get("next"));
      }

      if (!configuredToken || !safeEqualHexOrUtf8(token.trim(), configuredToken)) {
        if (ct.includes("application/json")) {
          sendJson(res, 401, { error: "Invalid administrator token", code: "UNAUTHORIZED" });
        } else {
          sendHtml(
            res,
            401,
            renderLoginHtml(
              !configuredToken
                ? "PENGU_ADMIN_TOKEN is not configured on the server."
                : "Invalid administrator token.",
              redirectTo
            )
          );
        }
        return true;
      }

      const isHttpsReq =
        req.socket?.encrypted ||
        String(req.headers["x-forwarded-proto"] || "").split(",")[0].trim() === "https";
      const secureFlag = isHttpsReq ? "; Secure" : "";
      const sessionCookie = `${SESSION_COOKIE_NAME}=${encodeURIComponent(
        signSessionToken()
      )}; HttpOnly; SameSite=Strict; Path=/${secureFlag}`;

      if (ct.includes("application/json")) {
        sendJson(res, 200, { authenticated: true, redirectTo }, { "Set-Cookie": sessionCookie });
      } else {
        res.writeHead(302, { Location: redirectTo, "Set-Cookie": sessionCookie });
        res.end();
      }
      return true;
    }

    if (method === "POST" && pathname === "/api/admin/logout") {
      res.writeHead(302, {
        Location: "/console",
        "Set-Cookie": `${SESSION_COOKIE_NAME}=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0`,
      });
      res.end();
      return true;
    }

    return false;
  }

  return {
    isConfigured: () => !!configuredToken,
    isAuthorized,
    rejectIfUnauth,
    handleAuthRoutes,
  };
}

module.exports = {
  DEFAULT_JSON_LIMIT,
  readBufferedBodyWithLimit,
  readJsonWithLimit,
  sendJson,
  sendHtml,
  escapeHtml,
  createRateLimiter,
  getClientIp,
  getPublicBaseUrl,
  createAdminAuth,
};
