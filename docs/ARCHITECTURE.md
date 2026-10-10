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

`DiceStyle`: character-owned visual style with material, primary/secondary colors and stable ordering. Roll appearance policies reference style IDs and never participate in dice math. `CharacterProfile.diceTableTheme` selects the persisted per-character roll table and `diceTableImage` can override the felt with a portable custom image.

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

## Critical-hit quick template (#140 / EPIC #139)

`EffectEditorDraft.criticalHit` creates a normal persisted `RollEffect` with stable IDs, `PART` trigger on the first subgroup (`DICE_ONLY == 20`) and a `MULTIPLY ×2` action on the second subgroup using `DICE_ONLY`. Modifiers remain unmodified and the execution path stays `EffectSequenceExecutor` → `EffectActionEngine`, never the renderer. No schema migration or separate effects executor is needed. The UI offers the template plus WHEN/THEN headings; existing advanced groups/actions remain editable and serializable. Limitation: `DICE_ONLY` is the dice subtotal of the Part, not a predicate on one die face within a multi-die Part.

## Immutable dice appearance presets (EPIC #138, slice #141)

`DiceStylePresets` is a pure-domain, immutable catalog of ten material/color palettes with stable keys. Presets are *definitions*, not persisted records. Adding a preset creates a fresh character-owned `DiceStyle` via `DiceStyleDataOperations.createStyle`, with an owned ID, localized saved name and existing reference-safe storage/backup/Drive lifecycle. The home character list now links to a Dice Customization hub, which selects a character and reuses the existing editing surface. No graphics can affect `DiceExpression.evaluate` or physics-to-RNG coupling.

**Not global user-style sharing yet:** the catalog management hub currently works on a selected character; cross-character copies are value based. Future #138 subissues will establish shared user-created style identity and its offline-first sync/conflict model before changing persisted owner semantics. Do not silently reinterpret existing character style IDs as global.

## Dice style editor

Character Edit mode exposes the character-owned `DiceStyle` collection without coupling the OpenGL renderer to persistence. `DiceStyleDataOperations` owns reference-safe create/update/duplicate/reorder/default/delete operations, while Compose edits domain values and persists the returned `AppData` through the existing screen boundary.

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


### Per-Roll cinematic visualization (#126–#129)

`RollDefinition.visualEffects` is an independent, offline-first `RollVisualEffectsSettings` value. The default `BALANCED` profile preserves the expected premium UX; `SUBTLE`, `OFF`, and custom toggles change only presentation. Eight independently stored flags control semantic badge, A/B lanes, post-settle winner lighting, aura rings, particles, action-type cues, numeric interpolation, and gentle camera impact. JSON v15 encodes all fields; missing pre-v15 data decodes to BALANCED, and `CharacterRevision` emits a visual-settings section **only for nondefault configuration**. The existing global `animationsEnabled` flag disables motion-heavy effects at runtime without rewriting user choices.

Double-roll numeric samples and winning candidate are determined in `DoubleRollEngine`; `DoubleRollVisualPlanner` constructs A/B membership metadata using already-sampled component indices, independently of RNG. `DiceAppearanceResolver` uses candidate B membership to apply the character's optional `secondaryDiceStyleId`; when absent, a deterministic contrasting gemstone/metal preset works even with no persisted styles. Candidate A still respects per-Roll appearance policies; neutral dice are not overridden. The optional preference is encoded for backup/sync and remapped on clone, while unset legacy values are omitted from canonical revision hashing. `DiceTablePhysics` keeps grouped candidates within compact central bounded lanes rather than the full table height (no clipping through a post-physics translation). `Dice3DScene` receives the selected indices and delays loser dimming until the final stage settles; `CandidateVisualLighting` is a pure, unit-tested post-reveal policy. Physics computes no dice values. `EffectsVisualTimeline` carries action kinds for visual action-specific rings and particles; `CinematicActionSequenceV2` replays the engine trace without rerolling or persisting additional logs. `RollResultsOverlayV2` can animate numeric original→final values without changing immutable results. After-effect dice use a visual-only edge spawn with pre-resolved values; malus rune fractures and bonus particle trails share the capped canvas timeline. Numeric interpolation starts on the original value to avoid a one-frame final-value flash. Particle count, lifetime and stage count remain capped. Candidate A/B labels are not overlaid on the table; optional spatial separation, distinct materials/colors and post-reveal dimming distinguish the groups, while the textual breakdown remains available in roll statistics.

Manual device QA is still required for different GPU/Android configurations, power/motion accessibility and table themes; emulator success alone is not real-device visual sign-off.
