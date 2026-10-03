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

`CharacterProfile`: id, name, imageUri, tag, level, order.

`CharacterModifier`: id, characterId, name, integer value, order.

`RollGroup`: id, characterId, name, order.

`RollDefinition`: id, characterId, name, base expression, groupId, enabled, order, levelRules.

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
7. store the fully resolved expression in the roll log.

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

The application stores one versioned JSON document in private SharedPreferences. Storage version 2 adds character level, modifiers and level rules.

Version-1 data remains readable:

- missing character level defaults to 1;
- missing modifiers default to an empty list;
- missing roll level rules default to an empty list.

The preference key remains unchanged so existing installations migrate transparently.

If the model grows substantially, `LocalStore` is the boundary to replace, for example with Room.

## Shake handling

The accelerometer listener is active only while the roll screen is visible. A debounce window avoids duplicate throws from one physical gesture.

## 3D renderer

A future 3D renderer consumes the already-produced numerical result. Physics/animation never decides the numerical result: the dice engine decides first, then the renderer visualizes it.

## Localization

The locale registry mirrors Android.ScreenLock's 40 languages. English and Italian are the reference translations while the UI vocabulary is still evolving.

## Website

`src/overviewapp` is a React/Vite static site with localized copy, light/dark theme, privacy, terms and contact pages.
