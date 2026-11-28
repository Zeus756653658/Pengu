const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const VALID_CATEGORIES = Object.freeze([
  "Downloading",
  "Video Player",
  "Audio Player",
  "Playlist",
  "Subtitles",
  "Network",
  "User Interface",
  "Application Crash",
  "Other",
]);

const VALID_STATUSES = Object.freeze([
  "Open",
  "Reviewing",
  "In Progress",
  "Fixed",
  "Rejected",
  "Closed",
]);

const LIMITS = Object.freeze({
  TITLE_MIN: 5,
  TITLE_MAX: 140,
  DESCRIPTION_MIN: 15,
  DESCRIPTION_MAX: 4000,
  STEPS_MAX: 2500,
  EXPECTED_MAX: 1500,
  ACTUAL_MAX: 1500,
  DEV_RESPONSE_MAX: 2000,
  CRASH_TRACE_MAX: 16000,
  MAX_ATTACHMENTS: 3,
  MAX_ATTACHMENT_BYTES: 3 * 1024 * 1024,
  DUPLICATE_WINDOW_MS: 10 * 60 * 1000,
});

function sanitizeText(raw, maxLen) {
  if (raw === undefined || raw === null) return "";
  const cleaned = String(raw)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "")
    .trim();
  return cleaned.length > maxLen ? cleaned.slice(0, maxLen) : cleaned;
}

function redactSensitiveText(text) {
  if (!text) return "";
  return String(text)
    .replace(/(authorization|cookie|set-cookie|x-tracking-token|password|passwd|secret|token|apikey|api_key)\s*[:=]\s*[^\s,;'"&]+/gi, "$1=[REDACTED]")
    .replace(/([?&](?:token|key|password|auth|sig|cookie|session)=)[^&\s]+/gi, "$1[REDACTED]")
    .replace(/Bearer\s+[A-Za-z0-9._~+/-]+=*/gi, "Bearer [REDACTED]");
}

function detectImageTypeFromBytes(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 12) return null;
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47 &&
    buffer[4] === 0x0d &&
    buffer[5] === 0x0a &&
    buffer[6] === 0x1a &&
    buffer[7] === 0x0a
  ) {
    return { mimeType: "image/png", ext: ".png" };
  }
  // JPEG: FF D8 FF
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return { mimeType: "image/jpeg", ext: ".jpg" };
  }
  // WEBP: "RIFF" .... "WEBP"
  if (
    buffer.subarray(0, 4).toString("ascii") === "RIFF" &&
    buffer.subarray(8, 12).toString("ascii") === "WEBP"
  ) {
    return { mimeType: "image/webp", ext: ".webp" };
  }
  return null;
}

