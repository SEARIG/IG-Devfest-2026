# AGRO AI architecture overview

## Runtime structure

~~~text
Jetpack Compose screens
        |
AppViewModel / feature view models
        |
Owner-scoped repositories
        |
Room v3 database  <---->  WorkManager sync queue  <---->  Firestore
        |
Local deterministic engines
        |
Optional provider interfaces
   |             |                    |
Open-Meteo   FastAPI gateway     Android platform services
             |      |            camera/gallery, GPS,
             |      |            notifications, speech, TTS
             |      |
             AI/vision provider
             AGMARKNET/data.gov.in
~~~

## Data ownership

- local: legacy or intentionally offline records.
- demo: isolated fictional records used only for presentation.
- Firebase UID: authenticated farmer records eligible for cloud synchronization.

Demo and local owners are rejected by the cloud scheduler and sync engine. Sync records use sequential revisions, owner-scoped document paths, conflict states, and tombstones instead of destructive remote deletion.

## Android security boundary

- Release builds are non-debuggable, minified, resource-shrunk, and cleartext-disabled.
- Backups and device transfer extraction are disabled.
- Crop images use an unexported FileProvider scoped to internal inspection_images.
- Provider keys and Firebase Admin credentials are never placed in Android resources.
- Release signing accepts all four AGRO_RELEASE variables together or none.

## Backend security boundary

- /health is public.
- Every /v1 route requires a Firebase ID token for agro-ai-34b4a.
- Requests and provider responses are schema-validated.
- CORS is disabled unless explicit origins are supplied; wildcard origins are rejected.
- Timeouts, output limits, per-user rate limiting, safe upstream errors, and structured operational logs are enabled.
- The current in-memory limiter requires one worker/instance until replaced by a shared limiter.

## External services

- Firebase: authentication and owner-scoped Firestore sync.
- Open-Meteo: weather geocoding/forecast with source and freshness.
- FastAPI: authenticated boundary for OpenAI-compatible chat/vision and data.gov.in Mandi data.
- Room remains the source of truth when networks or providers are unavailable.
