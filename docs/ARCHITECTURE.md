# Dice Thrower architecture

## Principles

- Game agnostic: no D&D-specific concepts in the domain model.
- Offline-first: no network dependency for core functionality.
- Local ownership: character data and logs remain on-device.
- UI/runtime separation: dice parsing and rolling are independent from Compose and from any future 3D renderer.
- Explicit ordering: groups and rolls persist an `order` field.
- Stable identifiers: all entities use UUID strings.

## Domain model

`CharacterProfile`: id, name, imageUri, tag, order.

`RollGroup`: id, characterId, name, order.

`RollDefinition`: id, characterId, name, expression, groupId (nullable), enabled, order.

`RollLog`: id, characterId, rollDefinitionId, rollName, expression, total, detail, timestamp.

`AppSettings`: theme, shake, animations, roll-button visibility/position, log retention.

## Persistence

The bootstrap stores a single versioned JSON document in private SharedPreferences. This keeps the first implementation dependency-light and easy to migrate. If the data model grows substantially, `LocalStore` is the boundary to replace, for example with Room.

## Dice expressions

Grammar v0.1:

```
expression := term (("+" | "-") term)*
term       := dice | integer
dice       := [count] "d" sides
```

Supported sides: 2, 3, 4, 6, 10, 12, 20, 100.

## Shake handling

The accelerometer listener is active only while the roll screen is visible. A debounce window avoids duplicate throws from one physical gesture.

## 3D renderer

A future 3D renderer consumes the already-produced numerical result. Physics/animation never decides the numerical result: the dice engine decides first, then the renderer visualizes it.

## Localization

The locale registry mirrors Android.ScreenLock's 40 languages.

## Website

`src/overviewapp` is a React/Vite static site with localized copy, light/dark theme, privacy, terms and contact pages.
