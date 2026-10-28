# AGRO AI — one-page project overview

AGRO AI is a native Android farming companion built for DevFest 2026. It helps a farmer keep farm records, plan crops, calculate inputs, track activities and harvests, inspect crop symptoms, and view weather or market information without making unsafe promises about live data.

## What makes it useful

- **Offline-first:** Room is the primary data store. Farm records, plans, calendar activities, deterministic calculations, symptom guidance, and harvest analytics work without a backend.
- **Bilingual:** English and Hindi can be switched from the app header.
- **Farmer-owned records:** authenticated data is scoped by Firebase UID; local and demo owners remain separate.
- **Honest intelligence:** supported calculations and crop recommendations use deterministic local rules. Connected AI and vision remain disabled until a secure authenticated backend is configured.
- **Safe demonstration:** Demo Mode uses an isolated fictional owner and clearly labels its farmer, weather, and Mandi records as fictional.

## Demonstrated workflow

1. Select English or Hindi.
2. Open the fictional farmer dashboard and profile.
3. Review a plot, Farm Scan mapping screen, and crop recommendations.
4. Inspect the active crop plan and calendar activity.
5. Run seed and farm calculations locally.
6. Ask the offline assistant a supported farming question.
7. Run a symptom-only Crop Doctor assessment with uncertainty and next-step guidance.
8. Review explicitly fictional weather and Mandi cards.
9. Review harvest quantity, expenses, revenue, and gross margin.

## Technology

- Kotlin, Jetpack Compose, Room, WorkManager, Android SpeechRecognizer/TTS
- Firebase Phone Authentication and Firestore sync, gated by real authentication
- FastAPI backend for authenticated AI/vision and official Mandi lookup
- Open-Meteo weather client with attribution and cached fallback
- AGMARKNET/data.gov.in Mandi adapter with source date and INR/quintal units

## Current submission status

The debug-signed demonstration APK is verified on an authorized physical Android phone and is suitable for a controlled DevFest demo. Production signing is blocked only by the missing owner-controlled keystore. Firebase Phone Auth Console settings, SHA registration, backend deployment, paid AI/vision, and live Mandi activation are not claimed as complete.

The reliable presentation path is the offline fictional demo described in DEVFEST_DEMO_GUIDE.md.
