const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { BugReportService } = require("./services/bugReportService");
const { createBugReportHandler } = require("./routes/bugReports");

function readJsonBody(req, maxBytes = 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on("data", (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new Error("Payload too large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        const text = Buffer.concat(chunks).toString("utf8");
        resolve(text ? JSON.parse(text) : {});
      } catch (err) {
        reject(new Error("Invalid JSON body"));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, status, data) {
  const payload = JSON.stringify(data, null, 2);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
    "X-Content-Type-Options": "nosniff",
  });
  res.end(payload);
}

function createBetaServer(options = {}) {
  const dataDir = options.dataDir || process.env.PENGU_DATA_DIR || path.join(__dirname, "data");
  const uploadsDir =
    options.uploadsDir || process.env.PENGU_UPLOADS_DIR || path.join(dataDir, "uploads");
  const storePath = path.join(dataDir, "store.json");

  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(uploadsDir, { recursive: true });

  const bugReportService = new BugReportService({
    dataDir,
    dbPath: options.bugDbPath || path.join(dataDir, "bug-reports.json"),
    attachmentsDir: options.attachmentsDir || path.join(dataDir, "attachments"),
    auditLogPath: path.join(dataDir, "admin-audit.log"),
    duplicateWindowMs: options.duplicateWindowMs,
  });
  const handleBugReports = createBugReportHandler({
    service: bugReportService,
    adminToken: options.adminToken,
    sessionSecret: options.sessionSecret,
  });

  function loadStore() {
    if (fs.existsSync(storePath)) {
      try {
        return JSON.parse(fs.readFileSync(storePath, "utf8"));
      } catch (_) {}
    }
    return {
      config: {
        openBetaEnabled: true,
        closedBetaEnabled: true,
        closedCapacity: 100,
        inviteCodes: ["PENGU-BETA-VIP"],
        knownBugs: [],
        experimentalFeatures: [
          "Early access Pengu Music Player presets",
          "Next-generation format & Ringtone Engine",
        ],
      },
      testers: [],
      releases: [],
    };
  }

  let store = loadStore();
  function saveStore() {
    fs.writeFileSync(storePath, JSON.stringify(store, null, 2));
  }
  saveStore();

  function closedActiveCount() {
    return store.testers.filter(
      (t) => t.enrollmentType === "closed" && t.status === "active"
    ).length;
  }

  function getTesterStatus(deviceId) {
    if (!deviceId) return null;
    return store.testers.find((t) => t.deviceId === deviceId) || null;
  }

  const server = http.createServer(async (req, res) => {
    try {
      const reqUrl = new URL(req.url, "http://localhost");
      const pathname = reqUrl.pathname;
      const method = req.method || "GET";

      if (await handleBugReports(req, res, pathname, reqUrl)) {
        return;
      }

      if (method === "GET" && (pathname === "/" || pathname === "/api/health")) {
        return sendJson(res, 200, {
          ok: true,
          service: "pengu-beta-backend",
          version: "1.2.0",
          endpoints: [
            "/api/health",
            "/api/beta/config",
            "/api/bug-reports",
            "/admin/bug-reports",
          ],
        });
      }

      if (method === "GET" && pathname === "/api/beta/config") {
        return sendJson(res, 200, {
          openBetaEnabled: store.config.openBetaEnabled,
          closedBetaEnabled: store.config.closedBetaEnabled,
          closedCapacity: store.config.closedCapacity,
          closedActiveCount: closedActiveCount(),
          knownBugs: store.config.knownBugs,
          experimentalFeatures: store.config.experimentalFeatures,
        });
      }

      if (method === "GET" && pathname === "/api/beta/status") {
        const deviceId = reqUrl.searchParams.get("deviceId") || "";
        const tester = getTesterStatus(deviceId);
        return sendJson(res, 200, {
          enrolled: !!tester && tester.status === "active",
          enrollmentType: tester ? tester.enrollmentType : "none",
          status: tester ? tester.status : "not_enrolled",
          joinedAt: tester ? tester.joinedAt : null,
          closedActiveCount: closedActiveCount(),
          closedCapacity: store.config.closedCapacity,
        });
      }

      if (method === "POST" && pathname === "/api/beta/enroll") {
        const body = await readJsonBody(req);
        const deviceId = String(body.deviceId || "").trim();
        const enrollmentType = body.enrollmentType === "closed" ? "closed" : "open";
        const inviteCode = String(body.inviteCode || "").trim();
        const deviceModel = String(body.deviceModel || "Android Device").trim();
        if (!deviceId) {
          return sendJson(res, 400, { error: "deviceId is required" });
        }

        const existingIdx = store.testers.findIndex((t) => t.deviceId === deviceId);
        if (enrollmentType === "open") {
          if (!store.config.openBetaEnabled) {
            return sendJson(res, 403, { error: "Open Beta is currently paused" });
          }
          const entry = {
            deviceId,
            deviceModel,
            enrollmentType: "open",
            status: "active",
            joinedAt: new Date().toISOString(),
          };
          if (existingIdx >= 0) store.testers[existingIdx] = entry;
          else store.testers.push(entry);
          saveStore();
          return sendJson(res, 200, entry);
        }

        if (!store.config.closedBetaEnabled) {
          return sendJson(res, 403, { error: "Closed Beta is not accepting requests" });
        }
        const alreadyActive =
          existingIdx >= 0 &&
          store.testers[existingIdx].enrollmentType === "closed" &&
          store.testers[existingIdx].status === "active";
        if (!alreadyActive && closedActiveCount() >= store.config.closedCapacity) {
          return sendJson(res, 403, { error: "Closed Beta tester capacity reached" });
        }
        const validInvite =
          inviteCode.length > 0 && store.config.inviteCodes.includes(inviteCode);
        const entry = {
          deviceId,
          deviceModel,
          enrollmentType: "closed",
          inviteCodeUsed: validInvite ? inviteCode : "",
          status: validInvite ? "active" : "pending",
          joinedAt: new Date().toISOString(),
        };
        if (existingIdx >= 0) store.testers[existingIdx] = entry;
        else store.testers.push(entry);
        saveStore();
        return sendJson(res, 200, entry);
      }

      if (method === "POST" && pathname === "/api/beta/leave") {
        const body = await readJsonBody(req);
        const deviceId = String(body.deviceId || "").trim();
        store.testers = store.testers.filter((t) => t.deviceId !== deviceId);
        saveStore();
        return sendJson(res, 200, { status: "not_enrolled", enrollmentType: "none" });
      }

      if (method === "GET" && pathname === "/api/releases") {
        const channel = (reqUrl.searchParams.get("channel") || "all").toLowerCase();
        const deviceId = reqUrl.searchParams.get("deviceId") || "";
        const tester = getTesterStatus(deviceId);
        const isClosedApproved =
          !!tester && tester.enrollmentType === "closed" && tester.status === "active";

        const visible = store.releases.filter((r) => {
          if (r.enabled === false) return false;
          if (r.channel === "closed" && !isClosedApproved) return false;
          if (channel !== "all" && r.channel !== channel) return false;
          return true;
        });
        return sendJson(res, 200, { releases: visible });
      }

      const trackMatch = pathname.match(/^\/api\/releases\/([^/]+)\/track-download$/);
      if (method === "POST" && trackMatch) {
        const id = decodeURIComponent(trackMatch[1]);
        const rel = store.releases.find((r) => r.id === id);
        if (!rel) return sendJson(res, 404, { error: "Release not found" });
        rel.downloadCount = (rel.downloadCount || 0) + 1;
        saveStore();
        return sendJson(res, 200, { id: rel.id, downloadCount: rel.downloadCount });
      }

      if (method === "GET" && pathname.startsWith("/apks/")) {
        const fileName = path.basename(pathname.slice("/apks/".length));
        const fullPath = path.join(uploadsDir, fileName);
        if (!fs.existsSync(fullPath)) {
          return sendJson(res, 404, { error: "APK not found" });
        }
        const stat = fs.statSync(fullPath);
        res.writeHead(200, {
          "Content-Type": "application/vnd.android.package-archive",
          "Content-Length": stat.size,
        });
        return fs.createReadStream(fullPath).pipe(res);
      }

      return sendJson(res, 404, { error: "Not found" });
    } catch (err) {
      const code = err.status || 500;
      return sendJson(res, code, {
        error: code === 500 ? "Internal server error" : err.message,
        code: err.code || "SERVER_ERROR",
      });
    }
  });

  server.bugReportService = bugReportService;
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
