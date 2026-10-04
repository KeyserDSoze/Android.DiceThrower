# Dice Thrower · Store assets

The visual identity follows the in-app arcane palette: deep navy, electric cyan/blue, violet and restrained gold accents.

## Sources

- Android launcher artwork: `src/app/src/main/res/drawable-nodpi/ic_launcher_art.png`
- Editable Google Play feature graphic: `play/assets/feature-graphic.svg`
- Android 13+ themed icon: `src/app/src/main/res/drawable/ic_launcher_monochrome.xml`

## Generate Play-ready raster files

From the repository root, with ImageMagick installed:

```bash
bash play/assets/generate-store-assets.sh
```

This writes:

- `play/generated/icon-512.png` — 512×512 Play Store icon
- `play/generated/feature-graphic-1024x500.png` — 1024×500 feature graphic

The generated directory is intended as release output. The editable/source assets remain version controlled so the artwork can evolve without losing reproducibility.

## Visual rules

- Keep the d20/dice cluster readable at small sizes.
- Do not put text inside the Android launcher icon.
- Use the gold accent sparingly for hierarchy, not as the dominant UI color.
- Store artwork may be more cinematic than the app UI; the app itself prioritizes speed and readability.
- Avoid game-system-specific trademarks or iconography: Dice Thrower stays game-agnostic.
