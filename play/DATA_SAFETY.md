# Google Play Data Safety — Dice Thrower

This file records the intended answers for the first local-only release and must be re-reviewed before every store submission.

## Data collection and sharing

- Data collected by the developer: **No**
- Data shared with third parties: **No**
- Advertising: **No**
- Analytics: **No**
- Account creation: **No**
- Internet permission in the Android manifest: **No**

## Data handled locally

The app stores the following only on the user's device:

- character names and levels;
- optional character image references selected through Android's Storage Access Framework;
- free-form character tags;
- locally defined character modifiers;
- dice-roll names, parameterized expressions and level-scaling rules;
- dashboard grouping and ordering;
- app settings;
- dice-roll history.

## Sensors

The accelerometer is used only while the roll screen is active when shake-to-roll is enabled. Sensor samples are processed in memory to detect a deliberate shake and are not persisted or transmitted.

## Deletion

Users can delete individual rolls and groups and clear roll history in the app. Uninstalling the application or clearing app storage removes the application's local data, subject to Android backup/restore behavior.

## Re-review triggers

Review this document and the Play Console Data Safety form before shipping any change that adds:

- Internet access;
- crash reporting or analytics;
- advertising;
- cloud sync;
- accounts;
- external SDKs;
- sharing/export;
- backup behavior changes.
