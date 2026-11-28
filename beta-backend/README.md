# Pengu Beta & Bug Report Server

Self-hosted HTTPS backend in `beta-backend/` powering:
1. **Pengu Beta Program**: Open Beta & invite/capacity-gated Closed Beta enrollment (`/api/beta/*`, `/api/releases*`, `/apks/*`)
2. **Pengu Bug Reporting & Developer Dashboard**: Public bug submission + tracking-token status queries (`/api/bug-reports*`) and server-authenticated Developer Bug Management UI + API (`/admin/bug-reports`, `/api/admin/bug-reports*`)

---

## Quick Start (Node 18+, zero npm dependencies)

```bash
cd beta-backend
export PENGU_ADMIN_TOKEN="replace-with-32-plus-char-random-secret"
export PENGU_SESSION_SECRET="replace-with-another-random-secret"
npm start
```

> **Security Note:** Never commit `PENGU_ADMIN_TOKEN` or `PENGU_SESSION_SECRET` to version control or embed them inside Android APK builds. If `PENGU_ADMIN_TOKEN` is unset, all `/api/admin/*` routes and `/admin/bug-reports` fail closed (`503`).

---

## Environment Variables

| Variable | Required | Default | Description |
| :--- | :--- | :--- | :--- |
| `PORT` | No | `8787` | TCP listen port |
| `PENGU_ADMIN_TOKEN` | Yes (for admin) | `""` | Constant-time Bearer token & dashboard login secret |
| `PENGU_SESSION_SECRET` | Recommended | Random per boot | HMAC-SHA256 key signing `HttpOnly` dashboard cookies |
| `PENGU_DATA_DIR` | No | `./data` | Directory for `bug-reports.json`, `store.json` & attachments |

---

## Bug Report API Documentation

### Public Endpoints

#### `POST /api/bug-reports`
Creates a validated bug report with optional base64 screenshot attachments (`image/png`, `image/jpeg`, `image/webp` verified via magic bytes; max 3 images, 3 MB each, 12 MB total request limit) and optional redacted diagnostics.
- **Rate limit:** `15` requests per `15` minutes per client IP (`429 Retry-After`).
- **Duplicate protection:** Identical `(category, title, description, actualBehavior)` submissions within `10` minutes return `409 Conflict`.
- **Example:**
```bash
curl -X POST https://your-server.example.com/api/bug-reports \
  -H "Content-Type: application/json" \
  -d '{
    "title": "4K download stops at 99%",
    "category": "Downloading",
    "description": "Merging audio and video stops cleanly at 99% on Wi-Fi.",
    "stepsToReproduce": "1. Paste URL\n2. Choose 2160p",
    "expectedBehavior": "File finishes merging cleanly",
    "actualBehavior": "Task fails at muxing step",
    "diagnostics": {
      "appVersion": "1.2.0",
      "versionCode": 102000400,
      "androidVersion": "15",
      "apiLevel": 35,
      "deviceManufacturer": "Google",
      "deviceModel": "Pixel 8",
      "supportedAbis": ["arm64-v8a"],
      "ytDlpVersion": "2025.01.15"
    }
  }'
```
Returns `201 Created`:
```json
{
  "id": "BR-20261128-9F4C2A",
  "trackingToken": "<48-hex-unguessable-token>",
  "status": "Open",
  "category": "Downloading",
  "createdAt": "2026-11-28T08:00:00.000Z",
  "attachmentCount": 0
}
```

#### `GET /api/bug-reports/:id/status`
Retrieves live report status (`Open`, `Reviewing`, `In Progress`, `Fixed`, `Rejected`, `Closed`) and `developerResponse`.
- Requires `X-Tracking-Token: <trackingToken>` header (compared via SHA-256 + timing-safe equality).

#### `GET /api/bug-reports/:id/attachments/:attachmentId`
Authorized screenshot download requiring valid `X-Tracking-Token` (or admin session/Bearer token).

---

### Admin-Protected Endpoints & Developer Dashboard

#### `GET /admin/bug-reports`
Server-rendered Developer Bug Management Dashboard. Unauthenticated visitors receive only a sign-in form; authenticated sessions receive search, category/version/status filtering, diagnostic inspection, status updates, developer replies, screenshot previews, and spam report deletion.

#### `POST /api/admin/login` / `POST /api/admin/logout`
Issues or clears `pengu_admin_session` (`HttpOnly; SameSite=Strict` + `Secure` over HTTPS).

#### `GET /api/admin/bug-reports?search=&category=&status=&version=&page=1&limit=20`
Lists paginated reports (`Authorization: Bearer <PENGU_ADMIN_TOKEN>` or valid session cookie).

#### `GET /api/admin/bug-reports/:id`
Returns full sanitized report details, diagnostics, and attachment metadata.

#### `PATCH /api/admin/bug-reports/:id/status`
Updates report `status` (`Open | Reviewing | In Progress | Fixed | Rejected | Closed`) and `developerResponse`, recording an entry in `data/admin-audit.log`:
```bash
curl -X PATCH https://your-server.example.com/api/admin/bug-reports/BR-20261128-9F4C2A/status \
  -H "Authorization: Bearer $PENGU_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"Fixed","developerResponse":"Resolved muxer fallback in Pengu 1.2.1."}'
```

#### `DELETE /api/admin/bug-reports/:id`
Deletes a spam report record + stored screenshot binaries from disk and appends an admin audit log line.

---

## Attachment Storage & Security Protections

- **Separate Persistent Storage**: Bug reports live in `data/bug-reports.json` (isolated from `data/store.json`).
- **Concurrency & Corruption Recovery**: Serialized write-queue + `fsync` temp file + `.bak` rotating backup + automatic `.bak` restore if `bug-reports.json` is damaged.
- **No Client-Trust**: Client MIME names/extensions are ignored; raw bytes are verified against PNG (`89 50 4E 47`), JPEG (`FF D8 FF`), and WebP (`RIFF...WEBP`) signatures and saved under random hex names inside `data/attachments/` with `nosniff` + `Content-Security-Policy: sandbox`.
- **HTTPS Requirement**: Pengu's Android client rejects plain `http://` URLs. Deploy behind Nginx TLS, Caddy, Cloudflare Tunnel, Railway, Fly.io, or Render with HTTPS enabled and configure the `https://` URL inside **Settings -> Troubleshooting -> Report a Bug -> Server Settings**.

---

## Automated Tests

```bash
cd beta-backend
npm test
```
Runs both `test_server.mjs` and `test_bug_reports.mjs`.
