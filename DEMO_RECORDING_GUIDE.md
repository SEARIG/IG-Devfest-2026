# Demo recording guide

Use the verified five-minute flow in DEVFEST_DEMO_GUIDE.md. Record in portrait orientation with notifications hidden and no personal phone numbers, photos, coordinates, tokens, or provider dashboards visible.

## Before recording

1. Install release/AGRO-AI-DevFest-2026-demo-debug.apk with adb install -r.
2. Verify SHA-256 F5D93F451D3231E4A1346450BC2FBCFBAC408495363D1B656796DAF1621C6C09.
3. Open Try fictional demo and confirm the DEMO MODE — fictional data card.
4. Set the phone to Do Not Disturb and close the notification shade.
5. Keep the app in English initially; switch briefly to Hindi on the profile screen.
6. Do not refresh live weather/Mandi or use real authentication during the guaranteed offline recording.

## Recommended recording

The phone's built-in screen recorder is preferable for a continuous five-minute recording and optional microphone narration.

ADB screenrecord is limited to three minutes per file on many Android versions. If using ADB, record two parts:

~~~powershell
$adb = "C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"

& $adb shell screenrecord --size 720x1280 --bit-rate 6000000 --time-limit 180 /sdcard/agro-demo-part1.mp4
& $adb pull /sdcard/agro-demo-part1.mp4 release\agro-demo-part1.mp4

& $adb shell screenrecord --size 720x1280 --bit-rate 6000000 --time-limit 180 /sdcard/agro-demo-part2.mp4
& $adb pull /sdcard/agro-demo-part2.mp4 release\agro-demo-part2.mp4
~~~

Start part 1 before the Welcome screen and stop after the assistant/calculator. Start part 2 at Crop Doctor and finish with weather/Mandi labels, harvest analytics, and the reset-isolation explanation.

## Narration guardrails

- Say “fictional demo” when showing the farmer, weather, and Mandi values.
- Say “local deterministic calculation,” not generative AI, for supported assistant answers.
- Say “possible causes, not a diagnosis” for Crop Doctor.
- Say “prepared but awaiting credentials/deployment” for connected AI, vision, and official Mandi.
- Do not claim production Phone Auth or Firestore sync until the authorized live cycle has been completed.

## Review before submission

- Confirm text is readable and no notification banner appears.
- Confirm the DEMO label and source/date fields are visible.
- Confirm there is no silent crash, blank screen, keyboard obstruction, or private information.
- Verify playback from beginning to end and record the final video filename and checksum in DEVFEST_FINAL_HANDOFF.md.
