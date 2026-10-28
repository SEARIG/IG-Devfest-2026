# AGRO AI — DevFest 2026 final handoff

Last verified: 2026-10-28, Asia/Calcutta

## Submission decision

The reliable submission path is the physical-phone-tested, debug-signed demonstration APK and the isolated offline fictional demo. It is suitable for an authorized controlled DevFest demonstration, but it is not a production-signed store release.

Production signing, Firebase Console activation, backend deployment, paid AI/vision, and live official Mandi remain credential/authorization-gated. None is required for the guaranteed offline presentation.

## Primary artifacts

| Artifact | Location | Verification |
|---|---|---|
| Controlled demo APK | release/AGRO-AI-DevFest-2026-demo-debug.apk | v2 Android Debug signature; installed and cold-launched on physical 24108PCE2I |
| Demo APK SHA-256 | F5D93F451D3231E4A1346450BC2FBCFBAC408495363D1B656796DAF1621C6C09 | Matches app/build/outputs/apk/debug/app-debug.apk |
| Unsigned minified candidate | release/AGRO-AI-1.0.0-release-unsigned.apk | SHA-256 8BC263DFFC2E49B08182160FB1F389D2F540CA05831947E9640C731DDBB45A77; not distributable as production |
| Source/config/docs backup | release/AGRO-AI-source-backup-2026-10-28.zip | Local-only archive; excluded from Git because it contains Firebase client configuration. Do not distribute it as a public source artifact. |
| Private Room/DataStore backup | backups/AGRO-AI-physical-demo-app-data-2026-10-28.tar | SHA-256 754FAD8FE49A3ED543D1FCA3741FCEB3CCB82A87A22CA5FD0EE193627575A280; do not distribute |

release/AGRO-AI-DevFest-2026-release.apk does not exist because no production signing identity was supplied.

## Physical-phone evidence

Verified on authorized model 24108PCE2I:

- update installation and explicit cold launch;
- Welcome/language selection;
- fictional farmer dashboard and profile;
- English and Hindi;
- My Farm and Farm Scan mapping screen;
- Crop Planner local recommendations;
- seeded crop plans and calendar activity;
- deterministic wheat seed calculation;
- Farm Calculator;
- local assistant and Android speech recognition;
- Crop Doctor camera permission prompt, gallery picker, symptom-only assessment, uncertainty, and saved inspection;
- explicit fictional Weather and Mandi source/date labels;
- harvest quantity, expenses, revenue, and gross-margin analytics;
- Room backup after the workflow.

Room evidence from the stopped-app backup:

- demo plots: 1
- demo plans: 2
- demo activities: 1
- demo conversations: 1
- demo inspections: 1
- demo harvests: 1
- demo expenses: 1
- demo sync records: 0

Key screenshots:

- release/physical-demo-home.png
- release/physical-profile-hi.png
- release/physical-crop-doctor-result.png
- release/physical-harvest.png
- release/phase5-demo.png

The related physical UI hierarchy evidence is stored beside the screenshots as physical-*.xml.

## Automated and service evidence

- Android JVM: 19 passed, 0 failed.
- Android lint: 0 errors, 27 warnings.
- assembleDebug and minified assembleRelease: passed.
- Existing current-source instrumentation report: 12 passed on Pixel_7(AVD) - 17.
- Backend pytest: 9 passed.
- Production-mode backend smoke: /health 200; /docs 404; unauthenticated /v1/chat 401.
- Firestore rules emulator: 4 passed, 0 failed; no deployment.
- Open-Meteo live contract check: Jaipur, Asia/Kolkata, 24 hourly points, expected °C/%/mm/km/h units.

## Firebase status

Read-only verification:

- Project agro-ai-34b4a is active.
- Android app ID 1:289373996219:android:c5557d20776b82371a159e is active.
- Package com.agroai.app and app/google-services.json match.
- Firestore (default) is Native mode in asia-south1.
- Registered Android SHA fingerprints: zero.

Not verified from Console: Phone provider enablement, India SMS policy, fictional test numbers, and billing/quota. No SMS was sent and no production write was made.

Exact commands, if authorized:

~~~powershell
firebase apps:android:sha:create 1:289373996219:android:c5557d20776b82371a159e D2:5F:00:51:F4:BD:C5:26:EC:ED:F5:A4:33:0B:80:75:24:2B:0D:12 --project agro-ai-34b4a

firebase deploy --only firestore:rules --project agro-ai-34b4a
~~~

Do not run either command until the target and intended production change are explicitly approved. Register the production certificate separately after a real release key exists.

## Backend deployment checklist

render.yaml is prepared with backend/ as root, one Uvicorn worker, /health, production mode, autoDeploy false, and no secrets in source.

Required secure values:

- FIREBASE_PROJECT_ID=agro-ai-34b4a
- Firebase Application Default Credentials or a scoped secret file
- explicit CORS_ALLOWED_ORIGINS

Optional provider activation:

- LIVE_AI_ENABLED=true
- AI_BASE_URL using HTTPS
- AI_API_KEY
- AI_MODEL
- AI_VISION_MODEL
- AI_TIMEOUT_SECONDS, AI_MAX_RETRIES, AI_MAX_OUTPUT_TOKENS
- DATA_GOV_API_KEY
- MANDI_TIMEOUT_SECONDS, MANDI_MAX_RETRIES

No deployment or paid provider call was made.

## Documentation map

- README.md — build and production boundaries
- PROJECT_OVERVIEW.md — one-page product overview
- ARCHITECTURE_OVERVIEW.md — data flow and trust boundaries
- DEVFEST_DEMO_GUIDE.md — five-minute judge flow
- DEMO_RECORDING_GUIDE.md — safe recording procedure
- FEATURE_COMPLETION_MATRIX.md — verified/mock/pending matrix
- KNOWN_LIMITATIONS.md — honest blockers and risks
- TEST_REPORT.md — executed evidence
- FINAL_RELEASE_SIGNING_REPORT.md — exact production signing procedure
- PRODUCTION_ACTIVATION_CHECKLIST.md — Firebase/backend/live activation
- DEVFEST_FINAL_CHECKLIST.md — final submission gate

## Exact remaining manual actions

### Truly critical

1. Record and review the five-minute demo using DEMO_RECORDING_GUIDE.md.
2. Confirm whether the DevFest portal accepts the debug-signed controlled-demo APK.
3. If production signing is mandatory, create/select the owner-controlled key outside the repo, set the four AGRO_RELEASE variables, and ask Codex to continue FINAL_RELEASE_SIGNING_REPORT.md.
4. Submit only intended artifacts; exclude backups/ and all credentials.

### Optional live activation

1. Register certificate SHA fingerprints.
2. Complete Phone Auth/India/test-number settings in Firebase Console.
3. Authorize the rules deployment and one fictional authenticated sync cycle.
4. Deploy Render with secure credentials.
5. Run bounded AI/vision and official Mandi checks.

If any optional service remains unavailable, use the verified offline demo and describe the service honestly as prepared but not activated.
