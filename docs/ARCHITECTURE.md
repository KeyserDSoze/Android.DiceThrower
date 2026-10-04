# Dice Thrower architecture

## Principles

- Game agnostic: no D&D-specific concepts in the domain model.
- Offline-first: no network dependency for core functionality.
- Local ownership: character data and logs remain on-device.
- UI/runtime separation: dice parsing and rolling are independent from Compose and from any future 3D renderer.
- Explicit ordering: groups and rolls persist an `order` field.
- Stable identifiers: entities use UUID strings.
- Parameterized rolls are resolved at runtime; leveling a character does not rewrite stored roll definitions.

## Domain model

`CharacterProfile`: id, name, portable `CharacterImageRef` (plus legacy `imageUri` migration source), tag, level, order, defaultDiceStyleId.

`CharacterModifier`: id, characterId, name, integer value, order.

`RollGroup`: id, characterId, name, order.

`RollDefinition`: id, characterId, name, base expression, groupId, enabled, order, levelRules, diceAppearance.

`DiceStyle`: character-owned visual style with material, primary/secondary colors and stable ordering. Roll appearance policies reference style IDs and never participate in dice math.

`RollLevelRule`: id, kind, trigger, expression.

`RollLog`: id, characterId, rollDefinitionId, rollName, resolved expression, total, detail, timestamp.

`AppSettings`: theme, shake, animations, roll-button visibility/position, log retention.

## Character variables

Every character exposes the built-in variable:

```
{level}
```

Users can define arbitrary integer modifiers, for example:

```
Intelligence = 4
Strength = -2
Spell Power = 7
```

They can then be referenced by name:

```
1d20+{Intelligence}
2d6+{Spell Power}
1d20+{Strength}+{level}
```

Names are resolved case-insensitively. `level` is reserved and modifier names must be unique within one character.

Modifier values can be negative. Sign normalization happens before dice parsing, so `1d20+{Strength}` with `Strength = -2` becomes `1d20-2`.

## Level scaling

A roll can contain zero or more additive level rules.

### From level

`FROM_LEVEL, trigger=4, expression=1d6`

At levels 1–3 nothing is added. From level 4 onward, `1d6` is added once.

Multiple threshold rules can model milestones such as level 4, 10 and 20.

### Every N levels

`EVERY_LEVELS, trigger=2, expression=1d6+2`

The expression is repeated once at level 2, twice at level 4, three times at level 6, and so on:

```
repeatCount = floor(characterLevel / trigger)
```

Rules may also reference character modifiers and `{level}`.

## Runtime resolution

The stored roll remains parametric. For every throw:

1. resolve `{level}` and named modifiers;
2. determine which level rules apply;
3. expand repeated rules;
4. normalize arithmetic signs;
5. validate the final dice expression;
6. generate the numerical result;
7. independently resolve a visual style for each stable die slot;
8. publish the immutable result + resolved appearances to the visual layer;
9. store the fully resolved expression in the roll log.

Level-up therefore affects every parameterized roll immediately without mutating the roll definitions.

## Dice expressions

After parameter resolution, the grammar is:

```
expression := term (("+" | "-") term)*
term       := dice | integer
dice       := [count] "d" sides
```

Supported sides: 2, 3, 4, 6, 10, 12, 20, 100.

## Persistence and migration

The application stores one versioned JSON document in private SharedPreferences. Storage version 5 adds portable character-image references on top of version 4's per-character sync metadata. Version 3 introduced the character-owned dice-style model and roll appearance policies; version 2 added character level, modifiers and level rules.

Version-1 data remains readable:

- missing character level defaults to 1;
- missing modifiers default to an empty list;
- missing roll level rules default to an empty list.

Version-1/2 data also receives safe dice-appearance defaults when read: missing style collections are empty and rolls use the deterministic built-in glossy-resin fallback until a character style is configured.

Version-1/2/3 data has no sync metadata. `LocalStore` migrates it lazily on first read by computing the current character revision, assigning a local `updatedAt` and recording the installation writer ID. The existing preference key remains unchanged.

