# Security review — Phase 5

Date: 2026-10-23

## Verified controls

| Area | Control |
|---|---|
| Android secrets | No backend/provider secret is embedded; Firebase client configuration is the expected public Android config and is ignored by Git |
| Authentication | Real Firebase ID tokens; no hardcoded OTP; backend verifies project token before protected routes |
| Local data | Room owner isolation; demo owner separate; non-destructive migrations; no demo cloud queue |
| Cloud sync | Sequential revisions, transaction checks, tombstones, owner-scoped paths; image URI excluded |
| Firestore rules | Default deny plus owner/path/type/length/range checks; 4 emulator tests pass |
| Images | MIME/dimension/5 MB validation; internal unexported FileProvider; image/prompt bodies not logged |
| Network | Release cleartext disabled; backend provider URLs require HTTPS outside localhost |
| API boundary | Strict request/response schemas, bounded strings/numbers, Firebase auth, per-user rate limit, timeouts, at most one transient retry |
| CORS | Disabled unless explicit origins are configured; wildcard rejected; no credentials |
| Errors/logging | Generic upstream errors; validation input stripped; request IDs and operational metadata only; optional aggregate token counts |
| Release | allowBackup=false, non-debuggable minified release, signing credentials sourced from environment only |

## Read-only production findings

- Firebase project and Android app exist and are active.
- Firestore (default) is Native mode in asia-south1.
- No SHA certificate fingerprints are currently registered for the Android app.
- Phone provider enablement, SMS region policy, test numbers, billing/quota, and production rules version were not asserted from the Console.

## Threats and remaining actions

1. **Unregistered release identity:** phone authentication may fail until production SHA-1/SHA-256 values are registered and refreshed configuration is downloaded.
2. **Undeployed rules:** local rules passed emulator tests but are not proof of the active production rules. Review and deploy explicitly.
3. **Credential handling:** use Render secret files or managed credentials for Firebase Admin; use secret variables for provider keys; restrict account IAM and rotate on exposure.
4. **Per-process rate limiting:** one worker is configured. Before horizontal scaling, replace it with a shared limiter and test atomic enforcement.
5. **Provider cost/abuse:** configure spend caps/alerts and monitor rate-limit/usage logs before enabling AI or vision.
6. **Data retention:** define retention/deletion for logs, provider requests, Firestore tombstones, and any uploaded diagnostic material before production.
7. **Physical-device permissions:** camera, location, microphone, gallery, and speech behavior require final phone testing across denial/revocation/offline paths.
8. **Toolchain compatibility:** assess the Kotlin metadata/R8 warning before store publication.

## Secret scan boundary

The workspace scan found no private key, OpenAI-style key, GitHub token, backend .env, or release keystore. app/google-services.json contains the expected Firebase Android client API key; it is not a server credential and remains ignored. This is a local pattern scan, not an external secret-manager or repository-history audit.

## Actions deliberately not taken

No rules deploy, Firebase configuration write, account creation, SMS, production Firestore write, backend deployment, provider activation, paid request, or release-key generation occurred.
