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

`CharacterProfile`: id, name, portable `CharacterImageRef` (plus legacy `imageUri` migration source), tag, level, order, defaultDiceStyleId, dice-table theme and optional portable dice-table image.

`CharacterModifier`: id, characterId, name, integer value, order.

`RollGroup`: id, characterId, name, order.

`RollDefinition`: id, characterId, name, canonical expression, groupId, enabled, order, levelRules, optional ordered subgroups, diceAppearance.

`RollSubgroup`: stable id, optional name, parametric expression and additive/subtractive operator. Subgroups are builder/statistics metadata; the canonical `RollDefinition.expression` remains the portable mathematical representation and legacy rolls keep an empty subgroup list.

`DiceStyle`: custom shared visual style (legacy records may retain a character owner until migration) with material, primary/secondary colors and stable ordering. Roll appearance policies reference style IDs and never participate in dice math. `CharacterProfile.diceTableTheme` selects the persisted per-character roll table and `diceTableImage` can override the felt with a portable custom image.

`RollLevelRule`: id, kind, trigger, expression.

`RollLog`: id, characterId, rollDefinitionId, rollName, resolved expression, total, detail, timestamp.

`AppSettings`: theme, animations, first-roll and reroll gesture triggers (tap/swipe/shake), legacy roll-button fields kept for backward compatibility, log retention and conflict policy. Interaction gestures are device-local; the footer dice action is always available in the V2 table.

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

Input gestures never participate in dice math: every enabled trigger calls the same debounced throw request, and the logical result is still generated before the renderer receives the immutable visual event.

Level-up therefore affects every parameterized roll immediately without mutating the roll definitions.

## Dice expressions

After parameter resolution, the grammar is linear arithmetic with explicit precedence:

```
expression := product (("+" | "-") product)*
product    := unary ("*" unary)*
unary      := ("+" | "-") unary | primary
primary    := dice | integer | "(" expression ")"
dice       := [count] "d" sides
```

Both `x` and `×` (as well as `*`) express multiplication, including in the guided builder. Adjacent numeric variables such as `2{level}` are migrated at template-resolution time to `2x{level}` rather than being concatenated into `24`; `{level}d6` still means a variable dice count. Multiplication is intentionally scalar: at least one operand must be dice-free. This keeps component statistics and mathematical expectation exact while supporting formulas such as `(1d6+2)*3` and `{level}*1d6`; `1d6*1d8` is rejected.

Supported sides: 2, 3, 4, 6, 8, 10, 12, 20, 100.

## Persistence and migration

The application stores one versioned JSON document in private SharedPreferences. Storage version 10 adds optional named multipart results to roll logs (missing parts decode as empty). Storage version 9 adds optional dice-style overrides keyed by roll subgroup; version 8 adds an optional portable custom dice-table image; version 7 adds optional roll subgroups while preserving the canonical expression; version 6 added the per-character dice-table theme; version 5 added portable character-image references on top of version 4's per-character sync metadata. Version 3 introduced the character-owned dice-style model and roll appearance policies; version 2 added character level, modifiers and level rules.

Version-1 data remains readable:

- missing character level defaults to 1;
- missing modifiers default to an empty list;
- missing roll level rules default to an empty list.
- missing roll subgroups default to an empty list, so every legacy expression remains valid without migration rewriting.

Version-1/2 data also receives safe dice-appearance defaults when read: missing style collections are empty and rolls use the deterministic built-in glossy-resin fallback until a character style is configured.

Version-1/2/3 data has no sync metadata. `LocalStore` migrates it lazily on first read by computing the current character revision, assigning a local `updatedAt` and recording the installation writer ID. The existing preference key remains unchanged.

Version-1 through version-4 characters may still contain a document `imageUri`. On load, the image layer attempts a lazy import while that URI remains readable. A successful import writes an app-owned asset and clears the URI; a failed import leaves the legacy character untouched so the UI can still attempt the old URI or fall back to initials.

## Portable images

New character images are copied immediately into private `filesDir/character-images`. `CharacterImageRef` stores a deterministic `img_<sha256>` asset ID, SHA-256 content identity, normalized image MIME type and byte size; imports are capped at 10 MiB. Content-derived IDs deduplicate identical bytes without relying on source-device URIs.

Reads verify size and hash before returning bytes. Missing or corrupt files therefore produce the normal avatar/table fallback instead of invalidating the character. The same asset envelope backs both character portraits and custom dice-table images. Unreferenced local files are retained for seven days before deletion, which makes cancelled edits/deletes recoverable from short-lived state while still bounding orphan storage. The provider-neutral Drive `appDataFolder` repository uploads/downloads the exact validated bytes rather than any `content://` URI.

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

Remote-data deletion is intentionally a different lifecycle action from disconnect. After explicit destructive confirmation, the Drive repository enumerates only files carrying Dice Thrower's `dt_app=dice_thrower` ownership marker and deletes that managed set idempotently. The app then switches locally to standalone before revoking the grant, preventing the just-deleted dataset from being recreated by an automatic resume sync. No `LocalStore` mutation occurs. If Drive authorization is revoked outside Dice Thrower, an authorization-class sync failure also switches locally to standalone and exposes the normal reconnect path without blocking startup.

Every new Google connection starts with `initialReconciliationPending = true`. The sync engine clears it only after a comparison completes without unresolved conflicts or concurrently skipped local writes; this prevents first connection from silently treating either side as authoritative.

## Google Drive app-data repository

Remote persistence uses Google Drive API v3 directly and is confined to the hidden `appDataFolder`; it does not create or discover user-visible Drive folders. `CloudRepositoryFactory` wraps the Drive implementation in `GatedCloudRemoteRepository`, which rejects every operation before token acquisition or transport access unless the saved account state is a fully authorized Google connection. Standalone therefore has no Drive I/O path.

Cloud schema v1 deliberately avoids a whole-database remote blob. It stores one manifest, one JSON document per character graph and independent portable image assets. Character Drive `appProperties` repeat the schema version, stable character ID, `updatedAt`, canonical SHA-256 revision and last-writer installation ID so the sync engine can enumerate and classify remote state before downloading every document. Image files carry their content ID, SHA-256, MIME type and byte length and are revalidated after download.

