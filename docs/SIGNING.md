# Stable Android signing

Dice Thrower uses a dedicated Android signing identity. The application identity must remain:

- namespace: `com.keyserdsoze.dicethrower`
- application ID: `com.keyserdsoze.dicethrower`
- keystore alias: `dicethrower`

The private keystore is never committed. GitHub Actions restores it at runtime from repository secrets and exposes only its temporary path to Gradle.

## Required release secrets

- `DICETHROWER_KEYSTORE_B64`: base64 encoding of the complete Dice Thrower JKS file.
- `DICETHROWER_KEYSTORE_PASSWORD`: password for both the JKS and the `dicethrower` key entry.

The build receives the decoded temporary file through `DICETHROWER_KEYSTORE_PATH`. A release workflow must invoke Gradle with `-PrequireStableSigning=true`; this makes configuration fail immediately if either signing input is absent or the restored keystore does not exist.

The release workflow is gated by the repository variable `RELEASE_ENABLED=true`. It also requires the non-secret repository variable `DICETHROWER_GOOGLE_WEB_CLIENT_ID` so a published build can never accidentally ship with Google connection disabled.

Optional Google Play publishing is gated separately with `GOOGLE_PLAY_ENABLED=true`. It consumes the already signed AAB from the GitHub Release, verifies its SHA-256 checksum, and uploads it to the Internal track (or `GOOGLE_PLAY_AUTO_TRACKS` when explicitly configured) using the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` repository secret.

Local unsigned builds remain supported when neither signing variable is configured.

## Certificate fingerprints

The public certificate can be inspected without exposing the private key:

```bash
keytool -list -v -keystore dicethrower-release.jks -alias dicethrower
```

Record the release SHA-1 when configuring the Android OAuth client for `com.keyserdsoze.dicethrower`. If Google Play App Signing is enabled, the Play app-signing certificate has its own fingerprint and must also be represented in the production Google configuration where required.

## Key custody

Keep a recoverable, access-controlled backup of the original JKS and its password outside the Git repository. GitHub repository secrets are deployment inputs, not the only backup of the signing identity. Never put the JKS, its base64 representation, or its password in source code, issues, workflow logs, or release artifacts.
