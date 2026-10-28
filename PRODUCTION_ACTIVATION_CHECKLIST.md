# Production activation checklist

Complete this checklist against the intended production accounts. Every remote mutation below is deliberately manual.

## 1. Release identity and signing

- [ ] Create or select the production keystore outside the repository; record its backup/ownership procedure.

~~~powershell
keytool -genkeypair -v -keystore C:\secure-path\agro-ai-release.jks -alias agro-ai -keyalg RSA -keysize 4096 -validity 10000
~~~

- [ ] Set AGRO_RELEASE_STORE_FILE, AGRO_RELEASE_STORE_PASSWORD, AGRO_RELEASE_KEY_ALIAS, and AGRO_RELEASE_KEY_PASSWORD only in the secure build environment.
- [ ] Build with the deployed HTTPS URL:

~~~powershell
.\gradlew.bat assembleRelease -PAGRO_AI_RELEASE_BACKEND_URL=https://YOUR-SERVICE.onrender.com
~~~

- [ ] Verify the APK and record certificate digests:

~~~powershell
apksigner verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
Get-FileHash -Algorithm SHA256 app\build\outputs\apk\release\app-release.apk
~~~

## 2. Firebase project and Android app

- [ ] Confirm project agro-ai-34b4a, Android app ID 1:289373996219:android:c5557d20776b82371a159e, and package com.agroai.app.
- [ ] Register the current debug certificate if debug authentication is required:

~~~powershell
firebase apps:android:sha:create 1:289373996219:android:c5557d20776b82371a159e D2:5F:00:51:F4:BD:C5:26:EC:ED:F5:A4:33:0B:80:75:24:2B:0D:12 --project agro-ai-34b4a
~~~

Debug SHA-256: 35:74:91:D2:49:16:2F:13:E3:0C:35:60:BD:AF:06:FE:B4:80:C9:9D:E1:D9:59:8B:27:E8:1A:B4:DD:89:75:63.

- [ ] Register both SHA-1 and SHA-256 from the production release certificate; do not reuse the debug values.
- [ ] Download a fresh google-services.json after registration and confirm it still targets the same app/project.
- [ ] In Firebase Console, enable Phone Authentication.
- [ ] Configure allowed SMS regions, including the intended India policy.
- [ ] Add Firebase test phone numbers for demos; do not use a real SMS for routine staging.
- [ ] Confirm the current Firebase billing/SMS quota requirements before real-number acceptance testing.

## 3. Firestore

- [ ] Confirm the existing (default) Native database in asia-south1.
- [ ] Review the local firestore.rules diff and rerun emulator tests:

~~~powershell
npm ci
npm run test:rules
~~~

- [ ] Deploy only after approving the exact target:

~~~powershell
firebase deploy --only firestore:rules --project agro-ai-34b4a
~~~

- [ ] With an approved test account, verify create/read/update/delete isolation, revision conflict handling, tombstones, retry, and sign-out data separation.
- [ ] Record all test documents created and remove only those approved test records after verification.

## 4. Backend and Render

- [ ] Create the Render service from root render.yaml; keep autoDeploy false until review.
- [ ] Add Firebase credentials as a secret file or use workload/Application Default Credentials.
- [ ] Set AI/data.gov.in secrets only if those providers are approved.
- [ ] Set explicit CORS_ALLOWED_ORIGINS; never use *.
- [ ] Configure provider spend alerts/budgets and review log retention/access.
- [ ] Deploy and verify /health, TLS, production docs disabled, CORS, safe errors, rate limiting, timeout, and one-retry behavior.
- [ ] Test every protected route with a valid Firebase ID token and confirm missing/expired/wrong-project tokens fail.
- [ ] Keep one worker/instance until the rate limiter is moved to a shared store.

## 5. Live provider and device acceptance

- [ ] On a physical Android phone, verify English/Hindi layout, permissions, camera/gallery, speech recognition, TTS, location, background sync, offline restart, and small/large screens.
- [ ] Verify one Firebase test-number sign-in and token refresh; run a real SMS only with explicit approval.
- [ ] Verify Open-Meteo refresh, attribution, stale/fresh cache states, timezone, and offline fallback.
- [ ] Verify one approved non-sensitive AI chat and crop image; inspect schema, uncertainty, latency, logs, and provider usage.
- [ ] Verify one data.gov.in Mandi lookup with source date, location, INR/quintal, and min/modal/max ordering.
- [ ] Rerun the five-minute demo on the exact presentation APK/device/network.

## 6. Approval record

- [ ] Record production APK hash/certificate, backend URL/revision, Firebase rules revision, test account, test documents, provider requests/cost, results, cleanup, approver, and timestamp.
