# Known limitations

Last verified: 2026-10-28

## Critical release blockers

- No production keystore or AGRO_RELEASE signing variables are available.
- release/AGRO-AI-DevFest-2026-release.apk does not exist and must not be fabricated from an unsigned or debug-signed APK.
- Firebase reports zero SHA fingerprints for com.agroai.app.

## Live-service limitations

- Phone Authentication provider status, India SMS policy, test numbers, and billing/quota are Console-only and remain unverified.
- Local Firestore rules pass emulator tests but were not deployed.
- No authenticated production Firestore write/sync cycle was run.
- Render is prepared but not deployed.
- AI and vision provider paths are mock-tested only; no paid credential was supplied.
- No data.gov.in key was supplied, so live Mandi lookup was not run.
- Open-Meteo contract was checked live, but the controlled demonstration intentionally keeps fictional weather data.

## Physical-device limitations

- Core demo navigation and local workflows passed on an authorized 24108PCE2I phone.
- Location and camera permission prompts were verified, but private GPS coordinates and camera captures were deliberately not collected.
- The system gallery picker opened and returned safely; no private image was selected.
- Speech recognition produced text. TTS was invoked without a crash, but acoustic output quality was not independently measured.
- Notification permission and real reminder delivery were not verified.
- Device radios were not disabled, so offline behavior is supported by architecture and Demo Mode rather than a destructive connectivity toggle on the user's phone.

## Toolchain limitations

- Gradle 8.13 produces a deprecation warning.
- The existing AGP/R8 toolchain reports Kotlin metadata compatibility warnings with Kotlin 2.4.21 during shrinking. The minified build and runtime smoke have passed.
- The backend rate limiter is per process; horizontal scaling requires a shared limiter.

## Submission wording

Call the preserved debug APK a controlled demonstration APK. Call features live only after the exact service/device path has been verified. Do not describe mock, cached, fictional, unavailable, or parser-only evidence as production live data.
