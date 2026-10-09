# Google Play Data Safety — Dice Thrower

This file records the intended Data Safety posture and must be re-reviewed before every store submission, especially now that optional Google account connectivity exists.

## Data collection and sharing

- Data collected by a Dice Thrower-operated backend: **No**
- Optional account data handled through Google Identity: **Yes — review against the current Play Console definitions before release**
- Advertising: **No**
- Analytics: **No**
- Dice Thrower account creation: **No**; optional existing Google account connection is supported
- Internet permission in the Android manifest: **Yes**, for user-initiated Google identity/authorization and optional Drive sync

## Local primary copy

The app always stores its primary working copy on the user's device. In standalone it remains only local; with optional Google sync enabled, the character/portable-image/revision data and the roaming preference subset described below are also copied to the user's Drive `appDataFolder`:

- character names and levels;
- optional character images selected through Android's Storage Access Framework and copied into private app storage;
- free-form character tags;
- locally defined character modifiers;
- dice-roll names, parameterized expressions, level-scaling rules, and optional user-defined Bonus/Malus Effects (activation thresholds, actions and target Part references);
- dashboard grouping and ordering;
- app settings;
- dice-roll history, including optional per-roll effect evaluation traces (original/final Part totals, conditions and generated dice details).
- synchronization revision metadata and a random, non-personal local installation writer ID.
- when Google is connected: stable Google account ID, email, optional display name and local connection/reconciliation flags in Android no-backup storage; no password or OAuth/ID token is persisted.

## User-directed backup and restore

Dice Thrower can export a versioned JSON backup through Android's Storage Access Framework. The user explicitly chooses the destination using the Android document picker. Dice Thrower does not upload the backup, choose a cloud provider, or receive a copy.

Restore is also user-initiated through the Android document picker. Backup format v2 embeds referenced character images and validates their SHA-256 hashes locally before replacing app data, making those images portable to another device. Legacy v1 URI references remain readable when the source document permission is still available.

This user-directed file operation does not itself perform a developer-operated data transfer or use the optional Google connection.

## Optional Google account and Drive authorization

Standalone remains a first-class mode. If the user explicitly connects Google, authentication uses Android Credential Manager and Drive authorization is requested separately through Google Play services with only `drive.appdata`. The app then syncs character graphs, portable images, revision/tombstone metadata and the deliberately roaming roll-button/log-retention preferences in Drive's hidden `appDataFolder` on resume and on explicit **Sync now**. Theme, shake, animation and conflict-policy preferences remain device-local. Disconnect revokes authorization while preserving the complete local snapshot and does not itself delete remote data. Revoked authorization falls back safely to standalone without changing local data.

Settings exposes a separate, confirmed **Delete Dice Thrower cloud data** action. It deletes only Dice Thrower-managed files from Drive `appDataFolder`, preserves local data, switches locally to standalone before any future sync can recreate those files, and then revokes the Drive grant. This is the explicit remote-data deletion path and is not performed by ordinary disconnect.

## Sensors

The accelerometer is used only while the roll screen is active when shake-to-roll is enabled. Sensor samples are processed in memory to detect a deliberate shake and are not persisted or transmitted.

## Deletion

Users can delete individual rolls and groups and clear roll history in the app. Character-level cascade deletion is implemented in the data layer. Uninstalling the application or clearing app storage removes the application's local data, subject to Android backup/restore behavior.

Users who enabled optional Google sync can explicitly delete all Dice Thrower cloud data from Settings as described above. The operation is retry-safe and is scoped to this application's managed `appDataFolder` files.

## Re-review triggers

Review this document and the Play Console Data Safety form before shipping any change that adds:

- Internet access;
- crash reporting or analytics;
- advertising;
- developer-operated cloud sync;
- accounts;
- external SDKs;
- automatic third-party sharing;
- changes that cause exported files to be transmitted without an explicit user-selected destination;
- Android backup behavior changes.
