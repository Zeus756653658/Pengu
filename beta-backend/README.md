# Pengu Beta Server

Optional self-hosted HTTPS backend for **Pengu Beta Program**:
- **Open Beta** & **Closed Beta** enrollment with invite-code & seat-capacity gating (`data/store.json`)
- **Beta / Closed Beta / Stable / Archived** release feeds & download counters
- Static APK hosting under `/apks/`

## Run Locally (Node 18+, zero dependencies)
```bash
npm start
```

## Run Tests
```bash
npm test
```

## HTTPS Requirement
Pengu's Android client enforces **HTTPS-only** (`https://`) URLs for all downloads and Beta endpoints. Deploy behind Cloudflare Tunnel, Fly.io, Render, Railway, or Nginx TLS and enter the `https://` URL inside **Settings -> Pengu Beta Program -> My Testing Program**.
