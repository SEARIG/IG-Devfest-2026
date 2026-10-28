# Release verification

## Candidate identity

- Package: com.agroai.app
- Version code: 1
- Version name: 1.0.0
- Minimum SDK: 24
- Target / compile SDK: 36
- Build date: 2026-10-23

## Handoff artifacts

| File | SHA-256 | Verification |
|---|---|---|
| release/AGRO-AI-1.0.0-debug.apk | F5D93F451D3231E4A1346450BC2FBCFBAC408495363D1B656796DAF1621C6C09 | v2 signature; Android Debug certificate |
| release/AGRO-AI-1.0.0-release-unsigned.apk | 8BC263DFFC2E49B08182160FB1F389D2F540CA05831947E9640C731DDBB45A77 | Minified/resource-shrunk; intentionally unsigned |
| release/phase5-demo.png | See local file hash if transferring separately | Emulator evidence only, not production proof |

SHA256SUMS.txt contains the distributable APK checksums.

## Runtime evidence

- Debug APK installed and cold-launched on Pixel_7(AVD) - 17.
- The final unsigned release was copied to a temporary location, signed there with the standard Android debug key solely for emulator testing, verified, installed, and cold-launched.
- The minified variant opened the welcome screen and fictional demo dashboard.
- The temporary test-signed release is not a deliverable and is not present in release/.

## Manifest and release posture

- Release is not debuggable.
- Release disallows cleartext traffic; its backend URL must be blank or HTTPS.
- allowBackup=false.
- Production minification and resource shrinking are enabled.
- FileProvider is unexported and scoped to internal inspection images.
- The production keystore was absent, so no production-signed APK is claimed.

## Production signing procedure

1. Keep the keystore and passwords outside the repository and CI logs.
2. Set all four AGRO_RELEASE_* environment variables; partial signing configuration fails the build.
3. Build with AGRO_AI_RELEASE_BACKEND_URL=https://....
4. Run apksigner verify --verbose --print-certs.
5. Register the resulting release SHA-1/SHA-256 with Firebase.
6. Install on a clean physical device, complete the acceptance checklist, then publish the exact verified bytes.

## Known release risk

The current AGP/R8 emits Kotlin metadata-version warnings while processing Kotlin 2.4.21 dependencies. Assembly and minified runtime smoke passed, but production signing/distribution should include a compatible stable AGP/R8 upgrade assessment and a final physical-device regression.
