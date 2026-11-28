const {
  readBufferedBodyWithLimit,
  readJsonWithLimit,
  sendJson,
  sendHtml,
  getPublicBaseUrl,
} = require("../services/adminAuth");
const { VALID_CATEGORIES, VALID_STATUSES } = require("../services/bugReportService");
const { MAX_APK_UPLOAD_BYTES, VALID_CHANNELS } = require("../services/betaStoreService");
const { renderConsoleLoginHtml, renderConsoleAppHtml } = require("../views/consoleHtml");

function createAdminConsoleHandler({ auth, betaStore, bugReportService }) {
  return async function handleAdminConsoleRequest(req, res, pathname, reqUrl) {
    const method = req.method || "GET";

    // Unified WebApp Console pages (/console, /admin, /admin/)
    if (
      method === "GET" &&
      (pathname === "/console" || pathname === "/admin" || pathname === "/admin/")
    ) {
      if (!auth.isConfigured()) {
        sendHtml(res, 503, renderConsoleLoginHtml("PENGU_ADMIN_TOKEN is not set on server.", "/console"));
        return true;
      }
      if (!auth.isAuthorized(req)) {
        sendHtml(res, 200, renderConsoleLoginHtml("", pathname === "/admin/" ? "/console" : pathname));
        return true;
      }
      const tabParam = reqUrl.searchParams.get("tab") || "overview";
      sendHtml(
        res,
        200,
        renderConsoleAppHtml({
          initialTab: tabParam,
          validCategories: VALID_CATEGORIES,
          validStatuses: VALID_STATUSES,
          validChannels: VALID_CHANNELS,
        })
      );
      return true;
    }

    if (method === "GET" && pathname === "/api/admin/overview") {
      if (auth.rejectIfUnauth(req, res)) return true;
      sendJson(res, 200, betaStore.getOverview(bugReportService));
      return true;
    }

    if (method === "PATCH" && pathname === "/api/admin/beta/config") {
      if (auth.rejectIfUnauth(req, res)) return true;
      const body = await readJsonWithLimit(req, 256 * 1024);
      const updated = betaStore.updateAdminConfig(body);
      sendJson(res, 200, updated);
      return true;
    }

    if (method === "GET" && pathname === "/api/admin/beta/testers") {
      if (auth.rejectIfUnauth(req, res)) return true;
      const data = betaStore.listAdminTesters({
        status: reqUrl.searchParams.get("status") || "all",
        enrollmentType: reqUrl.searchParams.get("enrollmentType") || "all",
        search: reqUrl.searchParams.get("search") || "",
      });
      sendJson(res, 200, data);
      return true;
    }

    const testerMatch = pathname.match(/^\/api\/admin\/beta\/testers\/([^/]+)$/);
    if (testerMatch) {
      const deviceId = decodeURIComponent(testerMatch[1]);
      if (method === "PATCH") {
        if (auth.rejectIfUnauth(req, res)) return true;
        const body = await readJsonWithLimit(req, 64 * 1024);
        const updated = betaStore.updateAdminTesterStatus(deviceId, body.status);
        sendJson(res, 200, updated);
        return true;
      }
      if (method === "DELETE") {
        if (auth.rejectIfUnauth(req, res)) return true;
        const out = betaStore.removeAdminTester(deviceId);
        sendJson(res, 200, out);
        return true;
      }
    }

    if (method === "GET" && pathname === "/api/admin/releases") {
      if (auth.rejectIfUnauth(req, res)) return true;
      const data = betaStore.listAdminReleases({
        channel: reqUrl.searchParams.get("channel") || "all",
        search: reqUrl.searchParams.get("search") || "",
      });
      sendJson(res, 200, data);
      return true;
    }

    if (method === "POST" && pathname === "/api/admin/releases/upload-apk") {
      if (auth.rejectIfUnauth(req, res)) return true;
      const ct = String(req.headers["content-type"] || "").toLowerCase();
      const rawHdr = String(req.headers["x-filename"] || "pengu-release.apk");
      let fileName = rawHdr;
      try {
        fileName = decodeURIComponent(rawHdr);
      } catch (_) {}
      let buffer;
      if (ct.includes("application/json")) {
        const body = await readJsonWithLimit(req, 20 * 1024 * 1024);
        fileName = body.fileName || fileName;
        const rawB64 = String(body.base64 || "").trim();
        buffer = Buffer.from(rawB64, "base64");
      } else {
        buffer = await readBufferedBodyWithLimit(req, MAX_APK_UPLOAD_BYTES);
      }
      const saved = betaStore.saveUploadedApk({
        fileName,
        buffer,
        publicBaseUrl: getPublicBaseUrl(req),
      });
      sendJson(res, 201, saved);
      return true;
    }

    if (method === "POST" && pathname === "/api/admin/releases") {
      if (auth.rejectIfUnauth(req, res)) return true;
      const body = await readJsonWithLimit(req, 256 * 1024);
      const created = betaStore.publishRelease(body);
      sendJson(res, 201, created);
      return true;
    }

    const promoteMatch = pathname.match(/^\/api\/admin\/releases\/([^/]+)\/promote$/);
    if (method === "POST" && promoteMatch) {
      if (auth.rejectIfUnauth(req, res)) return true;
      const body = await readJsonWithLimit(req, 32 * 1024);
      const promoted = betaStore.promoteRelease(decodeURIComponent(promoteMatch[1]), body);
      sendJson(res, 200, promoted);
      return true;
    }

    const releaseMatch = pathname.match(/^\/api\/admin\/releases\/([^/]+)$/);
    if (releaseMatch) {
      const id = decodeURIComponent(releaseMatch[1]);
      if (method === "PATCH") {
        if (auth.rejectIfUnauth(req, res)) return true;
        const body = await readJsonWithLimit(req, 128 * 1024);
        const updated = betaStore.updateRelease(id, body);
        sendJson(res, 200, updated);
        return true;
      }
      if (method === "DELETE") {
        if (auth.rejectIfUnauth(req, res)) return true;
        const out = betaStore.deleteRelease(id);
        sendJson(res, 200, out);
        return true;
      }
    }

    return false;
  };
}

module.exports = { createAdminConsoleHandler };
