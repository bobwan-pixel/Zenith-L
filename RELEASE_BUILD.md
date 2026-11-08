# Zenith'L Android build and release

## Local Debug APK

Requirements: JDK 17 and Android SDK platform 35, NDK 27.2.12479018, and CMake 3.22.1.

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Local signed Release APK

Local and GitHub Release builds must use the exact same permanent release keystore and key alias.

1. Keep the permanent keystore at `signing/zenithl-release.jks` (or set another path).
2. Copy `keystore.properties.example` to `keystore.properties`.
3. Set the real `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` values.
4. Build:

```powershell
.\gradlew.bat clean assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Never generate a new release key for each version. Losing the original key can prevent existing users from installing future updates.

## Configure GitHub Actions signing

In repository Settings > Secrets and variables > Actions, add these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64 encoding of the exact same `.jks` file used locally.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: key password.

Create the Base64 value in Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("signing/zenithl-release.jks")) | Set-Clipboard
```

Paste the clipboard value into `ANDROID_KEYSTORE_BASE64`. Do not commit the keystore, `keystore.properties`, passwords, or Base64 key material.

The `Build ZenithL APK` workflow runs on pushes to `main` and manual dispatch. It builds Debug and Release APKs and uploads both as GitHub Actions artifacts. Before the four secrets are configured, the main workflow deliberately labels the Release APK artifact as unsigned. Do not distribute that unsigned APK as an update.

After all four secrets exist, the same main workflow signs the Release APK. The `Android Release` workflow runs when a `v*` tag is pushed, requires signing secrets, verifies the APK signature, and publishes the signed APK as a GitHub Release asset.

## Versioning

Version values live in the root `gradle.properties`:

- Increase `VERSION_CODE` for every distributable update. It must always increase.
- Update `VERSION_NAME` for the user-facing version, such as `0.5.0`.
- Keep the Git tag aligned with the version name, such as `v0.5.0`.

Example:

```properties
VERSION_CODE=5
VERSION_NAME=0.5.0
```

Create a release tag after committing the version update:

```powershell
git tag v0.5.0
git push origin v0.5.0
```

Do not change `applicationId` or the release signing key for ordinary updates. Debug intentionally uses the separate `.debug` application ID and is not an update replacement for Release.

## Signing compatibility

Both build routes use the same keystore bytes and alias. Local builds read credentials from the ignored `keystore.properties`; GitHub Actions reads them from repository Secrets. An APK from either route is update-compatible only when signed by that same key. Protect a secure backup of the keystore and its passwords.
