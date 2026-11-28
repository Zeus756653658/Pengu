import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { createBetaServer } = require("./server.js");

const ADMIN_TOKEN = "console-admin-secret-token-88a9b7";

function startConsoleTestServer() {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "pengu-console-test-"));
  const dataDir = path.join(tmpDir, "data");
  const uploadsDir = path.join(tmpDir, "uploads");
  const server = createBetaServer({ dataDir, uploadsDir, adminToken: ADMIN_TOKEN });
  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => {
      resolve({
        server,
        tmpDir,
        dataDir,
        uploadsDir,
        baseUrl: `http://127.0.0.1:${server.address().port}`,
        cleanup: async () => {
          await new Promise((done) => server.close(done));
          fs.rmSync(tmpDir, { recursive: true, force: true });
        },
      });
    });
  });
}

test("WebApp Console auth, APK upload, Beta tester approvals, and Release promotion work end-to-end", async () => {
  const ctx = await startConsoleTestServer();
  try {
    // 1. Health endpoint & unauthenticated guards
    const healthRes = await fetch(`${ctx.baseUrl}/api/health`);
    assert.equal(healthRes.status, 200);
    const healthJson = await healthRes.json();
    assert.equal(healthJson.consoleUrl, "/console");

    const unauthApi = await fetch(`${ctx.baseUrl}/api/admin/overview`);
    assert.equal(unauthApi.status, 401);

    const loginPageRes = await fetch(`${ctx.baseUrl}/console`);
    assert.equal(loginPageRes.status, 200);
    const loginHtml = await loginPageRes.text();
    assert.ok(loginHtml.includes("Dev Rayan Sign In"));
    assert.ok(!loginHtml.includes("id=\"navBugsBadge\""));

    // 2. Cookie session login grants access to responsive desktop + mobile WebApp Console
    const loginRes = await fetch(`${ctx.baseUrl}/api/admin/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: ADMIN_TOKEN, redirectTo: "/console" }),
    });
    assert.equal(loginRes.status, 200);
    const setCookie = loginRes.headers.get("set-cookie") || "";
    const cookiePair = setCookie.split(";")[0];
    assert.ok(cookiePair.startsWith("pengu_admin_session="));

    const consoleRes = await fetch(`${ctx.baseUrl}/console`, {
      headers: { Cookie: cookiePair },
    });
    assert.equal(consoleRes.status, 200);
    const consoleHtml = await consoleRes.text();
    assert.ok(consoleHtml.includes("Pengu Developer WebApp Console"));
    assert.ok(consoleHtml.includes('id="bottomNav"'));
    assert.ok(consoleHtml.includes('id="sideNav"'));
    assert.ok(consoleHtml.includes("@media (max-width: 900px)"));

    // 3. Invalid binary rejected on APK upload
    const badApkRes = await fetch(`${ctx.baseUrl}/api/admin/releases/upload-apk`, {
      method: "POST",
      headers: {
        Cookie: cookiePair,
        "Content-Type": "application/vnd.android.package-archive",
        "X-Filename": "fake.apk",
      },
      body: Buffer.from("NOT_A_ZIP_OR_APK_BINARY"),
    });
    assert.equal(badApkRes.status, 400);
    assert.equal((await badApkRes.json()).code, "INVALID_APK_BINARY");

    // 4. Valid ZIP/APK upload computes SHA-256 and serves from /apks/
    const sampleApkBytes = Buffer.concat([
      Buffer.from([0x50, 0x4b, 0x03, 0x04]),
      Buffer.from("pengu-mock-apk-payload-1.3.0-beta.1"),
    ]);
    const expectedSha = crypto.createHash("sha256").update(sampleApkBytes).digest("hex");
    const uploadRes = await fetch(`${ctx.baseUrl}/api/admin/releases/upload-apk`, {
      method: "POST",
      headers: {
        Cookie: cookiePair,
        "Content-Type": "application/vnd.android.package-archive",
        "X-Filename": "Pengu-1.3.0-beta.1.apk",
      },
      body: sampleApkBytes,
    });
    assert.equal(uploadRes.status, 201);
    const uploaded = await uploadRes.json();
    assert.equal(uploaded.sha256, expectedSha);
    assert.ok(uploaded.apkPath.startsWith("/apks/"));

    const apkDownload = await fetch(`${ctx.baseUrl}${uploaded.apkPath}`);
    assert.equal(apkDownload.status, 200);
    const downloadedBuf = Buffer.from(await apkDownload.arrayBuffer());
    assert.deepEqual(downloadedBuf, sampleApkBytes);

    // 5. Publish closed beta release, approve pending closed tester, disable & promote to stable
    const pubRes = await fetch(`${ctx.baseUrl}/api/admin/releases`, {
      method: "POST",
      headers: { Cookie: cookiePair, "Content-Type": "application/json" },
      body: JSON.stringify({
        version: "1.3.0-beta.1",
        channel: "closed",
        apkUrl: uploaded.apkUrl,
        sha256: uploaded.sha256,
        releaseNotes: "## Beta\n- Ringtone Maker",
        enabled: true,
      }),
    });
    assert.equal(pubRes.status, 201);
    const rel = await pubRes.json();

    // Closed tester requests without invite -> pending -> hidden release until approved
    const reqClosed = await fetch(`${ctx.baseUrl}/api/beta/enroll`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        deviceId: "dev_closed_req_1",
        deviceModel: "Pixel 9",
        enrollmentType: "closed",
      }),
    });
    assert.equal((await reqClosed.json()).status, "pending");

    const beforeApprove = await fetch(
      `${ctx.baseUrl}/api/releases?channel=all&deviceId=dev_closed_req_1`
    );
    assert.equal((await beforeApprove.json()).releases.length, 0);

    const approveRes = await fetch(`${ctx.baseUrl}/api/admin/beta/testers/dev_closed_req_1`, {
      method: "PATCH",
      headers: { Cookie: cookiePair, "Content-Type": "application/json" },
      body: JSON.stringify({ status: "active" }),
    });
    assert.equal(approveRes.status, 200);
    assert.equal((await approveRes.json()).status, "active");

    const afterApprove = await fetch(
      `${ctx.baseUrl}/api/releases?channel=all&deviceId=dev_closed_req_1`
    );
    assert.equal((await afterApprove.json()).releases.length, 1);

    // Toggle disable -> hidden; promote to stable -> visible as stable 1.3.0
    const disableRes = await fetch(`${ctx.baseUrl}/api/admin/releases/${rel.id}`, {
      method: "PATCH",
      headers: { Cookie: cookiePair, "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: false }),
    });
    assert.equal((await disableRes.json()).enabled, false);

    const promoteRes = await fetch(`${ctx.baseUrl}/api/admin/releases/${rel.id}/promote`, {
      method: "POST",
      headers: { Cookie: cookiePair, "Content-Type": "application/json" },
      body: "{}",
    });
    assert.equal(promoteRes.status, 200);
    const promoted = await promoteRes.json();
    assert.equal(promoted.channel, "stable");
    assert.equal(promoted.version, "1.3.0");
    assert.equal(promoted.enabled, true);

    // Overview reflects metrics & delete cleans up local APK
    const ovRes = await fetch(`${ctx.baseUrl}/api/admin/overview`, {
      headers: { Cookie: cookiePair },
    });
    assert.equal(ovRes.status, 200);
    const ov = await ovRes.json();
    assert.equal(ov.beta.closedActive, 1);
    assert.equal(ov.releases.stable, 1);

    const delRelRes = await fetch(`${ctx.baseUrl}/api/admin/releases/${rel.id}`, {
      method: "DELETE",
      headers: { Cookie: cookiePair },
    });
    assert.equal(delRelRes.status, 200);
    assert.equal(fs.existsSync(path.join(ctx.uploadsDir, uploaded.fileName)), false);

    const remTester = await fetch(`${ctx.baseUrl}/api/admin/beta/testers/dev_closed_req_1`, {
      method: "DELETE",
      headers: { Cookie: cookiePair },
    });
    assert.equal(remTester.status, 200);
  } finally {
    await ctx.cleanup();
  }
});
