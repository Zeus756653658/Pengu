const fs = require("node:fs");
const {
  readJsonWithLimit,
  sendJson,
  sendHtml,
  createRateLimiter,
  getClientIp,
  createAdminAuth,
} = require("../services/adminAuth");
const { VALID_CATEGORIES, VALID_STATUSES } = require("../services/bugReportService");
const { VALID_CHANNELS } = require("../services/betaStoreService");
const { renderConsoleLoginHtml, renderConsoleAppHtml } = require("../views/consoleHtml");

const MAX_REQUEST_BYTES = 12 * 1024 * 1024; // 12 MB cap for base64 attachments + JSON

function createBugReportHandler({ service, adminToken, sessionSecret, auth }) {
  const effectiveAuth =
    auth ||
    createAdminAuth({
      adminToken,
      sessionSecret,
      renderLoginHtml: renderConsoleLoginHtml,
    });
  const submitLimiter = createRateLimiter({ windowMs: 15 * 60 * 1000, max: 15 });
  const readLimiter = createRateLimiter({ windowMs: 60 * 1000, max: 60 });

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

    // ---- Admin Bug Console Page (Unified WebApp Console on Bugs tab) ----
    if (method === "GET" && pathname === "/admin/bug-reports") {
      if (!effectiveAuth.isConfigured()) {
        sendHtml(
          res,
          503,
          renderConsoleLoginHtml("PENGU_ADMIN_TOKEN is not set on server.", "/admin/bug-reports")
        );
        return true;
      }
      if (!effectiveAuth.isAuthorized(req)) {
        sendHtml(res, 200, renderConsoleLoginHtml("", "/admin/bug-reports"));
        return true;
      }
      sendHtml(
        res,
        200,
        renderConsoleAppHtml({
          initialTab: "bugs",
          validCategories: VALID_CATEGORIES,
          validStatuses: VALID_STATUSES,
          validChannels: VALID_CHANNELS,
        })
      );
      return true;
    }

    if (!auth && (await effectiveAuth.handleAuthRoutes(req, res, pathname))) {
      return true;
    }

    // ---- Admin Bug API Endpoints ----
    if (method === "GET" && pathname === "/api/admin/bug-reports") {
      if (effectiveAuth.rejectIfUnauth(req, res)) return true;
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
      if (effectiveAuth.rejectIfUnauth(req, res)) return true;
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
      if (effectiveAuth.rejectIfUnauth(req, res)) return true;
      const id = decodeURIComponent(adminStatusMatch[1]);
      const body = await readJsonWithLimit(req, 64 * 1024);
      const updated = await service.updateAdminReportStatus(id, body.status, body.developerResponse);
      sendJson(res, 200, updated);
      return true;
    }

    const adminIdMatch = pathname.match(/^\/api\/admin\/bug-reports\/([^/]+)$/);
    if (method === "GET" && adminIdMatch) {
      if (effectiveAuth.rejectIfUnauth(req, res)) return true;
      const item = service.getAdminReportById(decodeURIComponent(adminIdMatch[1]));
      if (!item) {
        sendJson(res, 404, { error: "Bug report not found" });
        return true;
      }
      sendJson(res, 200, item);
      return true;
    }

    if (method === "DELETE" && adminIdMatch) {
      if (effectiveAuth.rejectIfUnauth(req, res)) return true;
      const out = await service.deleteAdminReport(decodeURIComponent(adminIdMatch[1]));
      sendJson(res, 200, out);
      return true;
    }

    return false;
  };
}

module.exports = { createBugReportHandler, readJsonWithLimit, sendJson };
