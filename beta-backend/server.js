const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { createAdminAuth, readJsonWithLimit, sendJson } = require("./services/adminAuth");
const { BugReportService } = require("./services/bugReportService");
const { BetaStoreService } = require("./services/betaStoreService");
const { createBugReportHandler } = require("./routes/bugReports");
const { createAdminConsoleHandler } = require("./routes/adminConsole");
const { renderConsoleLoginHtml } = require("./views/consoleHtml");

function createBetaServer(options = {}) {
  const dataDir = options.dataDir || process.env.PENGU_DATA_DIR || path.join(__dirname, "data");
  const uploadsDir =
    options.uploadsDir || process.env.PENGU_UPLOADS_DIR || path.join(dataDir, "uploads");
  const auditLogPath = path.join(dataDir, "admin-audit.log");

  const betaStore = new BetaStoreService({
    dataDir,
    uploadsDir,
    auditLogPath,
  });

  const bugReportService = new BugReportService({
    dataDir,
    dbPath: options.bugDbPath || path.join(dataDir, "bug-reports.json"),
    attachmentsDir: options.attachmentsDir || path.join(dataDir, "attachments"),
    auditLogPath,
    duplicateWindowMs: options.duplicateWindowMs,
  });

  const auth = createAdminAuth({
    adminToken: options.adminToken,
    sessionSecret: options.sessionSecret,
    renderLoginHtml: renderConsoleLoginHtml,
  });

  const handleBugReports = createBugReportHandler({
    service: bugReportService,
    auth,
  });

  const handleAdminConsole = createAdminConsoleHandler({
    auth,
    betaStore,
    bugReportService,
  });

  const server = http.createServer(async (req, res) => {
    try {
      const reqUrl = new URL(req.url, "http://localhost");
      const pathname = reqUrl.pathname;
      const method = req.method || "GET";

      if (await auth.handleAuthRoutes(req, res, pathname)) {
        return;
      }
      if (await handleAdminConsole(req, res, pathname, reqUrl)) {
        return;
      }
      if (await handleBugReports(req, res, pathname, reqUrl)) {
        return;
      }

      if (method === "GET" && pathname === "/" && String(req.headers.accept || "").includes("text/html")) {
        res.writeHead(302, { Location: "/console" });
        res.end();
        return;
      }

      if (method === "GET" && (pathname === "/" || pathname === "/api/health")) {
        return sendJson(res, 200, {
          ok: true,
          service: "pengu-beta-backend",
          version: "1.2.0",
          consoleUrl: "/console",
          endpoints: [
            "/console",
            "/api/health",
            "/api/beta/config",
            "/api/bug-reports",
            "/admin/bug-reports",
          ],
        });
      }

      if (method === "GET" && pathname === "/api/beta/config") {
        return sendJson(res, 200, betaStore.getPublicConfig());
      }

      if (method === "GET" && pathname === "/api/beta/status") {
        const deviceId = reqUrl.searchParams.get("deviceId") || "";
        return sendJson(res, 200, betaStore.getPublicTesterStatus(deviceId));
      }

      if (method === "POST" && pathname === "/api/beta/enroll") {
        const body = await readJsonWithLimit(req, 1024 * 1024);
        const result = betaStore.enrollTester(body);
        return sendJson(res, result.status, result.data);
      }

      if (method === "POST" && pathname === "/api/beta/leave") {
        const body = await readJsonWithLimit(req, 1024 * 1024);
        return sendJson(res, 200, betaStore.leaveTester(body));
      }

      if (method === "GET" && pathname === "/api/releases") {
        const channel = reqUrl.searchParams.get("channel") || "all";
        const deviceId = reqUrl.searchParams.get("deviceId") || "";
        return sendJson(res, 200, {
          releases: betaStore.getPublicReleases({ channel, deviceId }),
        });
      }

      const trackMatch = pathname.match(/^\/api\/releases\/([^/]+)\/track-download$/);
      if (method === "POST" && trackMatch) {
        const id = decodeURIComponent(trackMatch[1]);
        const tracked = betaStore.trackReleaseDownload(id);
        if (!tracked) return sendJson(res, 404, { error: "Release not found" });
        return sendJson(res, 200, tracked);
      }

      if (method === "GET" && pathname.startsWith("/apks/")) {
        let rawName = pathname.slice("/apks/".length);
        try {
          rawName = decodeURIComponent(rawName);
        } catch (_) {}
        const fileName = path.basename(rawName);
        const fullPath = path.resolve(uploadsDir, fileName);
        if (
          !fullPath.startsWith(path.resolve(uploadsDir) + path.sep) ||
          !fs.existsSync(fullPath)
        ) {
          return sendJson(res, 404, { error: "APK not found" });
        }
        const stat = fs.statSync(fullPath);
        res.writeHead(200, {
          "Content-Type": "application/vnd.android.package-archive",
          "Content-Length": stat.size,
          "Content-Disposition": `attachment; filename="${fileName.replace(/"/g, "")}"`,
        });
        return fs.createReadStream(fullPath).pipe(res);
      }

      return sendJson(res, 404, { error: "Not found" });
    } catch (err) {
      if (res.headersSent) {
        res.destroy();
        return;
      }
      const code = err.status || 500;
      return sendJson(
        res,
        code,
        {
          error: code === 500 ? "Internal server error" : err.message,
          code: err.code || "SERVER_ERROR",
        },
        err.closeConnection ? { Connection: "close" } : {}
      );
    }
  });

  server.bugReportService = bugReportService;
  server.betaStore = betaStore;
  return server;
}

if (require.main === module) {
  const port = Number(process.env.PORT || 8787);
  const server = createBetaServer();
  server.listen(port, () => {
    console.log(`Pengu Beta & Bug Report Backend running on port ${port}`);
  });
}

module.exports = { createBetaServer };
