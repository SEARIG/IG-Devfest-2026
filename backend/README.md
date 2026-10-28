# AGRO AI backend

FastAPI gateway for authenticated AI chat, crop-image analysis, and official Mandi data. `/health` is public; every `/v1/*` route requires a valid Firebase ID token for project `agro-ai-34b4a`.

## Local setup

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

`.env.example` is a reference only; the application does not load `.env` automatically. Set values through the shell, IDE, or a secret manager, then run:

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
python -m pytest tests -q
```

Do not commit `.env`, service-account JSON, Firebase tokens, or provider keys.

## Configuration

| Setting | Purpose / production rule |
|---|---|
| `ENVIRONMENT` | `development`, `test`, or `production`; production disables OpenAPI/docs routes |
| `FIREBASE_PROJECT_ID` | Must remain `agro-ai-34b4a` for this app |
| `GOOGLE_APPLICATION_CREDENTIALS` | Optional path to a securely mounted scoped service-account file; Application Default Credentials are also supported |
| `CORS_ALLOWED_ORIGINS` | Comma-separated explicit HTTPS origins; `*` is rejected and an empty value sends no CORS headers |
| `RATE_LIMIT_PER_MINUTE` | Per-user, per-process request limit |
| `LIVE_AI_ENABLED` | `false` by default; requires the AI settings below |
| `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`, `AI_VISION_MODEL` | OpenAI-compatible provider; non-local URLs must use HTTPS |
| `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS` | Provider timeout and bounded output |
| `AI_MAX_RETRIES` | `0` or `1`; retries transport errors and 502/503/504 only |
| `DATA_GOV_API_KEY` | Enables official Mandi lookup |
| `MANDI_TIMEOUT_SECONDS`, `MANDI_MAX_RETRIES` | Mandi timeout and `0` or `1` transient retry |

The API schema-validates requests and provider output. Crop images are decoded and checked for supported type, dimensions, and a 5 MB limit. Error responses do not disclose upstream bodies, credentials, image bytes, or validation input. Structured operational logs contain request ID, route, status, duration, and optional AI token counts—not tokens, prompts, images, or query strings.

The in-memory rate limiter is per process (`shortcut: use a shared limiter before deploying multiple workers or instances`).

## Render deployment preparation

The root `render.yaml` declares a Python web service with:

- `backend/` as the root directory;
- pinned Python `3.13.5` via `.python-version`;
- one Uvicorn worker so the in-memory limiter remains coherent;
- `/health` as the health-check path;
- production mode, bounded limits, and `autoDeploy: false`;
- secret values deliberately omitted so they must be entered or mounted securely.

In Render, create the service from `render.yaml`, add provider keys as secret environment variables, and mount Firebase credentials as a secret file. Set `GOOGLE_APPLICATION_CREDENTIALS` to that mounted file's absolute path. Do not paste service-account JSON into a normal variable or the repository.

After deployment:

```powershell
curl.exe -i https://YOUR-SERVICE.onrender.com/health
```

Confirm provider booleans match the settings, then call each `/v1/*` route with a fresh Firebase ID token from an approved test account. Verify unauthenticated calls return `401`, invalid payloads return safe `422` responses, and CORS headers appear only for explicitly allowed origins.

No Render deployment was performed in Phase 5.

## Provider activation boundaries

- AI and vision were exercised only with mock transports; no credential or paid request was available.
- Mandi parsing, range validation, retry, and generic failure handling were mock-tested. No data.gov.in key was available for a live request.
- Paid-provider account budgets and alerts must be configured at the provider. The server caps output tokens but cannot enforce currency spend.
