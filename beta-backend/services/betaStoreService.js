const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { sanitizeText } = require("./bugReportService");

const VALID_CHANNELS = Object.freeze(["stable", "beta", "closed", "archived"]);
const VALID_TESTER_STATUSES = Object.freeze(["active", "pending", "removed"]);
const MAX_APK_UPLOAD_BYTES = 120 * 1024 * 1024;

function normalizeStringList(rawList, maxItems = 25, maxLen = 160) {
  let items = [];
  if (Array.isArray(rawList)) {
    items = rawList;
  } else if (typeof rawList === "string") {
    items = rawList.split(/[\r\n,]+/);
  }
  const seen = new Set();
  const out = [];
  for (const raw of items) {
    const cleaned = sanitizeText(raw, maxLen);
    if (cleaned && !seen.has(cleaned)) {
      seen.add(cleaned);
      out.push(cleaned);
      if (out.length >= maxItems) break;
    }
  }
  return out;
}

function isValidHttpOrApksUrl(rawUrl) {
  if (!rawUrl) return false;
  if (rawUrl.startsWith("/apks/")) return true;
  try {
    const parsed = new URL(rawUrl);
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch (_) {
    return false;
  }
}

class BetaStoreService {
  constructor({ dataDir, uploadsDir, auditLogPath }) {
    this.dataDir = dataDir;
    this.uploadsDir = uploadsDir;
    this.storePath = path.join(dataDir, "store.json");
    this.backupPath = `${this.storePath}.bak`;
    this.auditLogPath = auditLogPath || path.join(dataDir, "admin-audit.log");
    this.tempCounter = 0;

    fs.mkdirSync(this.dataDir, { recursive: true });
    fs.mkdirSync(this.uploadsDir, { recursive: true });
    this.store = this._loadStoreSync();
    this._saveStoreSync();
  }

  _defaultStore() {
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

  _loadStoreSync() {
    const tryParse = (filePath) => {
      if (!fs.existsSync(filePath)) return null;
      const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
      if (!parsed || typeof parsed !== "object" || !parsed.config) return null;
      return {
        config: {
          openBetaEnabled: parsed.config.openBetaEnabled !== false,
          closedBetaEnabled: parsed.config.closedBetaEnabled !== false,
          closedCapacity: Math.max(1, Number(parsed.config.closedCapacity || 100) || 100),
          inviteCodes: Array.isArray(parsed.config.inviteCodes)
            ? parsed.config.inviteCodes
            : ["PENGU-BETA-VIP"],
          knownBugs: Array.isArray(parsed.config.knownBugs) ? parsed.config.knownBugs : [],
          experimentalFeatures: Array.isArray(parsed.config.experimentalFeatures)
            ? parsed.config.experimentalFeatures
            : [],
        },
        testers: Array.isArray(parsed.testers) ? parsed.testers : [],
        releases: Array.isArray(parsed.releases) ? parsed.releases : [],
      };
    };

    try {
      const primary = tryParse(this.storePath);
      if (primary) return primary;
    } catch (_) {
      try {
        const backup = tryParse(this.backupPath);
        if (backup) return backup;
      } catch (_) {}
    }
    return this._defaultStore();
  }

  _saveStoreSync() {
    this.tempCounter += 1;
    const tmpPath = `${this.storePath}.tmp.${process.pid}.${this.tempCounter}`;
    const payload = JSON.stringify(this.store, null, 2);
    const fd = fs.openSync(tmpPath, "w", 0o600);
    try {
      fs.writeSync(fd, payload, 0, "utf8");
      fs.fsyncSync(fd);
    } finally {
      fs.closeSync(fd);
    }
    if (fs.existsSync(this.storePath)) {
      try {
        fs.copyFileSync(this.storePath, this.backupPath);
      } catch (_) {}
    }
    fs.renameSync(tmpPath, this.storePath);
  }

  _audit(entry) {
    try {
      fs.appendFileSync(this.auditLogPath, `${new Date().toISOString()} ${entry}\n`, {
        mode: 0o600,
      });
    } catch (_) {}
  }

  closedActiveCount() {
    return this.store.testers.filter(
      (t) => t.enrollmentType === "closed" && t.status === "active"
    ).length;
  }

  getTesterStatus(deviceId) {
    if (!deviceId) return null;
    return this.store.testers.find((t) => t.deviceId === deviceId) || null;
  }

  getPublicConfig() {
    const cfg = this.store.config;
    return {
      openBetaEnabled: cfg.openBetaEnabled,
      closedBetaEnabled: cfg.closedBetaEnabled,
      closedCapacity: cfg.closedCapacity,
      closedActiveCount: this.closedActiveCount(),
      knownBugs: cfg.knownBugs,
      experimentalFeatures: cfg.experimentalFeatures,
    };
  }

  getPublicTesterStatus(deviceId) {
    const tester = this.getTesterStatus(deviceId);
    return {
      enrolled: !!tester && tester.status === "active",
      enrollmentType: tester ? tester.enrollmentType : "none",
      status: tester ? tester.status : "not_enrolled",
      joinedAt: tester ? tester.joinedAt : null,
      closedActiveCount: this.closedActiveCount(),
      closedCapacity: this.store.config.closedCapacity,
    };
  }

  enrollTester(body) {
    const deviceId = sanitizeText(body?.deviceId, 96);
    const enrollmentType = body?.enrollmentType === "closed" ? "closed" : "open";
    const inviteCode = sanitizeText(body?.inviteCode, 64);
    const deviceModel = sanitizeText(body?.deviceModel || "Android Device", 80) || "Android Device";
    if (!deviceId) {
      return { status: 400, data: { error: "deviceId is required" } };
    }

    const existingIdx = this.store.testers.findIndex((t) => t.deviceId === deviceId);
    if (enrollmentType === "open") {
      if (!this.store.config.openBetaEnabled) {
        return { status: 403, data: { error: "Open Beta is currently paused" } };
      }
      const entry = {
        deviceId,
        deviceModel,
        enrollmentType: "open",
        status: "active",
        joinedAt: new Date().toISOString(),
      };
      if (existingIdx >= 0) this.store.testers[existingIdx] = entry;
      else this.store.testers.push(entry);
      this._saveStoreSync();
      return { status: 200, data: entry };
    }

    if (!this.store.config.closedBetaEnabled) {
      return { status: 403, data: { error: "Closed Beta is not accepting requests" } };
    }
    const alreadyActive =
      existingIdx >= 0 &&
      this.store.testers[existingIdx].enrollmentType === "closed" &&
      this.store.testers[existingIdx].status === "active";
    if (!alreadyActive && this.closedActiveCount() >= this.store.config.closedCapacity) {
      return { status: 403, data: { error: "Closed Beta tester capacity reached" } };
    }
    const validInvite =
      inviteCode.length > 0 && this.store.config.inviteCodes.includes(inviteCode);
    const entry = {
      deviceId,
      deviceModel,
      enrollmentType: "closed",
      inviteCodeUsed: validInvite ? inviteCode : "",
      status: validInvite ? "active" : "pending",
      joinedAt: new Date().toISOString(),
    };
    if (existingIdx >= 0) this.store.testers[existingIdx] = entry;
    else this.store.testers.push(entry);
    this._saveStoreSync();
    return { status: 200, data: entry };
  }

  leaveTester(body) {
    const deviceId = sanitizeText(body?.deviceId, 96);
    this.store.testers = this.store.testers.filter((t) => t.deviceId !== deviceId);
    this._saveStoreSync();
    return { status: "not_enrolled", enrollmentType: "none" };
  }

  getPublicReleases({ channel = "all", deviceId = "" }) {
    const normalizedChannel = String(channel || "all").toLowerCase();
    const tester = this.getTesterStatus(deviceId);
    const isClosedApproved =
      !!tester && tester.enrollmentType === "closed" && tester.status === "active";

    return this.store.releases.filter((r) => {
      if (r.enabled === false) return false;
      if (r.channel === "closed" && !isClosedApproved) return false;
      if (normalizedChannel !== "all" && r.channel !== normalizedChannel) return false;
      return true;
    });
  }

  trackReleaseDownload(id) {
    const rel = this.store.releases.find((r) => r.id === id);
    if (!rel) return null;
    rel.downloadCount = (rel.downloadCount || 0) + 1;
    this._saveStoreSync();
    return { id: rel.id, downloadCount: rel.downloadCount };
  }

  // ---- Admin Config & Overview ----

  getOverview(bugReportService) {
    const bugList = bugReportService.loadDbSync().reports || [];
    const testers = this.store.testers;
    const releases = this.store.releases;

    const statusCounts = {
      Open: 0,
      Reviewing: 0,
      "In Progress": 0,
      Fixed: 0,
      Rejected: 0,
      Closed: 0,
    };
    for (const r of bugList) {
      if (statusCounts[r.status] !== undefined) statusCounts[r.status] += 1;
    }

    const totalDownloads = releases.reduce((acc, r) => acc + Number(r.downloadCount || 0), 0);
    return {
      bugs: {
        total: bugList.length,
        open: statusCounts.Open,
        inReview: statusCounts.Reviewing + statusCounts["In Progress"],
        fixed: statusCounts.Fixed,
        statusCounts,
      },
      beta: {
        totalTesters: testers.length,
        openActive: testers.filter((t) => t.enrollmentType === "open" && t.status === "active").length,
        closedActive: this.closedActiveCount(),
        closedPending: testers.filter((t) => t.enrollmentType === "closed" && t.status === "pending").length,
        closedCapacity: this.store.config.closedCapacity,
      },
      releases: {
        total: releases.length,
        stable: releases.filter((r) => r.channel === "stable" && r.enabled !== false).length,
        beta: releases.filter((r) => r.channel === "beta" && r.enabled !== false).length,
        closed: releases.filter((r) => r.channel === "closed" && r.enabled !== false).length,
        archived: releases.filter((r) => r.channel === "archived").length,
        totalDownloads,
      },
      config: this.store.config,
      server: {
        version: "1.2.0",
        nodeVersion: process.version,
        dataDir: this.dataDir,
      },
    };
  }

  updateAdminConfig(patch, actor = "admin") {
    if (!patch || typeof patch !== "object") {
      const err = new Error("Config body must be a JSON object");
      err.status = 400;
      throw err;
    }
    const cfg = { ...this.store.config };
    if (patch.openBetaEnabled !== undefined) {
      cfg.openBetaEnabled = Boolean(patch.openBetaEnabled);
    }
    if (patch.closedBetaEnabled !== undefined) {
      cfg.closedBetaEnabled = Boolean(patch.closedBetaEnabled);
    }
    if (patch.closedCapacity !== undefined) {
      const cap = Math.floor(Number(patch.closedCapacity));
      if (!Number.isFinite(cap) || cap < 1 || cap > 100000) {
        const err = new Error("Closed Beta capacity must be between 1 and 100000");
        err.status = 400;
        throw err;
      }
      cfg.closedCapacity = cap;
    }
    if (patch.inviteCodes !== undefined) {
      cfg.inviteCodes = normalizeStringList(patch.inviteCodes, 50, 64);
    }
    if (patch.knownBugs !== undefined) {
      cfg.knownBugs = normalizeStringList(patch.knownBugs, 30, 200);
    }
    if (patch.experimentalFeatures !== undefined) {
      cfg.experimentalFeatures = normalizeStringList(patch.experimentalFeatures, 30, 200);
    }
    this.store.config = cfg;
    this._saveStoreSync();
    this._audit(`BETA_CONFIG_UPDATE actor=${actor}`);
    return {
      ...cfg,
      closedActiveCount: this.closedActiveCount(),
    };
  }

  // ---- Admin Tester Management ----

  listAdminTesters({ status = "all", enrollmentType = "all", search = "" } = {}) {
    const st = String(status || "all").toLowerCase();
    const et = String(enrollmentType || "all").toLowerCase();
    const q = sanitizeText(search, 80).toLowerCase();

    const filtered = this.store.testers.filter((t) => {
      if (st !== "all" && String(t.status || "").toLowerCase() !== st) return false;
      if (et !== "all" && String(t.enrollmentType || "").toLowerCase() !== et) return false;
      if (
        q &&
        !String(t.deviceId || "").toLowerCase().includes(q) &&
        !String(t.deviceModel || "").toLowerCase().includes(q)
      ) {
        return false;
      }
      return true;
    });

    return {
      testers: filtered,
      closedActiveCount: this.closedActiveCount(),
      closedCapacity: this.store.config.closedCapacity,
    };
  }

  updateAdminTesterStatus(deviceId, newStatus, actor = "admin") {
    const cleanId = sanitizeText(deviceId, 96);
    const cleanStatus = sanitizeText(newStatus, 24).toLowerCase();
    if (!VALID_TESTER_STATUSES.includes(cleanStatus) || cleanStatus === "removed") {
      const err = new Error("Status must be 'active' or 'pending'");
      err.status = 400;
      throw err;
    }
    const tester = this.store.testers.find((t) => t.deviceId === cleanId);
    if (!tester) {
      const err = new Error("Tester not found");
      err.status = 404;
      throw err;
    }
    if (
      cleanStatus === "active" &&
      tester.enrollmentType === "closed" &&
      tester.status !== "active" &&
      this.closedActiveCount() >= this.store.config.closedCapacity
    ) {
      const err = new Error("Closed Beta seat capacity reached. Increase capacity before approving.");
      err.status = 409;
      throw err;
    }
    tester.status = cleanStatus;
    tester.updatedAt = new Date().toISOString();
    this._saveStoreSync();
    this._audit(`TESTER_STATUS deviceId=${cleanId} status=${cleanStatus} actor=${actor}`);
    return tester;
  }

  removeAdminTester(deviceId, actor = "admin") {
    const cleanId = sanitizeText(deviceId, 96);
    const prevLen = this.store.testers.length;
    this.store.testers = this.store.testers.filter((t) => t.deviceId !== cleanId);
    if (this.store.testers.length === prevLen) {
      const err = new Error("Tester not found");
      err.status = 404;
      throw err;
    }
    this._saveStoreSync();
    this._audit(`TESTER_REMOVE deviceId=${cleanId} actor=${actor}`);
    return { removed: true, deviceId: cleanId };
  }

  // ---- Admin Release & APK Management ----

  listAdminReleases({ channel = "all", search = "" } = {}) {
    const ch = String(channel || "all").toLowerCase();
    const q = sanitizeText(search, 80).toLowerCase();
    const items = this.store.releases.filter((r) => {
      if (ch !== "all" && String(r.channel || "").toLowerCase() !== ch) return false;
      if (
        q &&
        !String(r.version || "").toLowerCase().includes(q) &&
        !String(r.releaseNotes || "").toLowerCase().includes(q)
      ) {
        return false;
      }
      return true;
    });
    return { releases: items };
  }

  saveUploadedApk({ fileName, buffer, publicBaseUrl = "", actor = "admin" }) {
    if (!Buffer.isBuffer(buffer) || buffer.length < 4) {
      const err = new Error("Uploaded APK file is empty");
      err.status = 400;
      throw err;
    }
    if (buffer.length > MAX_APK_UPLOAD_BYTES) {
      const err = new Error("Uploaded APK exceeds 120 MB direct upload cap");
      err.status = 413;
      throw err;
    }
    // Verify ZIP/APK local file header magic (PK\x03\x04)
    if (
      buffer[0] !== 0x50 ||
      buffer[1] !== 0x4b ||
      buffer[2] !== 0x03 ||
      buffer[3] !== 0x04
    ) {
      const err = new Error("Invalid APK binary (expected Android ZIP/APK header)");
      err.status = 400;
      err.code = "INVALID_APK_BINARY";
      throw err;
    }

    const base = path
      .basename(sanitizeText(fileName || "pengu-release.apk", 96))
      .replace(/[^A-Za-z0-9._-]/g, "_");
    const safeName = base.toLowerCase().endsWith(".apk") ? base : `${base}.apk`;
    const uniqueName = `${Date.now()}-${crypto.randomBytes(3).toString("hex")}-${safeName}`;
    const dest = path.resolve(this.uploadsDir, uniqueName);
    if (!dest.startsWith(path.resolve(this.uploadsDir) + path.sep)) {
      const err = new Error("Invalid APK target path");
      err.status = 400;
      throw err;
    }

    fs.writeFileSync(dest, buffer, { mode: 0o644 });
    const sha256 = crypto.createHash("sha256").update(buffer).digest("hex");
    const apkPath = `/apks/${encodeURIComponent(uniqueName)}`;
    const apkUrl = publicBaseUrl ? `${publicBaseUrl}${apkPath}` : apkPath;
    this._audit(`APK_UPLOAD file=${uniqueName} size=${buffer.length} actor=${actor}`);
    return {
      fileName: uniqueName,
      apkPath,
      apkUrl,
      sha256,
      sizeBytes: buffer.length,
    };
  }

  publishRelease(body, actor = "admin") {
    const version = sanitizeText(body?.version, 48).replace(/^v/i, "");
    const channel = sanitizeText(body?.channel || "beta", 24).toLowerCase();
    const releaseNotes = sanitizeText(body?.releaseNotes, 8000);
    const apkUrl = sanitizeText(body?.apkUrl, 600);
    const sha256 = sanitizeText(body?.sha256, 64).toLowerCase();
    const knownBugs = normalizeStringList(body?.knownBugs, 20, 200);
    const experimentalFeatures = normalizeStringList(body?.experimentalFeatures, 20, 200);
    const enabled = body?.enabled !== false;

    if (!version || version.length < 3) {
      const err = new Error("Version string is required (e.g. 1.3.0-beta.1)");
      err.status = 400;
      throw err;
    }
    if (!VALID_CHANNELS.includes(channel)) {
      const err = new Error(`Invalid channel. Allowed: ${VALID_CHANNELS.join(", ")}`);
      err.status = 400;
      throw err;
    }
    if (!releaseNotes) {
      const err = new Error("Release notes are required");
      err.status = 400;
      throw err;
    }
    if (!isValidHttpOrApksUrl(apkUrl)) {
      const err = new Error("Valid HTTPS APK download URL (or uploaded /apks/ path) is required");
      err.status = 400;
      throw err;
    }
    if (sha256 && !/^[0-9a-f]{64}$/.test(sha256)) {
      const err = new Error("SHA-256 checksum must be 64 hexadecimal characters");
      err.status = 400;
      throw err;
    }

    const release = {
      id: `rel_${Date.now()}_${crypto.randomBytes(3).toString("hex")}`,
      version,
      channel,
      releaseNotes,
      knownBugs,
      experimentalFeatures,
      apkUrl,
      sha256,
      enabled,
      downloadCount: 0,
      publishedAt: new Date().toISOString(),
    };
    this.store.releases.unshift(release);
    this._saveStoreSync();
    this._audit(`RELEASE_PUBLISH id=${release.id} version=${version} channel=${channel} actor=${actor}`);
    return release;
  }

  updateRelease(id, patch, actor = "admin") {
    const rel = this.store.releases.find((r) => r.id === id);
    if (!rel) {
      const err = new Error("Release not found");
      err.status = 404;
      throw err;
    }
    if (patch.enabled !== undefined) rel.enabled = Boolean(patch.enabled);
    if (patch.channel !== undefined) {
      const ch = sanitizeText(patch.channel, 24).toLowerCase();
      if (!VALID_CHANNELS.includes(ch)) {
        const err = new Error(`Invalid channel. Allowed: ${VALID_CHANNELS.join(", ")}`);
        err.status = 400;
        throw err;
      }
      rel.channel = ch;
    }
    if (patch.releaseNotes !== undefined) {
      rel.releaseNotes = sanitizeText(patch.releaseNotes, 8000);
    }
    if (patch.knownBugs !== undefined) {
      rel.knownBugs = normalizeStringList(patch.knownBugs, 20, 200);
    }
    if (patch.experimentalFeatures !== undefined) {
      rel.experimentalFeatures = normalizeStringList(patch.experimentalFeatures, 20, 200);
    }
    rel.updatedAt = new Date().toISOString();
    this._saveStoreSync();
    this._audit(`RELEASE_UPDATE id=${rel.id} channel=${rel.channel} enabled=${rel.enabled} actor=${actor}`);
    return rel;
  }

  promoteRelease(id, body = {}, actor = "admin") {
    const rel = this.store.releases.find((r) => r.id === id);
    if (!rel) {
      const err = new Error("Release not found");
      err.status = 404;
      throw err;
    }
    const requestedVersion = sanitizeText(body?.version, 48).replace(/^v/i, "");
    const stableVersion =
      requestedVersion || rel.version.replace(/-(?:alpha|beta|closed|rc).*$/i, "") || rel.version;
    rel.promotedFromVersion = rel.version;
    rel.promotedFromChannel = rel.channel;
    rel.version = stableVersion;
    rel.channel = "stable";
    rel.enabled = true;
    rel.promotedAt = new Date().toISOString();
    this._saveStoreSync();
    this._audit(`RELEASE_PROMOTE id=${rel.id} version=${rel.version} actor=${actor}`);
    return rel;
  }

  deleteRelease(id, actor = "admin") {
    const idx = this.store.releases.findIndex((r) => r.id === id);
    if (idx === -1) {
      const err = new Error("Release not found");
      err.status = 404;
      throw err;
    }
    const [removed] = this.store.releases.splice(idx, 1);
    const localFile = this._extractLocalApkName(removed.apkUrl);
    if (
      localFile &&
      !this.store.releases.some((other) => this._extractLocalApkName(other.apkUrl) === localFile)
    ) {
      const target = path.resolve(this.uploadsDir, path.basename(localFile));
      if (target.startsWith(path.resolve(this.uploadsDir) + path.sep) && fs.existsSync(target)) {
        try {
          fs.unlinkSync(target);
        } catch (_) {}
      }
    }
    this._saveStoreSync();
    this._audit(`RELEASE_DELETE id=${removed.id} version=${removed.version} actor=${actor}`);
    return { deleted: true, id: removed.id };
  }

  _extractLocalApkName(apkUrl) {
    if (!apkUrl) return null;
    try {
      const pathname = apkUrl.startsWith("/") ? apkUrl : new URL(apkUrl).pathname;
      if (!pathname.startsWith("/apks/")) return null;
      return decodeURIComponent(pathname.slice("/apks/".length));
    } catch (_) {
      return null;
    }
  }
}

module.exports = {
  BetaStoreService,
  VALID_CHANNELS,
  MAX_APK_UPLOAD_BYTES,
};