Logical keys are stable and repository upserts update an existing Drive file. `files.create` is not blindly retried because it has no client idempotency key: after an ambiguous transient create failure the repository lists the stable logical key, adopts the committed file if present, then performs an idempotent update. Reads, updates and deletes use bounded exponential backoff for transient network/429/5xx failures. Authentication/authorization failures, schema mismatches, not-found state and transient transport failures remain distinct exceptions for the sync engine and UI to handle intentionally.

The Drive access token is requested on demand from Google Play services `AuthorizationClient` for the connected account and is used only in the HTTP `Authorization` header; it is never written to app storage.

## Offline-first sync engine

`CloudSyncEngine` runs on app resume and explicit **Sync now** only for a connected Google state. UI writes never wait for Drive: `LocalStore` stamps and persists them immediately, while unsynced character revisions remain distinguishable because `baseRevision != revision`. Network/auth/schema failures become observable sync status and leave the local snapshot usable.

Each pass enumerates remote metadata before transferring documents. Equal revisions transfer nothing; one-sided divergence uploads or downloads one character graph; two-sided divergence is a real conflict only after both revisions are shown to differ from their last common `baseRevision`. Downloads are committed locally only after all required remote work succeeds. The commit boundary rechecks the expected local revision, so an edit performed while a sync is in flight is never replaced by an older downloaded snapshot. An already-uploaded revision may advance only the known base of a same-device descendant, leaving that newer edit pending for the next pass.

Conflict handling sits above that revision proof. The device-local policy defaults to **Ask on conflict** and presents the character, local/Drive update times and a concise category diff before accepting **Keep this device** or **Use Drive version**. Resolution writes or downloads the selected graph and advances the common base, so the same conflict is not raised again. Delete-vs-edit conflicts use the same explicit choice. Roaming-settings divergence can also be resolved without changing device-local preferences.

The optional **Always use latest** policy is applied only after a real conflict has been established; timestamps never replace revision/base conflict detection. It compares `updatedAt` first, then canonical revision and writer/device ID as deterministic tie-breakers. Automatic resolutions are reported back through sync status as a non-blocking note. The policy itself stays device-local and is intentionally excluded from `RoamingSettings`.

Local deletions are recorded in the private no-backup sync journal before they are forgotten. A remote tombstone carries the last accepted remote revision: an unchanged device can apply the deletion, while delete-vs-edit becomes a conflict. Tombstones are retained in the manifest to prevent an offline stale device from resurrecting a deleted character. The journal also stores roaming-settings revision/base metadata and the timestamp of the last successful pass.

Roaming settings are intentionally narrow: roll-button visibility, roll-button position and log retention sync independently from character documents. Theme, shake enablement and animation/performance preference remain device-local. A settings divergence with no common base is surfaced rather than silently choosing a side.

## Shake handling

The accelerometer listener is active on a fresh roll screen until the first throw. A debounce window avoids duplicate throws from one physical gesture; after that first throw, the listener stays disarmed and rerolls require the explicit **Roll again** action.

## 3D renderer

The OpenGL ES 2.0 renderer consumes an already-produced numerical result plus the fully resolved appearance for each visible die. Its lightweight rigid-body layer handles table boundaries, pairwise die collisions, damping and settling only after the logical result exists. Physics/animation never decides the numerical result: the dice engine decides first, then the UI resolves appearance, then the renderer visualizes both. The table background is a separate visual quad sized from the camera viewport with a small overscan, while `DiceTableViewport.HALF_WIDTH/HALF_HEIGHT` remain the physical collision bounds. This lets artwork reach every screen edge without changing where dice can move. Bundled and custom table images are center-cropped against the actual screen aspect ratio so portrait or landscape sources are never stretched.

Face numerals are generated as small triangle meshes anchored to each logical die face, so they share the die model transform and remain readable while the die rotates. The renderer records each numbered face normal and progressively aligns the face selected by the already-resolved logical value toward the camera during the final physical settling window. The portrait viewport is deliberately tall and the camera is fitted from the physical table bounds/aspect ratio, while the background quad independently fills the whole screen. `CharacterProfile.diceTableTheme` keeps its original enum identifiers for backup/sync compatibility but now selects one of four bundled artwork presets plus six seeded native-resolution procedurally painted presets (1080×1920 full table, 540×960 preview; visual seeds are independent of dice RNG); `diceTableImage` remains an optional portable custom override. Only custom images participate as portable image assets in backup/revision/cloud sync; bundled presets are app resources referenced by the stable theme id. Roll navigation, statistics and reroll controls are a bottom Compose overlay rather than a separate header. Android system Back uses the same explicit route policy as the visible back control, so only the character-list root delegates Back to the Activity/system. Once physics settles, a single-part roll reveals its total (and can celebrate above-average results). A roll with multiple named parts reveals an independent labelled result for each part without an unrelated aggregate; the statistics sheet and new log entries also display each part separately. Legacy numeric log totals remain stored for backwards compatibility and old records without parts keep their old presentation. The selected table in the character editor is summarized in a collapsed accordion. Route-specific saved Compose state restores scrolling after returning from rolls and groups.

Renderer material profiles map the domain-level `GLOSSY_RESIN`, `MATTE_RESIN`, `METAL` and `GEMSTONE` values to lightweight shader parameters for ambient/diffuse/specular response, rim accents and gemstone inner glow. Both primary and secondary style colors feed the shader. Renderer fallback is deterministic and matches `DiceAppearanceResolver` defaults if a visual slot is unexpectedly missing.

Appearance randomization uses a random source separate from the one passed to `DiceExpression.evaluate`, so choosing or randomizing a visual style cannot consume or alter dice-result RNG state.

## Guided Effect form (#148)

`isGuidedEffectRule` is a pure eligibility predicate: one activation group, one PART-based numeric condition, one arithmetic action with numeric operand, existing source and target Parts. Compose offers a WHEN/THEN editor without changing stored `RollEffect` or group/action identifiers. If the predicate fails, the full advanced editor renders unconditionally, preserving preexisting OR/AND groups, chaining, formulas, REROLL and ROLL_AFTER. The `Advanced` toggle only changes UI presentation. The actual domain executor, numeric action scopes, JSON storage, backup and Drive revision semantics remain unchanged.

## Conditional support-only Roll Parts (#154)

