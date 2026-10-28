# DevFest demo guide

Use the fictional demo as the guaranteed path. Treat any connected segment as optional until that provider has passed the activation checklist on the presentation network and device.

## Five-minute judge flow

| Time | Action | What to say |
|---|---|---|
| 0:00–0:35 | Launch and select **Try fictional demo** | “This is an isolated, clearly labeled fictional dataset. It never enters Firebase sync.” |
| 0:35–1:10 | Show the dashboard, farmer, plot, active crop plan, activities, and Harvest records | “Room is the source of truth, so farm records remain usable offline.” |
| 1:10–1:50 | Open My Farm, map/plot details, Crop Planner, calendar, and seed/fertilizer calculators | “Core planning and calculations are deterministic and do not need paid AI.” |
| 1:50–2:30 | Open AGRO Assistant and ask “How much wheat seed for 2 acres?”; show English/Hindi and voice playback | “Known calculations run locally. Open-ended answers use the authenticated backend only when activated.” |
| 2:30–3:10 | Open Crop Doctor; show symptom fallback and the image path | “The result is decision support with uncertainty, not a confirmed diagnosis. Without vision credentials, the safe symptom workflow remains available.” |
| 3:10–3:50 | Open Weather and Mandi Prices | “The demo values are fictional. Connected weather shows source and freshness; official Mandi rows retain market date and INR/quintal units.” |
| 3:50–4:25 | Show expenses and harvest analytics | “The demo includes a past completed plan, harvest, and expense so the full season loop is visible.” |
| 4:25–5:00 | Show offline continuity and **Reset demo data** | “Reset replaces only demo-owned rows. Local and signed-in farmer records are not touched.” |

## Optional connected inserts

Use these only after same-day verification:

- Firebase Console test phone number: demonstrate real Firebase verification without sending a real SMS.
- Open-Meteo: refresh the profile district and point to the Open-Meteo.com — CC BY 4.0 attribution and freshness state.
- Mandi: fetch one known crop/state/district record and show its AGMARKNET/data.gov.in source date.
- AI/vision: submit one non-sensitive sample through the authenticated backend and state which provider is active.

Never call a cached, fictional, mocked, or fallback result “live.”

## Before presenting

- Install the intended APK and verify its SHA-256 and signing certificate.
- If presenting connected features, use a release-signed build with the deployed HTTPS backend URL.
- Confirm /health exposes only intentionally enabled providers.
- Use Firebase test phone numbers on stage; do not use a personal number.
- Check venue access to Firebase, Open-Meteo, the backend, and data.gov.in.
- Capture no personally identifying farm photo or token in logs/screenshots.
- Keep the fictional demo ready. If a dependency fails, state the fallback and continue rather than implying live success.

## Verified Phase 5 demo evidence

- Debug and minified release variants cold-launched on Pixel_7(AVD) - 17.
- The fictional banner, scoped reset, farmer, plot, active plan, activity, harvest data, Voice AI Assistant, and Crop Doctor were visible in emulator UI inspection.
- The screenshot is release/phase5-demo.png.
- On 2026-10-28 the preserved debug APK was installed as an update and cold-launched on an authorized physical 24108PCE2I phone.
- Physical-phone checks passed for English/Hindi, dashboard, profile, My Farm, Farm Scan screen, Crop Planner recommendations, calendar, deterministic seed calculation, Crop Doctor symptom fallback, weather/Mandi fictional labels, calculator, gallery picker, and harvest analytics.
- Location and camera permission prompts appeared and were dismissed without capturing private coordinates or photos. Speech recognition produced text; the TTS control was invoked without a crash, but acoustic output was not independently measured.
- Physical screenshots are in release/physical-demo-home.png, release/physical-profile-hi.png, release/physical-crop-doctor-result.png, and release/physical-harvest.png.