Version-1 through version-4 characters may still contain a document `imageUri`. On load, the image layer attempts a lazy import while that URI remains readable. A successful import writes an app-owned asset and clears the URI; a failed import leaves the legacy character untouched so the UI can still attempt the old URI or fall back to initials.

## Portable character images

New character images are copied immediately into private `filesDir/character-images`. `CharacterImageRef` stores a deterministic `img_<sha256>` asset ID, SHA-256 content identity, normalized image MIME type and byte size; imports are capped at 10 MiB. Content-derived IDs deduplicate identical bytes without relying on source-device URIs.

Reads verify size and hash before returning bytes. Missing or corrupt files therefore produce the normal avatar fallback instead of invalidating the character. Unreferenced local files are retained for seven days before deletion, which makes cancelled edits/deletes recoverable from short-lived state while still bounding orphan storage. The same portable asset envelope is provider-neutral: the Drive `appDataFolder` repository uploads/downloads the exact validated bytes rather than any `content://` URI.

Manual backup format v2 embeds only referenced assets as Base64 and validates every character reference against asset ID, size and SHA-256 during encode/decode. Both backup v2 and the provider-neutral sync serializer explicitly omit legacy document URIs. Format v1 remains accepted for backward compatibility. Restoring v2 writes verified assets before committing character data.

## Sync metadata foundation

Each character owns a `CharacterSyncMetadata` record covering the complete character graph (profile including portable image identity, modifiers, groups, rolls/rules/appearance, dice styles and roll log). `CharacterRevision` canonicalizes that graph with explicit length-prefixed fields and stable ID/key ordering, then hashes UTF-8 bytes with SHA-256. Metadata itself is excluded from the content hash.

`LocalStore` is the stamping boundary. Unchanged content preserves revision, `updatedAt` and last-writer identity; changed content receives a new revision, the current installation writer ID and a monotonic local `updatedAt`. The random installation writer ID lives in Android's private `noBackupFilesDir`, survives app restarts, is excluded from Android Auto Backup and is reset with the installation. Manual backups preserve character revision/base metadata but do not replace the destination installation's own writer identity.

`baseRevision` is the last known common remote revision. Conflict classification compares local and remote revisions against that base: equal content is `SAME`; one-sided divergence is `LOCAL_ONLY` or `REMOTE_ONLY`; divergence on both sides is `CONFLICT`. `updatedAt` is deliberately not required to establish a conflict and remains available for later user-facing/latest-wins policy.

If the model grows substantially, `LocalStore` is the boundary to replace, for example with Room.

## Optional Google account boundary

First launch has an explicit `UNDECIDED` account state and cannot enter the normal app shell until the user chooses standalone or starts the Google flow. Canceling that first Google flow transitions safely to `STANDALONE`; standalone never removes or disables local features and Settings always offers a later connection path.

Authentication and Drive authorization are deliberately separate. `GoogleAccountCoordinator` uses Android Credential Manager + Sign in with Google only to identify the account for local UI/session state, then Google Play services `AuthorizationClient` binds that same account and requests exactly `https://www.googleapis.com/auth/drive.appdata`. The local ID-token result is not treated as a server-side security boundary; Drive access is gated by Google's authorization result. A successful connection stores only stable account ID, email, optional display name and authorization/reconciliation flags. ID/access tokens, Google passwords and raw credentials are never persisted.

`CloudAccountStore` writes that small session/UI record under `noBackupFilesDir`; it is neither Android Auto Backup state nor part of Dice Thrower manual backups. Disconnect revokes the `drive.appdata` grant and clears the Credential Manager session before switching locally to standalone. It never calls `LocalStore`, so disconnect cannot delete the local snapshot.