`RollSubgroup.includeInNormalRoll` defaults to true for old JSON. False saves the Part definition for Effects without appending its expression to the canonical initial Roll expression. `RollFormulaResolver` keeps `subgroups` (normal dice) separate from `supportSubgroups` (resolved but dormant). DoubleRollEngine samples only normal Parts, so support-only dice are never sampled or compared in BEST/WORST until a logical Effect activates. `EffectRollSnapshot` includes zero-valued support-only Part IDs, which allows `EffectSequenceExecutor` REROLL/ROLL_AFTER to use their expressions as action targets; `EffectRuntimeHistory` and the results overlay show only those changed by an activated Effect. The renderer consumes pre-decided faces only. A subtracting dormant Part contributes negative dice when its default expression is used. Normal Part count must remain at least one.

`AppDataJsonCodec` defaults the flag to true when missing; the field is included in JSON/backup/Drive and `CharacterRevision` only when false, preserving earlier unchanged hashes. Deleting/reordering Part IDs remains safe through existing effect reference checks and validation. The original initial throw never silently includes dormant dice; none of this changes user account requirements or app data collection.

## Critical-hit quick template (#140 / EPIC #139)

`EffectEditorDraft.criticalHit` creates a normal persisted `RollEffect` with stable IDs, `PART` trigger on the first subgroup (`DICE_ONLY == 20`) and a `MULTIPLY ×2` action on the second subgroup using `DICE_ONLY`. Modifiers remain unmodified and the execution path stays `EffectSequenceExecutor` → `EffectActionEngine`, never the renderer. No schema migration or separate effects executor is needed. The UI offers the template plus WHEN/THEN headings; existing advanced groups/actions remain editable and serializable. Limitation: `DICE_ONLY` is the dice subtotal of the Part, not a predicate on one die face within a multi-die Part.

## Dice gallery and Roll builder UX (#156)

The home dice customizer displays the ten immutable presets in a padded one-column gallery, with a single enlarged 3D preview and separate reusable custom styles. Creating or duplicating a custom style changes only the global catalog; character/Part assignments happen in their respective editors.

## Immutable dice appearance presets (EPIC #138, slice #141)

`DiceStylePresets` is a pure-domain catalog of ten immutable palette/material definitions with stable `preset:<key>` IDs. System styles can be assigned directly to any character or Roll Part; copying a system preset into a custom uses `SharedDiceStyleCatalog.create` and produces a new globally shared ID.

**Global custom style sharing:** `SharedDiceStyleCatalog` holds global user-created styles and their CRUD operations. Character and Roll assignments reference IDs, not copies. Legacy character-owned records are migrated on load/restore without modifying IDs; deleting a global style in use atomically reassigns all references.

## Combined candidate/Part styling (#146)

The appearance resolver first derives an individual Part/per-die style, then handles candidate B. When a Part-specific or RANDOM_PER_DIE identity exists, a deterministic per-channel mix keeps the Part primary color while applying B's material and secondary accent. Otherwise candidate B preserves the explicit character B style (or automatic contrasting fallback). No dice engine RNG or schema data is changed. Tests cover two Parts independently in A/B, fixed B and automatic B. Shared user-created global style IDs are still planned in #144.

## Dice style editor

Character Edit mode uses a closed-by-default style accordion for default and candidate B assignments. Home Customize dice edits global custom definitions; Roll Parts select immutable system presets or the same shared custom IDs. Rendering and numerical RNG stay separate.

The editor's d20 preview creates a visual-only `DiceRollVisualEvent` from the unsaved style values; it does not invoke `DiceExpression` and therefore cannot roll or consume numerical RNG. Deleting an in-use style requires explicit confirmation and then reuses the same cleanup rules as storage-level deletion so default and roll references remain valid.

Cross-character style copy is value-based, never reference-based: selected source styles receive fresh IDs and destination ownership, name collisions are resolved locally, and the source default is mapped only when the user explicitly requests it. Full character duplication applies the same invariant to the whole style graph by remapping the character default and every roll appearance reference to newly duplicated style IDs.

Roll appearance assignment is edited independently from roll math. `DiceAppearanceResolver.slotsFor` derives stable `componentIndex:dieIndex` keys from the parsed expression shape without evaluating it, while resolved subgroup metadata maps component indexes back to stable subgroup IDs. The UI supports character-default, uniform, per-die, random-uniform and random-per-die policies; in per-die mode a subgroup style is the fallback for its dice and an explicit single-die slot override has higher precedence. Random policies may restrict selection to an explicit character-owned style pool. Saving a changed expression reconciles both subgroup and per-die references, preserving compatible keys and dropping obsolete ones. Newly introduced/unassigned slots deterministically fall back to the character default.

## Localization

The locale registry mirrors Android.ScreenLock's 40 languages. English and Italian are the reference translations while the UI vocabulary is still evolving.

## Website

`src/overviewapp` is a React/Vite static site with localized copy, light/dark theme, privacy, terms and contact pages.


### Scoped double rolls (Effects Engine groundwork, #107)

A Roll may enable `doubleRollEnabled`; each stable-ID Roll Part carries `includeInDoubleRoll`. On creation only the first Part participates by default, while legacy Rolls remain in normal mode. The `DoubleRollEngine` samples the baseline once and samples each participating Part again only in BEST/WORST mode. It compares the **sum of complete participating Part totals** across both candidate groups and selects the entire best/worst group, without independently selecting individual dice. Unselected Parts and level-rule dice remain single-sampled. Subtractive Parts respect their operator in group comparison.

The engine returns the chosen numerical result, the two Part candidates, the selected comparison sum and all visual dice independently. `Dice3DScene` reads preselected faces and receives component indices to dim; renderer physics never affects RNG. UI gestures are normal/up, best/right and worst/left when the global directional double-roll swipe preference is on. BEST/WORST footer controls remain available even with directional gestures off; NORMAL stays at the rightmost position. Candidate breakdowns persist in `RollLog` / `RollLogPart` via versioned JSON and are included in backup/restore and optional cloud sync. Older data omits these fields and decodes safely.


### Effects domain model and versioned persistence (#103)

