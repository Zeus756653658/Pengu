import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { createBetaServer } = require("./server.js");

test("Open Beta, Closed Beta capacity/invite access, and Release feeds work end-to-end", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "pengu-beta-test-"));
  const dataDir = path.join(tmpDir, "data");
  const uploadsDir = path.join(tmpDir, "uploads");
  fs.mkdirSync(dataDir, { recursive: true });
  fs.writeFileSync(
    path.join(dataDir, "store.json"),
    JSON.stringify(
      {
        config: {
          openBetaEnabled: true,
          closedBetaEnabled: true,
          closedCapacity: 100,
          inviteCodes: ["PENGU-BETA-VIP"],
          knownBugs: [],
          experimentalFeatures: ["Pengu Early Access"],
        },
        testers: [],
        releases: [
          {
            id: "rel_beta_1",
            version: "1.3.0-beta.1",
            channel: "beta",
            releaseNotes: "## Experimental\n- Open beta build",
            apkUrl: "https://beta.example.com/apks/pengu-beta.apk",
            sha256: "a".repeat(64),
            enabled: true,
            downloadCount: 0,
            publishedAt: new Date().toISOString(),
          },
          {
            id: "rel_closed_1",
            version: "1.3.0-beta.2",
            channel: "closed",
            releaseNotes: "## Closed Ring\n- VIP invite build",
            apkUrl: "https://beta.example.com/apks/pengu-closed.apk",
            sha256: "b".repeat(64),
            enabled: true,
            downloadCount: 0,
            publishedAt: new Date().toISOString(),
          },
        ],
      },
      null,
      2
    )
  );

  const server = createBetaServer({ dataDir, uploadsDir });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    // 1. Open Beta user joins without approval
    const openResp = await fetch(`${baseUrl}/api/beta/enroll`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: "dev_open_1", enrollmentType: "open" }),
    });
    assert.equal(openResp.status, 200);
    assert.equal((await openResp.json()).status, "active");

    // 2. Open Beta user does NOT see closed beta builds
    const openFeeds = await fetch(`${baseUrl}/api/releases?channel=all&deviceId=dev_open_1`);
    const openJson = await openFeeds.json();
    assert.equal(openJson.releases.length, 1);
    assert.equal(openJson.releases[0].channel, "beta");

    // 3. Closed Beta VIP with valid invite code sees closed beta build
    const closedVip = await fetch(`${baseUrl}/api/beta/enroll`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        deviceId: "dev_vip_1",
        enrollmentType: "closed",
        inviteCode: "PENGU-BETA-VIP",
      }),
    });
    assert.equal((await closedVip.json()).status, "active");

    const vipFeeds = await fetch(`${baseUrl}/api/releases?channel=all&deviceId=dev_vip_1`);
    const vipJson = await vipFeeds.json();
    assert.equal(vipJson.releases.length, 2);
  } finally {
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
