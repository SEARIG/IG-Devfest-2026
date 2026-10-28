# Feature completion matrix

Status vocabulary:

- **Verified locally** — passed source/build/test/emulator evidence in this workspace.
- **Mock-tested** — provider contract exercised without live credentials or spend.
- **Awaiting credentials** — implementation exists but the secret/provider account is absent.
- **Awaiting deployment** — prepared locally but not published or mutated remotely.
- **Awaiting physical/live verification** — emulator or contract evidence exists, but the real device/service path was not completed.

| Area | Status | Evidence / remaining activation |
|---|---|---|
| Native Android + English/Hindi | Verified locally | Compose app builds; debug and minified release variants launch on emulator |
| Firebase Android configuration | Verified read-only | Active app com.agroai.app in agro-ai-34b4a; Firestore Native DB in asia-south1 |
| Firebase phone authentication code | Verified locally | Real PhoneAuthProvider; no hardcoded OTP |
| Phone provider/SMS regions/test numbers/billing | Awaiting physical/live verification | Console-only settings were not changed or asserted; no SMS sent |
| SHA certificate registration | Awaiting deployment | Firebase currently reports no registered SHA fingerprint; exact debug values are in the activation checklist |
| Room-first farm data | Verified locally | Owner-scoped Room v3, non-destructive migrations, 12 device tests |
| Firestore sync engine | Verified locally | Revision/tombstone/conflict flow preserved; demo owner rejected; rules pass 4 emulator tests |
| Production Firestore rules | Awaiting deployment | Tightened local rules were not deployed |
| Deterministic calculations | Verified locally | Local engine remains the first route |
| Voice input/output | Verified on physical phone with limitation | Speech recognition produced text and TTS control did not crash; acoustic output quality was not independently measured |
| Connected assistant | Mock-tested / awaiting credentials | Authenticated backend and bounded provider adapter; no paid request |
| Crop Doctor local fallback | Verified locally | Symptom matching, uncertainty, and safe next steps |
| Crop image AI | Mock-tested / awaiting credentials | Validated/resized JPEG path and authenticated backend; no live vision call |
| Weather client | Verified contract and physical fallback | Controlled Jaipur Open-Meteo response contract passed; physical demo UI showed explicit fictional source and freshness |
| Official Mandi prices | Mock-tested / awaiting credentials | Parser, units, range validation, retry, and generic errors tested; no data.gov.in key |
| Backend production controls | Verified locally | Explicit CORS, Firebase auth, safe errors, timeouts, bounded retries, rate limit, structured logs, disabled production docs |
| Render service | Awaiting deployment | render.yaml, health check, runtime version, and secret placeholders prepared; no deployment |
| Fictional demo | Verified locally | Separate demo owner, no sync queue, reset isolation, plot/plan/activity/harvest/expense seeded |
| Debug APK | Verified locally | v2 debug signature and SHA-256 recorded |
| Minified release APK | Verified locally, unsigned | R8/shrink build and emulator launch passed; production keystore absent |
| Production signed release | Awaiting credentials | Requires secure release keystore and release certificate registration |
| Physical phone acceptance | Verified for core demo | Authorized 24108PCE2I phone: install/update, cold launch, bilingual UI, navigation, local tools, permission prompts, gallery picker, and Room-backed demo workflows passed; real GPS/camera capture and notification delivery remain pending |
