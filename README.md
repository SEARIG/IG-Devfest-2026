# AGRO AI — DevFest 2026

Native Kotlin/Jetpack Compose farming assistant with Firebase phone authentication, Room-first farm records, optional Firestore sync, deterministic local calculations, voice input/output, crop inspection, Open-Meteo forecasts, official Mandi lookup, and an isolated fictional demo.

## Phase 5 status

The project is release-candidate ready for controlled testing, not activated in production. The Android debug and unsigned minified release APKs are in `release/`. Production signing, backend deployment, Firebase Console phone-auth settings, SHA registration, and live authenticated provider checks remain operator actions.

No OTP is hardcoded, no service/provider key is embedded in the APK, and the app remains useful offline. Paid AI and official Mandi lookup stay disabled until the backend is securely configured.

| Boundary | Current evidence |
|---|---|
| Android | 19 JVM tests, lint with 0 errors, 12 emulator instrumentation tests, debug and minified release cold-launch smoke |
| Backend | 9 pytest tests, production-mode Uvicorn smoke, authenticated route rejects missing token |
| Firebase | Project/app/Firestore inspected read-only; rules pass 4 emulator tests; nothing deployed |
| Weather | One controlled Jaipur Open-Meteo contract check; Android refresh still needs a connected field test |
| AI / vision / Mandi | Mock-tested only; credentials absent and no paid request made |
| Physical device | Core offline demo verified on authorized `24108PCE2I`; production-signed and live-service acceptance remain pending |

See [TEST_REPORT.md](TEST_REPORT.md), [RELEASE_VERIFICATION.md](RELEASE_VERIFICATION.md), and [FEATURE_COMPLETION_MATRIX.md](FEATURE_COMPLETION_MATRIX.md) for the precise evidence boundary.

## Build

Use JDK 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Debug builds may use a local HTTP endpoint:

```powershell
.\gradlew.bat assembleDebug -PAGRO_AI_BACKEND_URL=http://10.0.2.2:8000
```

Release builds accept only a blank or HTTPS backend URL:

```powershell
.\gradlew.bat assembleRelease -PAGRO_AI_RELEASE_BACKEND_URL=https://your-backend.example
```

Release signing is enabled only when all four environment variables are present: `AGRO_RELEASE_STORE_FILE`, `AGRO_RELEASE_STORE_PASSWORD`, `AGRO_RELEASE_KEY_ALIAS`, and `AGRO_RELEASE_KEY_PASSWORD`. Secrets and keystores must stay outside the repository.

## Production activation

Follow [PRODUCTION_ACTIVATION_CHECKLIST.md](PRODUCTION_ACTIVATION_CHECKLIST.md) in order. The key boundaries are:

1. Review and deploy `firestore.rules` to the confirmed `agro-ai-34b4a` project.
2. Register the debug and production signing SHA fingerprints, enable Phone Authentication, set SMS regions/test numbers, and confirm billing policy in Firebase Console.
3. Deploy `backend/` from `render.yaml` with secret environment values or a Render secret file; verify `/health` and authenticated routes.
4. Build a genuinely release-signed APK and verify its certificate and hash.
5. Run phone-auth, sync, weather, AI/vision, and Mandi acceptance checks on a physical Android phone.

These are intentionally not automated because they mutate production state, may send SMS, may incur cost, or require private credentials.

## Data and safety boundaries

- Room is the primary local store. Legacy rows remain owned by `local`; authenticated rows are scoped by Firebase UID.
- Firestore sync preserves sequential revisions and tombstones. Weather and Mandi cache rows are local and are not uploaded.
- Demo records use the separate `demo` owner, never enter the sync queue, and are clearly labeled fictional.
- Crop image output is uncertain decision support, not a confirmed diagnosis. The local fallback uses selected symptoms only.
- Crop image URIs stay out of Firestore payloads; the FileProvider exposes only internal inspection images.
- Open-Meteo attribution is displayed in the app. Its free API is non-commercial; production/commercial use must satisfy the provider's current plan and licence terms.
- Mandi records are requested from the official data.gov.in AGMARKNET dataset and retain source date/unit labels.

## Documentation

- [Backend deployment and configuration](backend/README.md)
- [Five-minute demo](DEVFEST_DEMO_GUIDE.md)
- [Demo recording guide](DEMO_RECORDING_GUIDE.md)
- [One-page project overview](PROJECT_OVERVIEW.md)
- [Architecture overview](ARCHITECTURE_OVERVIEW.md)
- [Feature completion matrix](FEATURE_COMPLETION_MATRIX.md)
- [Known limitations](KNOWN_LIMITATIONS.md)
- [Production activation checklist](PRODUCTION_ACTIVATION_CHECKLIST.md)
- [Release verification](RELEASE_VERIFICATION.md)
- [Final release-signing handoff](FINAL_RELEASE_SIGNING_REPORT.md)
- [Security review](SECURITY_REVIEW.md)
- [Test report](TEST_REPORT.md)
- [Final submission checklist](DEVFEST_FINAL_CHECKLIST.md)
- [Final DevFest handoff](DEVFEST_FINAL_HANDOFF.md)