`RollDefinition.effects` is a list of independent typed rules. Each `RollEffect` declares `BONUS` or `MALUS`, an explicit order, an optional stop-following flag, OR-joined activation groups containing AND-joined conditions, and a sequence of typed actions. Condition sources are PART, ROLL and VARIABLE; Part values can be DICE_ONLY, MODIFIERS_ONLY or TOTAL, with explicit comparison operators and threshold expressions. Actions may add/subtract/multiply/replace values, reroll, or schedule ROLL_AFTER; engine evaluation and user-interface editing belong to subsequent issues #104–#110, and must not be silently considered implemented by this schema change.

Persist **Part IDs**, never user-editable Part names, in actions and conditions. Formula tokens are canonical `{partId:<stable-id>}`. `PartReferenceAliases.store` resolves unambiguous readable `{parts:Damage}` aliases to stable tokens, and `display` projects the current name without rewriting persisted expressions. Duplicate names cannot be resolved as readable tokens, but already-stable references remain valid; missing references cause validation failures instead of accidental reassignment. This decouples rename, backup, and optional synchronization from presentation aliases.

AppData JSON v12 adds optional `effects` to each Roll. Older data decodes to empty Effects lists. The same codec handles portable backup and optional cloud data, and `CharacterRevision` includes serialized effect graphs in deterministic order without changing prior revision hashes for Rolls with no effects. `AppDataValidator` rejects duplicate IDs, invalid targets, empty executable graphs and unresolved references before persisting them. Renderer/RNG remain independent.


### Activation evaluator (#104)

`EffectActivationEvaluator` is a pure, deterministic logic component. It consumes `EffectRollSnapshot` containing full Roll and per-Part `DiceRollResult` values. `DICE_ONLY` includes only signed dice components, `MODIFIERS_ONLY` only the constant contribution, and `TOTAL` both. A natural 18 on d20 with +2 therefore **does not** satisfy a dice-only threshold of 20, but satisfies a total threshold of 20. Conditions within each group combine by AND; groups combine by OR. Every condition is evaluated for an auditable trace, including those in groups that did not activate. Missing targets and invalid expressions fail closed with an error in the trace.

