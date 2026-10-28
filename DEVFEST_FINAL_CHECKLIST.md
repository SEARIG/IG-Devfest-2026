# Final DevFest submission checklist

## Ready now

- [x] Preserved physical-phone-tested demo APK.
- [x] APK v2 debug signature verified.
- [x] APK SHA-256 recorded.
- [x] Offline fictional demo workflow verified.
- [x] English and Hindi verified on a physical phone.
- [x] Project source/config/document backup created without backend secrets or private keys.
- [x] Private Room/DataStore backup created outside the distributable folder.
- [x] Android unit/build/lint checks passed.
- [x] Backend tests and production-mode smoke passed.
- [x] Firestore rules emulator tests passed.
- [x] Physical screenshots captured.
- [x] Five-minute demo and recording instructions prepared.
- [x] Overview, feature matrix, architecture, limitations, test report, and handoff prepared.

## Critical manual actions

- [ ] Record and review the final five-minute video.
- [ ] Confirm the DevFest portal accepts a debug-signed APK for controlled judging. If it requires production signing, complete FINAL_RELEASE_SIGNING_REPORT.md first.
- [ ] Copy only the intended submission files; exclude backups/ and any future keystore or secrets.

## Production signing, if required

- [ ] Create or select the owner-controlled keystore outside this project.
- [ ] Set all four AGRO_RELEASE variables securely.
- [ ] Build app-release.apk.
- [ ] Verify apksigner succeeds and the signer is not CN=Android Debug.
- [ ] Copy only the verified APK to release/AGRO-AI-DevFest-2026-release.apk.
- [ ] Record its SHA-256 and install it with adb install -r.
- [ ] Repeat the cold-launch and five-minute demo smoke.

## Optional live activation, only with authorization

- [ ] Register debug and production SHA-1/SHA-256 fingerprints in Firebase.
- [ ] Enable Phone Authentication, confirm India SMS policy, and configure fictional test numbers.
- [ ] Deploy reviewed Firestore rules with explicit approval.
- [ ] Verify one fictional test-number sign-in and one authenticated test sync; record documents and cleanup.
- [ ] Deploy Render with secret Firebase credentials and explicit CORS.
- [ ] Run one controlled non-sensitive chat/vision check within an approved budget.
- [ ] Add a data.gov.in key and verify one official Mandi response.

## Never submit

- backups/AGRO-AI-physical-demo-app-data-2026-10-28.tar
- a keystore, password, service-account file, backend .env, token, or paid-provider key
- release/AGRO-AI-1.0.0-release-unsigned.apk as a production APK
- any APK renamed as production without apksigner and device evidence
