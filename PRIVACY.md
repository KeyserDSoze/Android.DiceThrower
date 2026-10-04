# Privacy Policy — Dice Thrower

_Last updated: 4 October 2026_

Dice Thrower is developed and published by **Alessandro Rapiti**.

Canonical public web version:

https://keyserdsoze.github.io/Android.DiceThrower/privacy/

## Data collection

Dice Thrower does **not** require an account and does not sell, profile, or use personal data for advertising or analytics. Standalone mode remains fully functional without signing in.

## Local app data

Character names, optional character images selected by the user, free-form tags, dice-roll definitions, dashboard organization, settings, roll history, and synchronization revision metadata are stored locally on the device. Selected character images are copied into the app's private storage so they can be included in user-requested backups; the app stores their MIME type, size and cryptographic content hash for integrity/deduplication. The app also creates a random, non-personal installation writer ID used only to mark local revisions; that installation ID is stored in Android's private no-backup area so it is not restored by Android Auto Backup.

## Optional Google connection

If the user explicitly chooses **Continue with Google**, Dice Thrower uses Google Identity through Android Credential Manager. The app keeps only the Google account's stable identifier, email address and optional display name needed to show connection state. This small record is stored in Android's private no-backup area. Dice Thrower does not store the Google password, ID token, Drive access token or raw credentials.

Google Drive authorization is requested separately and is limited to the private `drive.appdata` scope. When Google is connected, Dice Thrower synchronizes character data, portable character images, revision/deletion metadata and selected roaming preferences through Drive's hidden app-specific data folder on app resume and when the user chooses **Sync now**. Theme, shake and animation preferences remain local to each device. The account can be disconnected later; disconnecting revokes this app-data authorization but does not delete local or already-synchronized remote Dice Thrower data.

## Network access

The application requests Internet access so an explicitly chosen Google sign-in/authorization flow and the optional Drive synchronization can contact Google services. Core Dice Thrower features remain offline-first and standalone mode does not require a network connection. The app has no advertising, analytics or tracking backend.

## Sensors

Dice Thrower can use the device accelerometer to detect a deliberate shake and trigger a dice roll. Accelerometer samples are processed locally in memory and are not recorded or transmitted.

## Website privacy

The official website has no advertising, analytics, tracking pixels, account system, or contact form. It stores only selected theme and language in browser local storage. GitHub Pages may process normal connection and security logs under GitHub's own privacy terms.

## Contact

https://keyserdsoze.github.io/Android.DiceThrower/contact/

or:

https://github.com/KeyserDSoze/Android.DiceThrower/issues
