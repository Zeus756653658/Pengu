import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { createBetaServer } = require("./server.js");
const { BugReportService } = require("./services/bugReportService.js");

const PNG_1X1_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
const ADMIN_TOKEN = "test-super-secret-admin-token-9f8e";

function startTestServer() {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "pengu-bug-test-"));
  const dataDir = path.join(tmpDir, "data");
  const uploadsDir = path.join(tmpDir, "uploads");
  const server = createBetaServer({ dataDir, uploadsDir, adminToken: ADMIN_TOKEN });
  return server.listen(0, "127.0.0.1") && new Promise((resolve) => {
    server.once("listening", () => {
      resolve({
        server,
        tmpDir,
        dataDir,
        baseUrl: `http://127.0.0.1:${server.address().port}`,
        cleanup: async () => {
          await new Promise((done) => server.close(done));
          fs.rmSync(tmpDir, { recursive: true, force: true });
        },
      });
    });
  });
}

test("Bug Report lifecycle: validation, attachments, dedup, tracking token, admin status & delete", async () => {
  const ctx = await startTestServer();
  try {
    // 1. Missing required fields -> 400
    const missingRes = await fetch(`${ctx.baseUrl}/api/bug-reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: "Hi", category: "Downloading", description: "short" }),
    });
    assert.equal(missingRes.status, 400);

    // 2. Invalid category -> 400
    const badCatRes = await fetch(`${ctx.baseUrl}/api/bug-reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: "Player freeze on seek",
        category: "UnknownMagic",
        description: "Seeking forward freezes video frame for 10 seconds.",
      }),
    });
    assert.equal(badCatRes.status, 400);

    // 3. Invalid attachment magic bytes disguised as PNG -> 400
    const badAttRes = await fetch(`${ctx.baseUrl}/api/bug-reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: "Spoofed image file",
        category: "Other",
        description: "Uploading disguised HTML script inside base64 image field.",
        attachments: [
          {
            mimeType: "image/png",
            base64: Buffer.from("<script>alert(1)</script>not-an-image").toString("base64"),
          },
        ],
      }),
    });
    assert.equal(badAttRes.status, 400);
    assert.equal((await badAttRes.json()).code, "INVALID_ATTACHMENT_TYPE");

    // 4. Oversized payload -> 413
    const hugeBase64 = "A".repeat(13 * 1024 * 1024);
    const bigRes = await fetch(`${ctx.baseUrl}/api/bug-reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: "Big", description: hugeBase64 }),
    });
    assert.equal(bigRes.status, 413);

    // 5. Valid report submission with valid PNG + diagnostics redaction
    const validPayload = {
      title: "Video fails above 1080p",
      category: "Downloading",
      description: "Selecting 4K stream returns HTTP 403 authorization: Bearer supersecret123",
      stepsToReproduce: "1. Paste link\n2. Select 2160p",
      expectedBehavior: "Starts download",
      actualBehavior: "Shows HTTP 403 error",
      diagnostics: { appVersion: "1.2.0", versionCode: 102000400, apiLevel: 35 },
      attachments: [{ mimeType: "image/png", base64: PNG_1X1_BASE64 }],
    };
    const createRes = await fetch(`${ctx.baseUrl}/api/bug-reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(validPayload),
    });
    assert.equal(createRes.status, 201);
    const created = await createRes.json();
    assert.ok(created.id.startsWith("BR-"));
    assert.ok(created.trackingToken.length >= 32);
    assert.equal(created.status, "Open");
    assert.equal(created.attachmentCount, 1);

    // 6. Duplicate submission rejected -> 409
    const dupRes = await fetch(`${ctx.baseUrl}/api/bug-reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(validPayload),
    });
    assert.equal(dupRes.status, 409);

    // 7. Unknown report ID -> 404
    const missingIdRes = await fetch(`${ctx.baseUrl}/api/bug-reports/BR-NOT-FOUND/status`, {
      headers: { "X-Tracking-Token": created.trackingToken },
    });
    assert.equal(missingIdRes.status, 404);

    // 8. Unauthorized status check (wrong token -> 401), authorized -> 200
    const badTokenRes = await fetch(`${ctx.baseUrl}/api/bug-reports/${created.id}/status`, {
      headers: { "X-Tracking-Token": "wrong-token" },
    });
    assert.equal(badTokenRes.status, 401);

    const statusRes = await fetch(`${ctx.baseUrl}/api/bug-reports/${created.id}/status`, {
      headers: { "X-Tracking-Token": created.trackingToken },
    });
    assert.equal(statusRes.status, 200);
    assert.equal((await statusRes.json()).status, "Open");

    // 9. Unauthorized admin requests -> 401
    const unauthAdmin = await fetch(`${ctx.baseUrl}/api/admin/bug-reports`);
    assert.equal(unauthAdmin.status, 401);

    const unauthPatch = await fetch(`${ctx.baseUrl}/api/admin/bug-reports/${created.id}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "Fixed" }),
    });
    assert.equal(unauthPatch.status, 401);

    // 10. Authorized admin status change + redaction check + public sync
    const adminDetail = await fetch(`${ctx.baseUrl}/api/admin/bug-reports/${created.id}`, {
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    const detailJson = await adminDetail.json();
    assert.ok(!detailJson.description.includes("supersecret123"));

    const patchRes = await fetch(`${ctx.baseUrl}/api/admin/bug-reports/${created.id}/status`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${ADMIN_TOKEN}`,
      },
      body: JSON.stringify({
        status: "Fixed",
        developerResponse: "Updated extractor flags in v1.2.1",
      }),
    });
    assert.equal(patchRes.status, 200);

    const syncedStatus = await fetch(`${ctx.baseUrl}/api/bug-reports/${created.id}/status`, {
      headers: { "X-Tracking-Token": created.trackingToken },
    });
    const syncedJson = await syncedStatus.json();
    assert.equal(syncedJson.status, "Fixed");
    assert.equal(syncedJson.developerResponse, "Updated extractor flags in v1.2.1");

    // 11. Admin deletion deletes record + attachment binary
    const delRes = await fetch(`${ctx.baseUrl}/api/admin/bug-reports/${created.id}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    assert.equal(delRes.status, 200);
    const afterDel = await fetch(`${ctx.baseUrl}/api/admin/bug-reports/${created.id}`, {
      headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
    });
    assert.equal(afterDel.status, 404);
  } finally {
    await ctx.cleanup();
  }
});

test("Storage corruption recovery restores valid reports from .bak backup", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "pengu-bug-recover-"));
  try {
    const svc = new BugReportService({ dataDir: tmpDir });
    const first = await svc.createReport({
      title: "Subtitle overlap in landscape",
      category: "Subtitles",
      description: "Bottom caption overlaps player seekbar when rotating horizontally.",
    });
    // Second write guarantees .bak holds valid previous state
    await svc.createReport({
      title: "Second bug to rotate backup",
      category: "User Interface",
      description: "Checks backup creation cleanly before simulated primary corruption.",
    });

    // Corrupt primary JSON file directly
    fs.writeFileSync(path.join(tmpDir, "bug-reports.json"), "{CORRUPT_JSON!!!");

    const listResult = svc.listAdminReports({});
    assert.ok(listResult.reports.some((r) => r.id === first.id));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
