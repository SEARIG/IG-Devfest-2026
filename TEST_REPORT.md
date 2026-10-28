# Test report — Phase 5 production activation

Date: 2026-10-23

## Emergency finalization recheck — 2026-10-28

- Android build: testDebugUnitTest, lintDebug, assembleDebug, and assembleRelease passed.
- Android JVM tests: 19 passed, 0 failed.
- Android lint: 0 errors, 27 warnings.
- Backend pytest: 9 passed.
- Firestore rules emulator: 4 passed, 0 failed; no deployment.
- Production-mode backend smoke: /health 200 with optional providers disabled, /docs 404, and unauthenticated /v1/chat 401.
- Open-Meteo: controlled Jaipur geocoding/forecast contract check returned Asia/Kolkata, 24 hourly points, and expected temperature/humidity/precipitation/wind units.
- Firebase read-only check: project and Android app active, google-services.json matches, Firestore Native database present in asia-south1, and zero Android SHA fingerprints registered.
- Authorized physical phone 24108PCE2I: debug update install, explicit cold launch, English/Hindi, dashboard, profile, mapping screen, planner/recommendations, calculator, calendar, assistant, Crop Doctor fallback, gallery picker, weather/Mandi fictional labeling, and harvest analytics verified.
- Private Room backup verified: one demo plot, two plans, one activity, one conversation, one inspection, one harvest, one expense, and zero demo sync records.
- No production Firebase mutation, rules deployment, real SMS, paid AI/vision request, live Mandi request, backend deployment, or production signing occurred.

## Automated checks

| Check | Result |
|---|---|
| Python compilation | Passed: python -m compileall -q backend/app backend/tests |
| Backend pytest | 9 passed |
| Backend production smoke | /health 200, /docs 404, unauthenticated /v1/chat 401 |
| Android JVM tests | 19 passed, 0 failed |
| Android lint | 0 errors, 27 warnings |
| Debug and release assembly | Passed; release minified and resource-shrunk |
| Firestore rules emulator | 4 passed, 0 failed; no deployment |
| Android instrumentation | 12 passed, 0 failed on Pixel_7(AVD) - 17 |
| Debug install / cold launch | Passed; MainActivity resumed |
| Minified release runtime | Temporarily debug-signed outside release/, installed, cold-launched, and opened fictional demo |
| Debug APK signature | v2 verified with Android Debug certificate |
| Release APK signature | Expected failure: handoff artifact is intentionally unsigned |

## Functional evidence

- Fictional demo: banner/reset, farmer, one plot, one active plan, activity, past completed plan, harvest, and expense were present.
- Demo isolation: demo is rejected by cloud queue and sync engine; instrumentation asserts no demo sync queue entry.
- Production backend controls: explicit-origin CORS, disabled production docs, generic validation/provider errors, one bounded transient retry, and structured request/token-usage logging have automated coverage.
- Firebase read-only inspection: project agro-ai-34b4a, active Android app com.agroai.app, and Firestore (default) Native database in asia-south1 were found. The Android app currently has no registered SHA fingerprint.
- Weather: one controlled free Open-Meteo geocoding/forecast request for Jaipur returned timezone Asia/Kolkata with 72 points and expected temperature/humidity/rain/wind units.
- Mandi: parsing, date/unit retention, min-modal-max ordering, retry, and safe failure were mock-tested. No live data.gov.in request was made.
- AI/vision: transport, schema, retry, authentication boundary, and safe error paths were mock-tested. No provider credential or paid request was used.

## Release artifacts

| Artifact | Size | SHA-256 | Signing |
|---|---:|---|---|
| release/AGRO-AI-1.0.0-debug.apk | 30,994,199 bytes | F5D93F451D3231E4A1346450BC2FBCFBAC408495363D1B656796DAF1621C6C09 | Android debug; v2 verified |
| release/AGRO-AI-1.0.0-release-unsigned.apk | 4,102,716 bytes | 8BC263DFFC2E49B08182160FB1F389D2F540CA05831947E9640C731DDBB45A77 | Unsigned by design |

Package: com.agroai.app; version code 1; version name 1.0.0; min SDK 24; target/compile SDK 36.

## Non-blocking warnings

- The bundled R8/AGP toolchain warns that Kotlin 2.4.21 metadata is newer than its supported metadata reader. The minified build and runtime smoke passed, but the Android Gradle Plugin/R8 should be upgraded when a compatible stable toolchain is available.
- One deprecated location API remains in the compatibility path.
- Two native libraries could not be stripped and were packaged as-is.
- Gradle reports features that will be incompatible with Gradle 9.

## Not performed

No production rules deployment, Firebase setting mutation, real SMS/OTP, authenticated production Firestore write, backend deployment, paid AI/vision call, live Mandi request, production signing, or physical-phone test was performed. Configuration readiness and mocks are not counted as live verification.