Every new Google connection starts with `initialReconciliationPending = true`. The sync engine (#10) must clear it only after it has compared existing local and remote state; this prevents first connection from silently treating either side as authoritative.

## Google Drive app-data repository

Remote persistence uses Google Drive API v3 directly and is confined to the hidden `appDataFolder`; it does not create or discover user-visible Drive folders. `CloudRepositoryFactory` wraps the Drive implementation in `GatedCloudRemoteRepository`, which rejects every operation before token acquisition or transport access unless the saved account state is a fully authorized Google connection. Standalone therefore has no Drive I/O path.

Cloud schema v1 deliberately avoids a whole-database remote blob. It stores one manifest, one JSON document per character graph and independent portable image assets. Character Drive `appProperties` repeat the schema version, stable character ID, `updatedAt`, canonical SHA-256 revision and last-writer installation ID so the sync engine can enumerate and classify remote state before downloading every document. Image files carry their content ID, SHA-256, MIME type and byte length and are revalidated after download.

Logical keys are stable and repository upserts update an existing Drive file. `files.create` is not blindly retried because it has no client idempotency key: after an ambiguous transient create failure the repository lists the stable logical key, adopts the committed file if present, then performs an idempotent update. Reads, updates and deletes use bounded exponential backoff for transient network/429/5xx failures. Authentication/authorization failures, schema mismatches, not-found state and transient transport failures remain distinct exceptions for the sync engine and UI to handle intentionally.

The Drive access token is requested on demand from Google Play services `AuthorizationClient` for the connected account and is used only in the HTTP `Authorization` header; it is never written to app storage. The repository exists independently of sync policy: #10 owns reconciliation/conflict decisions and is the first layer that will invoke it from the running app.

## Shake handling

The accelerometer listener is active only while the roll screen is visible. A debounce window avoids duplicate throws from one physical gesture.

## 3D renderer

The OpenGL ES 2.0 renderer consumes an already-produced numerical result plus the fully resolved appearance for each visible die. Physics/animation never decides the numerical result: the dice engine decides first, then the UI resolves appearance, then the renderer visualizes both.

Renderer material profiles map the domain-level `GLOSSY_RESIN`, `MATTE_RESIN`, `METAL` and `GEMSTONE` values to lightweight shader parameters for ambient/diffuse/specular response, rim accents and gemstone inner glow. Both primary and secondary style colors feed the shader. Renderer fallback is deterministic and matches `DiceAppearanceResolver` defaults if a visual slot is unexpectedly missing.

Appearance randomization uses a random source separate from the one passed to `DiceExpression.evaluate`, so choosing or randomizing a visual style cannot consume or alter dice-result RNG state.

## Dice style editor

Character Edit mode exposes the character-owned `DiceStyle` collection without coupling the OpenGL renderer to persistence. `DiceStyleDataOperations` owns reference-safe create/update/duplicate/reorder/default/delete operations, while Compose edits domain values and persists the returned `AppData` through the existing screen boundary.

The editor's d20 preview creates a visual-only `DiceRollVisualEvent` from the unsaved style values; it does not invoke `DiceExpression` and therefore cannot roll or consume numerical RNG. Deleting an in-use style requires explicit confirmation and then reuses the same cleanup rules as storage-level deletion so default and roll references remain valid.

Cross-character style copy is value-based, never reference-based: selected source styles receive fresh IDs and destination ownership, name collisions are resolved locally, and the source default is mapped only when the user explicitly requests it. Full character duplication applies the same invariant to the whole style graph by remapping the character default and every roll appearance reference to newly duplicated style IDs.

Roll appearance assignment is edited independently from roll math. `DiceAppearanceResolver.slotsFor` derives stable `componentIndex:dieIndex` keys from the parsed expression shape without evaluating it. The UI supports character-default, uniform, per-die, random-uniform and random-per-die policies; random policies may restrict selection to an explicit character-owned style pool. Saving a changed expression reconciles per-die references against the new resolved slot set, preserving compatible keys and dropping obsolete ones. Newly introduced/unassigned slots deterministically fall back to the character default.

## Localization

The locale registry mirrors Android.ScreenLock's 40 languages. English and Italian are the reference translations while the UI vocabulary is still evolving.

## Website

`src/overviewapp` is a React/Vite static site with localized copy, light/dark theme, privacy, terms and contact pages.