function hashSha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function safeEqualHexOrUtf8(a, b) {
  if (!a || !b) return false;
  const bufA = Buffer.from(String(a), "utf8");
  const bufB = Buffer.from(String(b), "utf8");
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

class BugReportService {
  constructor(options = {}) {
    this.dataDir = options.dataDir || path.join(__dirname, "..", "data");
    this.dbPath = options.dbPath || path.join(this.dataDir, "bug-reports.json");
    this.backupPath = `${this.dbPath}.bak`;
    this.attachmentsDir = options.attachmentsDir || path.join(this.dataDir, "attachments");
    this.auditLogPath = options.auditLogPath || path.join(this.dataDir, "admin-audit.log");
    this.tempCounter = 0;
    this.writeQueue = Promise.resolve();
    this.duplicateWindowMs = options.duplicateWindowMs ?? LIMITS.DUPLICATE_WINDOW_MS;

    fs.mkdirSync(this.dataDir, { recursive: true });
    fs.mkdirSync(this.attachmentsDir, { recursive: true });
    this.ensureDbInitialized();
  }

  ensureDbInitialized() {
    if (!fs.existsSync(this.dbPath)) {
      this._atomicWriteDb({ reports: [] });
    }
  }

  loadDbSync() {
    const tryParse = (filePath) => {
      if (!fs.existsSync(filePath)) return null;
      const raw = fs.readFileSync(filePath, "utf8");
      const parsed = JSON.parse(raw);
      if (!parsed || !Array.isArray(parsed.reports)) {
        throw new Error("Invalid schema: reports array missing");
      }
      return parsed;
    };

    try {
      const data = tryParse(this.dbPath);
      if (data) return data;
    } catch (_) {
      // Quarantine corrupted primary file before recovering from backup
      if (fs.existsSync(this.dbPath)) {
        const corruptCopy = `${this.dbPath}.corrupt.${Date.now()}`;
        try {
          fs.renameSync(this.dbPath, corruptCopy);
        } catch (_) {}
      }
      try {
        const backupData = tryParse(this.backupPath);
        if (backupData) {
          this._atomicWriteDb(backupData);
          return backupData;
        }
      } catch (_) {}
    }

    const empty = { reports: [] };
    this._atomicWriteDb(empty);
    return empty;
  }

  _atomicWriteDb(data) {
    this.tempCounter += 1;
    const tmpPath = `${this.dbPath}.tmp.${process.pid}.${this.tempCounter}`;
    const serialized = JSON.stringify(data, null, 2);

    const fd = fs.openSync(tmpPath, "w", 0o600);
    try {
      fs.writeSync(fd, serialized, 0, "utf8");
      fs.fsyncSync(fd);
    } finally {
      fs.closeSync(fd);
    }

    if (fs.existsSync(this.dbPath)) {
      try {
        fs.copyFileSync(this.dbPath, this.backupPath);
      } catch (_) {}
    }
    fs.renameSync(tmpPath, this.dbPath);
  }

  enqueueWrite(taskFn) {
    const run = this.writeQueue.then(() => taskFn());
    this.writeQueue = run.catch(() => {});
    return run;
  }

  validateReportPayload(body) {
    if (!body || typeof body !== "object") {
      return { error: "Request body must be a JSON object", code: "INVALID_BODY", status: 400 };
    }

    const title = sanitizeText(body.title, LIMITS.TITLE_MAX + 1);
    const category = sanitizeText(body.category, 64);
    const description = sanitizeText(body.description, LIMITS.DESCRIPTION_MAX + 1);
    const stepsToReproduce = sanitizeText(body.stepsToReproduce, LIMITS.STEPS_MAX);
    const expectedBehavior = sanitizeText(body.expectedBehavior, LIMITS.EXPECTED_MAX);
    const actualBehavior = sanitizeText(body.actualBehavior, LIMITS.ACTUAL_MAX);

    if (title.length < LIMITS.TITLE_MIN) {
      return {
        error: `Bug title must be at least ${LIMITS.TITLE_MIN} characters`,
        code: "VALIDATION_ERROR",
        status: 400,
      };
    }
    if (title.length > LIMITS.TITLE_MAX) {
      return {
        error: `Bug title exceeds ${LIMITS.TITLE_MAX} characters`,
        code: "VALIDATION_ERROR",
        status: 400,
      };
    }
    if (!VALID_CATEGORIES.includes(category)) {
      return {
        error: `Invalid category. Allowed: ${VALID_CATEGORIES.join(", ")}`,
        code: "INVALID_CATEGORY",
        status: 400,
      };
    }
    if (description.length < LIMITS.DESCRIPTION_MIN) {
      return {
        error: `Detailed description must be at least ${LIMITS.DESCRIPTION_MIN} characters`,
        code: "VALIDATION_ERROR",
        status: 400,
      };
    }
    if (description.length > LIMITS.DESCRIPTION_MAX) {
      return {
        error: `Detailed description exceeds ${LIMITS.DESCRIPTION_MAX} characters`,
        code: "VALIDATION_ERROR",
        status: 400,
      };
    }

    const diagnostics = this._sanitizeDiagnostics(body.diagnostics);
    const attachmentsInput = Array.isArray(body.attachments) ? body.attachments : [];
    if (attachmentsInput.length > LIMITS.MAX_ATTACHMENTS) {
      return {
        error: `At most ${LIMITS.MAX_ATTACHMENTS} screenshot attachments are allowed`,
        code: "TOO_MANY_ATTACHMENTS",
        status: 400,
      };
    }

    const decodedAttachments = [];
    for (let i = 0; i < attachmentsInput.length; i++) {
      const item = attachmentsInput[i];
      const rawBase64 =
        item && typeof item.base64 === "string"
          ? item.base64.replace(/^data:[^;]+;base64,/i, "").trim()
          : "";
      if (!rawBase64) {
        return { error: "Attachment data is empty", code: "INVALID_ATTACHMENT", status: 400 };
      }
      const estimatedBytes = Math.floor((rawBase64.length * 3) / 4);
      if (estimatedBytes > LIMITS.MAX_ATTACHMENT_BYTES) {
        return {
          error: `Attachment #${i + 1} exceeds 3 MB limit`,
          code: "ATTACHMENT_TOO_LARGE",
          status: 413,
        };
      }
      const buffer = Buffer.from(rawBase64, "base64");
      if (buffer.length === 0 || buffer.length > LIMITS.MAX_ATTACHMENT_BYTES) {
        return {
          error: `Attachment #${i + 1} exceeds 3 MB or could not decode`,
          code: "INVALID_ATTACHMENT",
          status: buffer.length > LIMITS.MAX_ATTACHMENT_BYTES ? 413 : 400,
        };
      }
      const detected = detectImageTypeFromBytes(buffer);
      if (!detected) {
        return {
          error: "Invalid attachment format. Only PNG, JPEG, or WebP images are permitted.",
          code: "INVALID_ATTACHMENT_TYPE",
          status: 400,
        };
      }
      decodedAttachments.push({
        buffer,
        mimeType: detected.mimeType,
        ext: detected.ext,
        sha256: hashSha256(buffer),
      });
    }

    return {
      valid: {
        title: redactSensitiveText(title),
        category,
        description: redactSensitiveText(description),
        stepsToReproduce: redactSensitiveText(stepsToReproduce),
        expectedBehavior: redactSensitiveText(expectedBehavior),
        actualBehavior: redactSensitiveText(actualBehavior),
        diagnostics,
        decodedAttachments,
      },
    };
  }

  _sanitizeDiagnostics(raw) {
    if (!raw || typeof raw !== "object") return null;
    const versionCode = Number(raw.versionCode || 0);
    const apiLevel = Number(raw.apiLevel || 0);
    const abis = Array.isArray(raw.supportedAbis)
      ? raw.supportedAbis.slice(0, 6).map((x) => sanitizeText(x, 24)).filter(Boolean)
      : [];
    return {
      appVersion: sanitizeText(raw.appVersion, 32),
      versionCode: Number.isFinite(versionCode) ? versionCode : 0,
      androidVersion: sanitizeText(raw.androidVersion, 32),
      apiLevel: Number.isFinite(apiLevel) ? apiLevel : 0,
      deviceManufacturer: sanitizeText(raw.deviceManufacturer, 64),
      deviceModel: sanitizeText(raw.deviceModel, 64),
      supportedAbis: abis,
      ytDlpVersion: sanitizeText(raw.ytDlpVersion, 64),
      crashStackTrace: redactSensitiveText(sanitizeText(raw.crashStackTrace, LIMITS.CRASH_TRACE_MAX)),
    };
  }

  async createReport(body) {
    const checked = this.validateReportPayload(body);
    if (checked.error) {
      const err = new Error(checked.error);
      err.status = checked.status;
      err.code = checked.code;
      throw err;
    }

    const v = checked.valid;
    const dedupSource = `${v.category}|${v.title.toLowerCase()}|${v.description.toLowerCase()}|${v.actualBehavior.toLowerCase()}`;
    const dedupHash = hashSha256(dedupSource);

    return this.enqueueWrite(() => {
      const db = this.loadDbSync();
      const nowMs = Date.now();
      const duplicate = db.reports.find(
        (r) =>
          r.dedupHash === dedupHash &&
          nowMs - Date.parse(r.createdAt || "") < this.duplicateWindowMs
      );
      if (duplicate) {
        const err = new Error(
          `Duplicate bug report detected recently (${duplicate.id}).`
        );
        err.status = 409;
        err.code = "DUPLICATE_REPORT";
        err.existingReportId = duplicate.id;
        throw err;
      }

      const dateTag = new Date(nowMs).toISOString().slice(0, 10).replace(/-/g, "");
      const id = `BR-${dateTag}-${crypto.randomBytes(3).toString("hex").toUpperCase()}`;
      const trackingToken = crypto.randomBytes(24).toString("hex");
      const trackingTokenHash = hashSha256(trackingToken);

      const savedAttachments = [];
      for (const att of v.decodedAttachments) {
        const attId = `att_${crypto.randomBytes(8).toString("hex")}`;
        const storedName = `${attId}${att.ext}`;
        const dest = path.resolve(this.attachmentsDir, storedName);
        if (!dest.startsWith(path.resolve(this.attachmentsDir) + path.sep)) {
          throw new Error("Invalid attachment storage path");
        }
        fs.writeFileSync(dest, att.buffer, { mode: 0o600 });
        savedAttachments.push({
          id: attId,
          storedName,
          mimeType: att.mimeType,
          sizeBytes: att.buffer.length,
          sha256: att.sha256,
        });
      }

      const nowIso = new Date(nowMs).toISOString();
      const report = {
        id,
        trackingTokenHash,
        title: v.title,
        category: v.category,
        description: v.description,
        stepsToReproduce: v.stepsToReproduce,
        expectedBehavior: v.expectedBehavior,
        actualBehavior: v.actualBehavior,
        diagnostics: v.diagnostics,
        attachments: savedAttachments,
        status: "Open",
        developerResponse: "",
        dedupHash,
        createdAt: nowIso,
        updatedAt: nowIso,
      };

      db.reports.unshift(report);
      this._atomicWriteDb(db);

      return {
        id: report.id,
        trackingToken,
        status: report.status,
        category: report.category,
        createdAt: report.createdAt,
        attachmentCount: report.attachments.length,
      };
    });
  }

  getPublicStatus(reportId, trackingToken) {
    const cleanId = sanitizeText(reportId, 48);
    if (!cleanId) return { status: 400, error: "Invalid report ID" };

    const db = this.loadDbSync();
    const report = db.reports.find((r) => r.id === cleanId);
    if (!report) return { status: 404, error: "Bug report not found" };

    const token = sanitizeText(trackingToken, 128);
    if (!token || !safeEqualHexOrUtf8(hashSha256(token), report.trackingTokenHash)) {
      return { status: 401, error: "Valid tracking token required to view report status" };
    }

    return {
      status: 200,
      data: {
        id: report.id,
        title: report.title,
        category: report.category,
        status: report.status,
        developerResponse: report.developerResponse || "",
        attachmentCount: (report.attachments || []).length,
        createdAt: report.createdAt,
        updatedAt: report.updatedAt,
      },
    };
  }

  listAdminReports(filters = {}) {
    const db = this.loadDbSync();
    const q = sanitizeText(filters.search, 120).toLowerCase();
    const category = sanitizeText(filters.category, 64);
    const status = sanitizeText(filters.status, 32);
    const version = sanitizeText(filters.version, 32);
    const page = Math.max(1, Number(filters.page || 1) || 1);
    const limit = Math.min(100, Math.max(1, Number(filters.limit || 20) || 20));

    let list = db.reports;
    if (q) {
      list = list.filter(
        (r) =>
          r.id.toLowerCase().includes(q) ||
          r.title.toLowerCase().includes(q) ||
          r.description.toLowerCase().includes(q)
      );
    }
    if (category && category !== "All") {
      list = list.filter((r) => r.category === category);
    }
    if (status && status !== "All") {
      list = list.filter((r) => r.status === status);
    }
    if (version && version !== "All") {
      list = list.filter((r) => (r.diagnostics?.appVersion || "") === version);
    }

    const total = list.length;
    const totalPages = Math.max(1, Math.ceil(total / limit));
    const offset = (page - 1) * limit;
    const paged = list.slice(offset, offset + limit).map((r) => this._serializeAdminSummary(r));
    const availableVersions = [
      ...new Set(db.reports.map((r) => r.diagnostics?.appVersion).filter(Boolean)),
    ];

    return {
      reports: paged,
      pagination: { page, limit, total, totalPages },
      availableVersions,
    };
  }

  getAdminReportById(reportId) {
    const db = this.loadDbSync();
    const report = db.reports.find((r) => r.id === reportId);
    if (!report) return null;
    return this._serializeAdminDetail(report);
  }

  async updateAdminReportStatus(reportId, newStatus, developerResponse, actor = "admin") {
    const cleanStatus = sanitizeText(newStatus, 32);
    if (!VALID_STATUSES.includes(cleanStatus)) {
      const err = new Error(
        `Invalid status. Allowed: ${VALID_STATUSES.join(", ")}`
      );
      err.status = 400;
      err.code = "INVALID_STATUS";
      throw err;
    }
    const cleanReply =
      developerResponse !== undefined
        ? sanitizeText(developerResponse, LIMITS.DEV_RESPONSE_MAX)
        : undefined;

    return this.enqueueWrite(() => {
      const db = this.loadDbSync();
      const report = db.reports.find((r) => r.id === reportId);
      if (!report) {
        const err = new Error("Bug report not found");
        err.status = 404;
        err.code = "NOT_FOUND";
        throw err;
      }
      const prevStatus = report.status;
      report.status = cleanStatus;
      if (cleanReply !== undefined) {
        report.developerResponse = cleanReply;
      }
      report.updatedAt = new Date().toISOString();
      this._atomicWriteDb(db);
      this._writeAuditLog(
        `STATUS_UPDATE report=${report.id} from=${prevStatus} to=${cleanStatus} actor=${actor}`
      );
      return this._serializeAdminDetail(report);
    });
  }

  async deleteAdminReport(reportId, actor = "admin") {
    return this.enqueueWrite(() => {
      const db = this.loadDbSync();
      const idx = db.reports.findIndex((r) => r.id === reportId);
      if (idx === -1) {
        const err = new Error("Bug report not found");
        err.status = 404;
        err.code = "NOT_FOUND";
        throw err;
      }
      const [removed] = db.reports.splice(idx, 1);
      for (const att of removed.attachments || []) {
        const filePath = path.resolve(this.attachmentsDir, path.basename(att.storedName));
        if (filePath.startsWith(path.resolve(this.attachmentsDir) + path.sep) && fs.existsSync(filePath)) {
          try {
            fs.unlinkSync(filePath);
          } catch (_) {}
        }
      }
      this._atomicWriteDb(db);
      this._writeAuditLog(`DELETE report=${removed.id} category=${removed.category} actor=${actor}`);
      return { deleted: true, id: removed.id };
    });
  }

  resolveAttachment(reportId, attachmentId, { isAdmin, trackingToken }) {
    const db = this.loadDbSync();
    const report = db.reports.find((r) => r.id === reportId);
    if (!report) return { status: 404, error: "Bug report not found" };

    if (!isAdmin) {
      const token = sanitizeText(trackingToken, 128);
      if (!token || !safeEqualHexOrUtf8(hashSha256(token), report.trackingTokenHash)) {
        return { status: 401, error: "Unauthorized attachment access" };
      }
    }

    const att = (report.attachments || []).find((a) => a.id === attachmentId);
    if (!att) return { status: 404, error: "Attachment not found" };

    const safeFile = path.resolve(this.attachmentsDir, path.basename(att.storedName));
    if (!safeFile.startsWith(path.resolve(this.attachmentsDir) + path.sep) || !fs.existsSync(safeFile)) {
      return { status: 404, error: "Attachment binary missing" };
    }
    return { status: 200, attachment: att, filePath: safeFile };
  }

  _writeAuditLog(entry) {
    try {
      const line = `${new Date().toISOString()} ${entry}\n`;
      fs.appendFileSync(this.auditLogPath, line, { mode: 0o600 });
    } catch (_) {}
  }

  _serializeAdminSummary(r) {
    return {
      id: r.id,
      title: r.title,
      category: r.category,
      status: r.status,
      appVersion: r.diagnostics?.appVersion || "Unknown",
      attachmentCount: (r.attachments || []).length,
      developerResponse: r.developerResponse || "",
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
    };
  }

  _serializeAdminDetail(r) {
    return {
      id: r.id,
      title: r.title,
      category: r.category,
      description: r.description,
      stepsToReproduce: r.stepsToReproduce,
      expectedBehavior: r.expectedBehavior,
      actualBehavior: r.actualBehavior,
      diagnostics: r.diagnostics,
      attachments: (r.attachments || []).map((a) => ({
        id: a.id,
        mimeType: a.mimeType,
        sizeBytes: a.sizeBytes,
        sha256: a.sha256,
      })),
      status: r.status,
      developerResponse: r.developerResponse || "",
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
    };
  }
}

module.exports = {
  BugReportService,
  VALID_CATEGORIES,
  VALID_STATUSES,
  LIMITS,
  detectImageTypeFromBytes,
  redactSensitiveText,
  sanitizeText,
  safeEqualHexOrUtf8,
  hashSha256,
};