Values from a double Roll are taken from the *selected* candidate group, never the spare dice rendered for visual comparison. Conditions on the entire Roll must explicitly use source `ROLL` (the existing aggregate); the UI still reports individual Part results instead of an ambiguous Roll grand total. Variable conditions must use scope `TOTAL`. Threshold expressions are initially evaluated using the existing RNG-free integer expression parser and variable tokens; a future expression evaluator (#105) extends this to `f()`, `c()`, `r()` and non-integer arithmetic, through the supplied threshold resolver callback. No Android UI, renderer, or storage reads occur in this component.


### Effect arithmetic and targeted changes (#105)

`EffectFormulaInterpreter` is deliberately separate from the dice expression parser. It never samples RNG or produces 3D events. It accepts arithmetic `+`, `-`, `*`, `/`, numeric variables, and stable `{partId:...}` references. It implements `f(x)` = floor, `c(x)` = ceil, and `r(x)` = nearest integer with halves **away from zero**, including negative values. Division is real-valued until the final result; conversions to the integer roll-domain default to **floor**, with explicit ceiling or rounding supported. Division by zero, unknown references, random dice expressions, nonfinite/overflowing results, or excessive nesting reject the formula.

`EffectActionEngine` is an immutable numeric projection of sampled Part results. `ADD`, `SUBTRACT`, `MULTIPLY`, and `REPLACE` change independently selected `DICE_ONLY`, `MODIFIERS_ONLY`, or `TOTAL` values. A total-only adjustment does not rewrite the natural dice faces or the underlying modifiers. The engine returns before/after/error traces and leaves the previous snapshot untouched on failure. `REROLL` and `ROLL_AFTER` require actual dice sampling, handled in the subsequent chain executor (#106), not arithmetic evaluation. Effect activation thresholds use the same deterministic formula interpreter without consuming RNG.


### Ordered Effects, exclusivity and roll-after execution (#106)

`EffectSequenceExecutor` consumes the logical dice engine's sampled Part results and resolved Part expressions. It evaluates enabled Effects in ascending explicit order, applying typed arithmetic through `EffectActionEngine` and generating fresh dice only through injected `Random` for `REROLL` or `ROLL_AFTER`. An Effect with `stopFollowingEffects` halts subsequent Effects only after it activates. Without it, multiple matching Effects apply sequentially; arithmetic sees each preceding action's updated values. Missing targets or unsupported scopes fail closed with an error trace, leaving the earlier snapshot unmodified.

Generated dice can activate previously unmatched rules in later passes, but each effect ID can activate **at most once per throw**. Additional protections cap 64 Effects, 128 actions, 100 extra rendered dice and 8 passes. The executor returns the original snapshot, final per-Part values, condition/action traces and extra sampled dice, separately from rendering. The 3D renderer never calculates numeric results. A modifiers-only dice reroll cannot consume dice RNG, and an invalid form is rejected instead of silently rerolling unrelated dice.

This is domain groundwork; table animations, displayed follow-up dice, historical trace serialization and UX remain in #108–#110. A release must not claim full interactive Effects until those issues are implemented and tested.


### Effects Editor (#108)

The Roll Parts remain the sole editable source of the roll expression. At the end of the Roll Builder, `EffectsEditorSectionV2` edits the separately persisted rule graph: BONUS/MALUS, ascending explicit priority (up/down), enabled flag, stop-following on trigger, OR activation groups containing AND conditions, source/scope/comparison/threshold, and ordered action entries with target Part IDs. Each Part selection is retained by stable ID, not its potentially duplicated or renamed title. Draft formulas show human-readable `{parts:Name}` references when unambiguous and are normalized to `{partId:id}` **only on Save**, so partial edits are not overwritten mid-typing. `EffectEditorDraft.canonicalize` validates references, arithmetic functions and dice expressions and disables Save for incomplete graphs. All Effects editor/trace strings are now provided in all 40 advertised locales, with strict format/technical-token validation; native-speaker review and physical-device QA remain release sign-off items in #111.


### Runtime Effects application and audit trail (#109)

After `DoubleRollEngine` chooses the winning (or losing) full group, `RollScreenV2` invokes the pure `EffectSequenceExecutor` on **only the selected** candidate Part results. Numerical final values come from `EffectNumericSnapshot`, not from the 3D simulation. The per-Part result UI shows those final totals without discarding the original dice faces; changed Parts display original → final. The historic single aggregate is adjusted only by actual Part deltas for backwards compatibility and is **not presented as a combined score across unrelated Parts**. The alternative double-roll group and its original comparison sum remain available independently, unmodified by Effects.

`EffectRuntimeHistory.steps` turns the engine's activation/action trace into an immutable per-throw record with effect name/type at execution time, condition group/value/comparison/threshold/pass/error, action kind/scope/target/before/after/error and generated-dice detail. This data is displayed in both the live statistics sheet and restored roll history. JSON v13 adds optional `effectSteps` and original Part values, used by local persistence, portable backups and optional Drive sync. Older logs decode with empty traces; optional fields are omitted from legacy revision hashes so previous records remain valid. Dice added by Roll After are recorded in this domain trace; staged 3D follow-up animations are addressed by #110. Full localization coverage is implemented; native-speaker review and device UX QA remain #111 release sign-off items.

### Premium effect visuals / staged dice (#110)

`EffectsVisualTimeline` is a pure presentation adapter. It builds bounded stages by **reading the existing logical engine's immutable** `EffectExecutionResult.generatedDice`; it does not sample new dice, resolve trigger conditions, or decide any result. A normal/Best/Worst roll first displays its already-selected dice. When that phase settles, `RollScreenV2` supplies a new visual event with the next pre-sampled REROLL or ROLL_AFTER dice. `DiceSceneRenderer` keeps previously settled dice still and animates only the newly introduced dice; the original values are never thrown again. REROLL visually dims replaced original Part dice, while nonselected double-roll dice continue to appear subdued.

Effect dice have theme-independent **green/cyan BONUS** or **crimson MALUS** accent and rim highlights. The GPU updates the numeral contrast against the tinted body, ensuring faces remain readable in both light and dark UI. A small, non-color-only Compose badge names the active effect; the badge and dice retain a static accessible state when `animationsEnabled=false`. Disabled animations bypass all intermediate stages and display the final *already-sampled* visual combination immediately, without delays or extra RNG. Full dice visibility is bounded by the renderer's 12-die budget; overflowing visual groups switch to the new sample rather than silently hiding it. A maximum of 12 visual stages prevents excessive scene updates; all generated values remain in the immutable logical trace/history even when not visualized.

The remaining acceptance criteria for #110 include a **manual real-device** visual pass across multiple tables, light/dark and low-power configurations, and pause/resume. #111 tracks strict 40-language validation, manual language review and device QA.


### Minimum Roll character level (#123)

`RollDefinition.minimumLevel` defaults to 1, bounded to 1..9999 and validated at storage/backup boundaries. `RollLevelAvailability.isAvailable(roll, characterLevel)` checks the saved independent `enabled` switch and the live character level. Both the dashboard and group **Use** screens filter through this same pure policy; **Edit** never filters out prepared high-level Rolls. When a character is leveled down while inside a locked Roll, the route resolves back to CHARACTER before another throw can be made. No Roll is deleted or rewritten by level changes; level-scaling formula rules are separate.

JSON data v14 includes `minimumLevel` in local/backup/cloud serialization, with a backwards-compatible default of 1 for older records. Revision hashing includes the field only when it differs from 1, keeping existing remote base revisions unchanged. Character duplication copies the threshold with the Roll; backup/restore and offline-first optional Drive reconciliation use the same model field. UI label uses already-localized `from_level` and the accompanying help text is translated to all 40 resource locales.


### Global cinematic visualization (#126–#129, #151)

`AppSettings.visualEffects` is one device-wide `RollVisualEffectsSettings` value, applied by every `RollScreenV2` through `AppSettings.effectiveVisualEffects()`. The default `BALANCED` profile preserves the expected premium UX; `SUBTLE`, `OFF` and custom toggles change only visual presentation. Eight flags control the badge, A/B lanes, post-settle winner lighting, aura, particles, action cues, transitions, and camera impact. The existing global `animationsEnabled` switch disables motion-heavy effects at runtime without rewriting preferences.

The configuration lives in Settings UI rather than each Roll editor, and is encoded/decoded in `AppDataJsonCodec.encodeSettings/decodeSettings`, included in exported backups and restored with safe BALANCED defaults when missing. **This setting is local to each installation**, as `animationsEnabled` is; `RoamingSettings` continues synchronizing only its existing subset so Drive manifest schema/revision hashes cannot silently diverge. Legacy `RollDefinition.visualEffects` remains accepted/serialized in JSON v15 and `CharacterRevision` to preserve existing tester backups/sync, but the renderer ignores per-Roll visual settings. Changing global visuals does not rewrite Roll documents, touch `CharacterRevision`, or consume RNG.

`RollBuilderScreenV2` now owns an opaque themed `Surface` → `ArcaneBackground` → transparent `Scaffold` and a single `TopAppBar` for every entry route (character, group, table), preventing underlying dice table/theme backgrounds from bleeding into the editor and guaranteeing correct Material content colors in DARK/LIGHT/SYSTEM. Android back and Cancel retain their existing navigation semantics. No new personal data collection or privacy permissions are introduced.

Double-roll numeric samples and winning candidate are determined in `DoubleRollEngine`; `DoubleRollVisualPlanner` constructs A/B membership metadata using already-sampled component indices, independently of RNG. `DiceAppearanceResolver` uses candidate B membership to apply the character's optional `secondaryDiceStyleId`; when absent, a deterministic contrasting gemstone/metal preset works even with no persisted styles. Candidate A still respects per-Roll appearance policies; neutral dice are not overridden. The optional preference is encoded for backup/sync and remapped on clone, while unset legacy values are omitted from canonical revision hashing. `DiceTablePhysics` keeps grouped candidates within compact central bounded lanes rather than the full table height (no clipping through a post-physics translation). `Dice3DScene` receives the selected indices and delays loser dimming until the final stage settles; `CandidateVisualLighting` is a pure, unit-tested post-reveal policy. Physics computes no dice values. `EffectsVisualTimeline` carries action kinds for visual action-specific rings and particles; `CinematicActionSequenceV2` replays the engine trace without rerolling or persisting additional logs. `RollResultsOverlayV2` can animate numeric original→final values without changing immutable results. After-effect dice use a visual-only edge spawn with pre-resolved values; malus rune fractures and bonus particle trails share the capped canvas timeline. Numeric interpolation starts on the original value to avoid a one-frame final-value flash. Particle count, lifetime and stage count remain capped. Candidate A/B labels are not overlaid on the table; optional spatial separation, distinct materials/colors and post-reveal dimming distinguish the groups, while the textual breakdown remains available in roll statistics.

Manual device QA is still required for different GPU/Android configurations, power/motion accessibility and table themes; emulator success alone is not real-device visual sign-off.


### Shared dice style catalog

`AppData.diceStyles` stores user-defined dice looks without a character owner (`DiceStyle.characterId = null`); non-null owners are accepted only for backward compatibility and migrated preserving IDs on local load/restore. Built-ins are immutable `preset:<key>` definitions in `DiceStylePresets` and never stored as editable records. Characters and Roll appearances store only stable style references; global edits do not change numerical dice RNG or per-character revision hashes. Deletion atomically reassigns all character and roll references to another saved custom look or a built-in fallback.

The independent catalog revision is a SHA-256 of canonical definitions, tracked in `LocalSyncJournal.diceCatalogMetadata` and `CloudManifest.diceCatalog` with writer/time/base revision and conflict classification, separately from character/settings revisions. A remote catalog conflict must be resolved before character synchronization can commit. Existing character cloud documents may carry read-only style snapshots for referential validation; the manifest catalog is the authoritative shared collection. Manual backup/restore serializes the versioned catalog and migrates legacy definitions without ID changes.


## Cross-device character sharing (web workshop)

The GitHub Pages React/Vite workshop saves character documents in IndexedDB (standalone, account-free). An explicit share action packages a **single** character and its modifiers, Roll Parts, level rules, effects and custom dice styles into `dice-thrower-character` format v1. JSON is zlib-compressed and encoded as Base64URL (`#character=v1.…`); the URL fragment is not sent to GitHub Pages. QR codes encode this self-contained HTTPS URL, not a server-side identifier. Web preview requires an explicit Save as copy; the Android `dicethrower://import?data=…` deep link opens a confirm dialog and stores a new independent character, remapping references. Original local data stays untouched. History, sync metadata, images and tokens are excluded from transfer. Large characters require JSON file export instead of QR. Do not advertise future Google Drive web sync as implemented. Schema changes require coordinated browser and Android decoder migration, tests and privacy review.

### Native outgoing character share and portable file fallback
The Android Character detail header includes an explicit share dialog. `CharacterTransferCodec.exportCharacter` projects a single local character and only referenced custom global styles into `AppData`; it removes all image references and excludes history, settings, sync metadata, other characters and unreferenced styles. `encodeJson` packages a v1 JSON envelope, `encodeToken` zlib-compresses it and Base64URL-encodes it; the exact URL-fragment format is shared with the web workshop. QR encoding is local-only (ZXing). Files that exceed the browser-compatible QR URL bound can be transferred as JSON through the Android Storage Access Framework (CreateDocument/OpenDocument), with strict decoding limits and the existing explicit confirmation/copy-remap import path. No network permission or Google account is needed to *create* the transfer. Physical device QR scanning remains a release acceptance test.

### Guided web rule composer interoperability
The web Character Workshop's `guided.js` owns purely structural factories and validators that mirror Android `Effects.kt`, `EffectsJsonCodec`, `DiceAppearanceResolver`, and `AppDataJsonCodec` fields. Its React component `GuidedRollEditor.jsx` changes the same versioned `dice-thrower-character` envelope rather than introducing new persisted fields or changing backup version. Stable Part IDs are used for conditions, actions and dice appearance mappings, and all imported IDs are rewritten on copy. Deleting a Part referenced by a rule is prevented; removing an unreferenced Part prunes its style assignment. `includeInDoubleRoll` and `includeInNormalRoll` are independent, but at least one Part stays active on the initial throw. Hidden expert JSON retains the full native schema for supported advanced cases. The browser does not perform RNG, logical effect evaluation or 3D rendering. No Google Drive account is needed and browser data stays in IndexedDB until users explicitly share/export it.

### D&D 5e starter critical-hit rule and legacy web models
The web level-one Fighter starter provides a single Roll with two independently surfaced Parts: Attack and Damage. It serializes `EffectCondition(PART, attackId, DICE_ONLY, EQUAL, "20")` and `EffectAction(ROLL_AFTER, damageId, DICE_ONLY, "1d8")`. The engine samples an additional d8 using its logical RNG, adds it to dice-only damage, and preserves Strength as the single modifier; `MULTIPLY` would produce an incorrect damage distribution. Templates for non-D&D systems must not inherit this D&D rule. Pre-fix browser characters with two separate default weapon Rolls are offered a **manual, explicit IndexedDB upgrade** only after strict compatibility checks (original expressions, no nested Parts/effects/styles or changed roll options). No IndexedDB schema migration, overwrite or automatic upgrade is performed. The upgrade preserves character/group and attack Roll IDs, along with unrelated character data. Existing JSON shares continue to import unchanged.

### Recipient-side character import preferences
`withImportPreferences` (web) and `CharacterTransferCodec.withRecipientPreferences` (Android) clone only the transferred character profile's name/level while retaining Roll Parts, future `minimumLevel` gating, dice styles, effects and groups. The recipient explicitly confirms import after inspecting a locally rendered preview; `importAsCopy` / `importCopy` remap persistent IDs independently of existing local characters. The browser's self-contained URL fragment continues to open the HTTPS workshop first. Its app button invokes the custom `dicethrower://import` scheme by a user gesture; no verified HTTPS App Links, redirect service or Cloud Sync requirement is claimed. Character JSON file imports use the same native preview. All raw document validation and size limits remain in force.

### D&D 2014 Bard preset (Lv 1–20)

The optional Bard 2014 factory uses only portable generic Roll Parts, level thresholds and formulas. Rapier has attack/damage with a natural-20 dice-only d8 bonus. Vicious Mockery is **damage-only** (no attack or critical; targets a WIS save) with gated 1d4/2d4/3d4/4d4 at 1/5/11/17. Bardic Inspiration **ally-only** Rolls are gated d6/d8/d10/d12 at levels 1/5/10/15; Song of Rest conditional heals are gated d6/d8/d10/d12 at 2/9/13/17. No effect is attached to regular attack Rolls, since eligible targets and limited uses cannot be inferred. Jack of All Trades is only a model **unproficient** Wisdom check with `{levelstep:2:9:17}` producing `floor(proficiency / 2)` from level 2, and it is never applied to any sample proficient skill or save.

Optional Healing Word slot 1–9 upcast variants use `Nd4+{Charisma}`, gated at Bard class levels 1/3/5/7/9/11/13/15/17, with spell knowledge/slot usage manual. EN/IT class tables give correct Bard 2014 known spells/cantrips and full-caster slots for all 20 levels, noting Bard College, Expertise, Magical Secrets, and ASI are choices. Historic generic adventure Bards remain untouched. Short version-pinned `#template=bard2014v1` references apply *only* to pristine built-in share shapes. No RNG/renderer/Android backup/storage/Drive schema migration.

### D&D 2014 Paladin preset (Lv 1–20)

The Paladin 2014 factory prepares optional (never silently migrated) Rolls using existing portable Part / `minimumLevel` / logical effect schema and generic `{levelstep:5:9:13:17}` proficiency token. Single-attack Longsword at level 1 has 1d8+STR and a natural-20 effect adding a fresh 1d8 dice-only. The level-5 Extra Attack variant has independent Attack/Damage pairs and conditional crit effects. The level-11 Improved Divine Smite (not a slot expenditure) adds 1d8 to every successful *melee weapon* hit: `2d8+STR` for each hit and critical effect adding `2d8` more. No modifiers are doubled and earlier variants remain usable as alternatives; selection is the player's responsibility.

From level 2, Divine Smite is an optional, **mutually exclusive** damage-only roll after an eligible melee weapon hit and spell-slot expenditure. Slot 1–5 templates unlock at class levels 2/5/9/13/17. Base radiant dice are `min(5, slot + 1)d8` plus 1d8 against undead/fiends capped at 6d8; independent critical variants double the chosen smite dice. These are not attached to the weapon's permanent effect, and resources, aura, oath, subclass/ASI, spell preparation, target/attack hit adjudication stay manual. The EN/IT 1–20 reference includes correct slot counts and Lay on Hands pool 5×class level. `#template=paladin2014v1` is an explicit short-link recipe only for a fingerprint-identical built-in model; web/Android full v1 transfer is unchanged. No backup/storage/Drive schema changes.

### D&D 2014 Cleric preset (Lv 1–20)

The opt-in 2014 Cleric starter uses the existing game-neutral `RollDefinition`, `minimumLevel` and `{levelstep:5:9:13:17}` domains, with no schema/backup/Drive changes. Sample Cleric proficient checks use Wisdom except Religion, which uses Intelligence, while Wisdom/Charisma saves and example spell attacks scale proficiency. A mace Attack/Damage pair has a natural-20 dice-only d6 critical action.

**Sacred Flame** is a target Dexterity saving throw and only rolls 1/2/3/4d8 damage using level-gated variants at 1/5/11/17; no attack die or critical rule is generated. Optional, prepared **Healing Word** / **Cure Wounds** variants at slot levels 1..9 carry `Nd4+{Wisdom}` / `Nd8+{Wisdom}`, with minimum level 1/3/5/7/9/11/13/15/17. `Guidance` and `Bless` are separate, optional 1d4 Rolls: no automatic spell eligibility or concentration enforcement. The 20-row cleric EN/IT timeline refers to full-caster 2014 spell slots and cantrips known and explains that preparation, Channel Divinity, Divine Intervention, Domain, ASI and slot tracking are manual. Historical saved generic fantasy Cleric docs remain unchanged. Share-preset recipe `#template=cleric2014v1` is only used for strictly matching unmodified presets; Android import remains a full v1 snapshot.

### D&D 2014 Ranger preset (Lv 1–20)

A new optional Ranger starter (without migrating saved generic Ranger data) uses existing generic `minimumLevel`, `RollSubgroup` and `RollEffect` domains. Bow Attack and Damage Parts have a natural-20 condition and independent `ROLL_AFTER 1d8` applied to **dice-only** Damage. Level 5 Extra Attack is a distinct Roll with two independent pairs and critical effects. As with Fighter, earlier attack options remain available, and the player selects the appropriate option when leveling. Proficiency scaling `{Proficiency}+{levelstep:5:9:13:17}` affects sample proficient skills, saves, attacks and optional spell attacks.

Starting at Ranger level 2, `Hunter's Mark` has two *optional*, **mutually exclusive** damage-only Rolls (`1d6` on eligible hit / `2d6` on eligible critical), not an unconditional weapon effect; the engine cannot infer concentration, spell known, target eligibility or once-per-hit use. Optional Cure Wounds `Nd8+{Wisdom}` upcast presets are gated at class levels 2/5/9/13/17 for spell slots 1–5. An EN/IT editorial 20-row class table includes the 2014 spell-slot counts; no automatic resources, spell-known selections, Favored Enemy, terrain, ASI or subclass choices are added. Strict unmodified-version sharing uses `#template=ranger2014v1`; JSON v1 and native Android backup/sync are unchanged. Legacy generic Ranger pre-fix weapon migration remains available with the historical tag/formulas, not implicitly applied to new D&D presets.

### Native web RollGroup parity, Wizard v2 and favicon (#198)

No new data model is needed: the browser works against the existing portable `RollGroup(id,characterId,name,order)` and `RollDefinition.groupId`. Both the recipient QR preview and website editor group Rolls by their same native IDs and `order`. Each group is an accessible, collapsible `details` element; reassignment updates only `groupId`, and deleting a group clears existing Roll group references instead of deleting its children. Ungrouped Rolls remain visible. Wizard-only SRD names live in `wizard-spells.js` as immutable **editorial reference**, NOT persistence rows or automatically prepared spells. Only spells with numeric dice/check actions become Roll presets. The website's SVG favicon reuses the dice path from `ic_launcher_monochrome.xml`, referenced via Vite `%BASE_URL%` on all Pages routes; Android launcher assets are unchanged.

Version pinning is essential: `buildWizard2014ProgressionV1` reconstructs the exact original pinned `wizard2014v1` template including its old group names, while `buildWizard2014Progression` wraps that factory to emit new RollGroups + dice spells. `matchingUnmodifiedSharePreset` compares the correct *version-specific* normalized document; new stock uses `wizard2014v2` and old share URLs decode to v1 without modifying existing characters. Native `AppData` envelope and manual backups remain v1 compatible; no implicit migration, schema bump or Drive change. Large web↔Android deep links are intentionally not offered beyond 8000 chars; the existing explicit JSON file picker is the fallback for complex presets. E2E on physical QR reader remains under EPIC #165.

### D&D 2014 Wizard preset (Lv 1–20)

The optional Wizard starter applies generic proficiency milestone tokens `{levelstep:5:9:13:17}` to sample spell attacks/skills/saves. Fire Bolt is modelled with four separate, level-gated Attack/Damage `RollDefinition` presets at thresholds 1/5/11/17; this is necessary because its natural-20 `ROLL_AFTER` damage scales with the **entire number of cantrip dice**, not just the level-one die. Each preset has its own Part IDs and effect references. No game-specific RNG rule or persistence change is needed.

For Magic Missile, 9 optional slot-tier Roll presets are gated to Wizard levels 1/3/5/7/9/11/13/15/17. Each spell has between 3 and 11 distinct, independently addressable `1d4+1` Dart Parts and **no** attack-roll/critical effect. The tabletop's alternative shared-d4 ruling can be handled by using one dart result for all missiles instead of interpreting all rolled darts as independent. The 20-row EN/IT reference table shows slot counts (spell levels 1..9), cantrips known and class features but does not represent a live spellbook or slot expenditure. Spell preparation, Arcane Tradition/ASI and high-level free casts are user choices. New v2 Wizard RollGroups are **Cantrips, Spell Lv 1–9, Skills & saves**; base spell level defines the group, not the slot used to upcast. 2014 SRD names 0–9 are reference only; the standard numeric Rolls cover common attacks, save damage and conditional checks, with correct critical scope and class/slot gates. Magic Missile stays in Spell Lv 1 for slots 1–9; Scorching Ray has one attack/damage/critical pair per ray; Fireball, Burning Hands, Thunderwave and Lightning Bolt have separately gated upcasts. Casting, spellbook/preparation, concentration, save outcomes, slots and targeting are external game decisions. The new pristine shape uses `#template=wizard2014v2`; `#template=wizard2014v1` still reproduces the exact original unmodified example.

### D&D 2014 Rogue preset (Lv 1–20)

The Rogue 2014 starter builds on the *existing* generic `RollDefinition` / `RollLevelRule` model without adding class-logic to the Android dice engine or changing storage/Drive/backups. Dagger has paired Attack/Damage `RollSubgroup` definitions and a natural-20 `DICE_ONLY` condition with `ROLL_AFTER 1d4` targeting Damage, not the Dexterity modifier. Every proficient skill/save receives `{Proficiency}+{levelstep:5:9:13:17}`, with the sample skills editable by the user. A Wisdom save starts at `minimumLevel=15`, matching Slippery Mind.

Sneak Attack is intentionally separate optional extra damage (eligibility and once-per-turn are game-table decisions). Normal and critical sneak damage are two mutually exclusive Roll definitions. The normal baseline `1d6` has `FROM_LEVEL` incremental `1d6` milestones 3/5/7/9/11/13/15/17/19. The critical baseline `2d6` has `FROM_LEVEL 2d6` milestones. Thus level 1 is 1d6/2d6 and level 19–20 is 10d6/20d6. Critical **weapon** effect does not also auto-apply Sneak Attack because its eligibility cannot be inferred. A 20-level reference timeline in EN/IT distinguishes automatic rolls from manual choices for Expertise, subclass, ASI, Reliable Talent and once-per-rest class resources. The standard starter remains opt-in; previous generic Rogue saves remain untouched.

### Generic milestone expressions and D&D 2014 Fighter (Lv 1–20)
`RollFormulaResolver.resolveExpression` supports the deterministic, reusable `{levelstep:N[:N...]}` placeholder: ordered strict integer thresholds in 2..9999 yield the count of thresholds reached at the character's current level. It requires no schema change and is resolved by the logical formula engine before parsing dice, while the browser workshop validates the same token shape. It is a **game-neutral** formula feature, not an implicit "D&D proficiency" special case. The 2014 Fighter starter combines a base `{Proficiency}` +2 with `{levelstep:5:9:13:17}` to match the 2014 level table. Each future Attack-action variant is a separate `RollDefinition(minimumLevel=5/11/20)` and has independent Attack/Damage `RollSubgroup` pairs plus corresponding natural-20 `ROLL_AFTER 1d8` actions; dice RNG stays in Android's logical dice engine. The website optionally shows a 20-level class-feature timeline without persisting game-specific metadata in `AppData`. At and after a threshold the earlier presets remain available as alternatives: the dice app does not silently alter or delete configured rolls. Player-chosen ASI/feats/subclass are not inferred; users must configure them. No backup, Drive or sync schema migration required.

### QR capacity and pinned factory templates
Single QR codes are bounded by physical payload capacity. The workshop favors full compressed `#character=v1.` payloads; only an unmodified 2014 Fighter, Rogue, Wizard, Ranger, Cleric, Paladin or Bard whose full v1 token exceeds the safe URL bound uses a pinned `#template=fighter2014v1&name=…&level=…` , `#template=rogue2014v1&name=…&level=…` , `#template=wizard2014v1&name=…&level=…` or `#template=wizard2014v2&name=…&level=…` , `#template=ranger2014v1&name=…&level=…` , `#template=cleric2014v1&name=…&level=…` , `#template=paladin2014v1&name=…&level=…` or `#template=bard2014v1&name=…&level=…` fragment. `canonicalPresetShape` maps all local IDs and nested reference IDs to stable semantic positions before comparing all stored user data except recipient-editable name/level with the exact built-in model. Any custom modifier, roll, Part, effect, default style, image, tag or unknown field prevents this shortcut. No user-authored configuration is silently lost. The destination browser rebuilds the version-pinned factory, previews it, and uses the existing unmodified v1 `dicethrower://import` transfer to the installed app. This compact preset link is a **recipe reference**, not a self-contained copy of the whole character, and depends on the compatible website remaining available; export portable JSON for durable/offline independent transfer.
