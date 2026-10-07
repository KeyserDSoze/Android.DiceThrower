# Google production verification

Issue: #29

Dice Thrower must remain fully usable in standalone mode. Google connection is optional and the final production check must prove that authentication and Drive app-data synchronization work with the same signed artifact that is intended for distribution.

## Automated prerequisites

The release workflow fails unless all of these are present:

- stable Dice Thrower signing key;
- `DICETHROWER_GOOGLE_WEB_CLIENT_ID`;
- `DICETHROWER_GOOGLE_ANDROID_CLIENT_ID`.

The release build injects only the Web Client ID into Android resources. The Android Client ID is a release-readiness input and must not be embedded in the APK. The workflow verifies both properties and rejects runtime source references to a client secret.

The Android package is always:

`com.keyserdsoze.dicethrower`

Drive authorization is separate from sign-in and requests only:

`https://www.googleapis.com/auth/drive.appdata`

Remote files are created and listed only in Drive `appDataFolder`.

## Google console prerequisites

Before the device test:

1. Google Drive API is enabled for the production Google Cloud project.
2. The OAuth consent/branding configuration is published or otherwise available to the intended tester audience.
3. The Web OAuth client used by `DICETHROWER_GOOGLE_WEB_CLIENT_ID` belongs to the same production project.
4. The Android OAuth client uses package `com.keyserdsoze.dicethrower` and the SHA-1 of the certificate that signs the APK being tested.
5. If the build is installed from Google Play, also configure the Google Play App Signing SHA-1 where required; it can differ from the upload-key SHA-1.

## End-to-end device checklist

Use a clean install or clear app data before the first pass.

- [ ] Launch succeeds without a Google account and **Use standalone** enters the app.
- [ ] **Continue with Google** opens the Google account chooser and completes sign-in.
- [ ] Drive authorization requests only Dice Thrower app-data access.
- [ ] After connection, Settings reports Google connected and Drive app-data authorized.
- [ ] Create or edit a character locally; local save succeeds immediately.
- [ ] **Sync now** completes successfully.
- [ ] Re-launch the app; the connected state is restored without persisting raw credentials/tokens.
- [ ] On a second installation/device (or after a controlled local reset), connect the same Google account and verify the first-sync reconciliation restores the expected character data.
- [ ] Create divergent local/remote edits and verify conflict handling does not silently overwrite either side.
- [ ] **Disconnect Google** returns to standalone while preserving local data and leaving remote data intact.
- [ ] Reconnect and verify the remote snapshot is still available.
- [ ] **Delete Dice Thrower cloud data** removes only Dice Thrower-managed appDataFolder files, keeps the local copy, and returns to standalone.
- [ ] Reconnect after cloud deletion and verify deleted cloud state is not silently recreated before the explicit reconciliation/sync path.

## Acceptance evidence

Record in issue #29:

- tested app version/tag and commit;
- installation source (GitHub signed APK or Google Play Internal);
- signing SHA-1 used by the Android OAuth client;
- device/Android version;
- account-flow result;
- sync/reconciliation result;
- disconnect and cloud-delete result.

Do not paste OAuth client secrets, access tokens, ID tokens, passwords, keystore material, or other private credentials into GitHub issues or logs.
