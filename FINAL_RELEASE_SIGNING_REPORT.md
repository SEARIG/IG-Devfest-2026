# Final Android release signing report

Last reverified: 2026-10-28

## Status

**Blocked only on production signing credentials.**

No production keystore was found in the workspace, none of the four AGRO_RELEASE signing variables is set, and release/AGRO-AI-DevFest-2026-release.apk was deliberately not created.

The Gradle release configuration is ready and fail-closed:

- all four signing values must be provided together;
- passwords and the keystore come only from environment variables;
- release builds are non-debuggable, minified, and resource-shrunk;
- cleartext traffic is disabled;
- the release backend URL must be blank or HTTPS.

## Verified candidate

- Application ID: com.agroai.app
- Version code: 1
- Version name: 1.0.0
- Minimum SDK: 24
- Target / compile SDK: 36
- Backups: disabled, including explicit cloud-backup and device-transfer exclusions
- FileProvider: unexported
- Existing candidate: app/build/outputs/apk/release/app-release-unsigned.apk
- Unsigned candidate SHA-256: 8BC263DFFC2E49B08182160FB1F389D2F540CA05831947E9640C731DDBB45A77

apksigner correctly reports that the candidate does not verify because it is unsigned. It must not be distributed as a production release.

Current regression evidence:

- testDebugUnitTest: 19 passed
- lintDebug: 0 errors, 27 warnings
- assembleRelease: passed
- Current-source emulator instrumentation: 12 passed on Pixel_7(AVD) - 17
- Preserved debug APK installed and cold-launched on an authorized physical 24108PCE2I phone; complete offline demo workflow verified
- Production-signed install/launch: pending credentials

## Secure one-time identity creation

Run this interactively from a secure administrator-controlled location outside the repository. Replace the example path; do not add passwords or -dname on the command line.

~~~powershell
keytool -genkeypair -v -keystore "D:\PRIVATE-KEYS\agro-ai-release.p12" -storetype PKCS12 -alias "agro-ai-release" -keyalg RSA -keysize 4096 -validity 10000
~~~

Keytool will prompt for the password and certificate identity. Back up the resulting file in at least two encrypted, access-controlled locations. Record the alias and certificate fingerprints separately. Loss of this identity can prevent future updates; exposure requires an incident response.

If an organization-owned production keystore already exists, use it instead of creating another identity.

## Build and verify after credentials are available

Open a fresh PowerShell session. These prompts mask passwords and keep them out of command history:

~~~powershell
$env:AGRO_RELEASE_STORE_FILE = Read-Host "Absolute production keystore path"
$env:AGRO_RELEASE_STORE_PASSWORD = Read-Host "Keystore password" -MaskInput
$env:AGRO_RELEASE_KEY_ALIAS = Read-Host "Key alias"
$env:AGRO_RELEASE_KEY_PASSWORD = Read-Host "Key password" -MaskInput
$env:AGRO_AI_RELEASE_BACKEND_URL = Read-Host "Approved HTTPS backend URL, or press Enter to disable connected backend features"

.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
~~~

The signed build must be app/build/outputs/apk/release/app-release.apk. If only app-release-unsigned.apk exists, stop: signing was not applied.

Verify the built APK before copying it:

~~~powershell
$apksigner = "C:\Users\Admin\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat"
$builtApk = "app\build\outputs\apk\release\app-release.apk"

& $apksigner verify --verbose --print-certs $builtApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }

$signer = & $apksigner verify --print-certs $builtApk
if ($signer -match "CN=Android Debug") { throw "Refusing Android Debug certificate" }
~~~

Only after those checks pass:

~~~powershell
$finalApk = "release\AGRO-AI-DevFest-2026-release.apk"
Copy-Item -LiteralPath $builtApk -Destination $finalApk -Force

& $apksigner verify --verbose --print-certs $finalApk
if ($LASTEXITCODE -ne 0) { throw "Copied APK signature verification failed" }

Get-FileHash -Algorithm SHA256 -LiteralPath $finalApk
~~~

Register that production certificate's SHA-1 and SHA-256 with the existing Firebase Android app before production Phone Auth acceptance testing. Never substitute the Android debug certificate.

## Install, launch, and final regression

Do not clear app data. Install as an update so existing Room data is preserved:

~~~powershell
$adb = "C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$finalApk = "release\AGRO-AI-DevFest-2026-release.apk"

& $adb devices -l
& $adb install -r $finalApk
if ($LASTEXITCODE -ne 0) { throw "Release installation failed" }

& $adb shell am force-stop com.agroai.app
& $adb shell monkey -p com.agroai.app -c android.intent.category.LAUNCHER 1
& $adb shell dumpsys activity activities | Select-String "mResumedActivity|mCurrentFocus"
~~~

Confirm:

1. AGRO AI is the resumed application.
2. Existing Room records remain present after the update.
3. English/Hindi, login, fictional demo, farm records, planner, calculator, Crop Doctor fallback, weather fallback, Mandi fallback, voice controls, and harvest analytics open without crash.
4. Offline restart works.
5. The installed package reports versionCode 1 and versionName 1.0.0.
6. The final APK hash and signer certificate match the recorded release values.

After verification, clear the five variables without printing them and store the keystore only in the approved secure locations:

~~~powershell
Remove-Item Env:AGRO_RELEASE_STORE_FILE,Env:AGRO_RELEASE_STORE_PASSWORD,Env:AGRO_RELEASE_KEY_ALIAS,Env:AGRO_RELEASE_KEY_PASSWORD,Env:AGRO_AI_RELEASE_BACKEND_URL -ErrorAction SilentlyContinue
~~~
