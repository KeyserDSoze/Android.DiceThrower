#!/usr/bin/env python3
"""Validate that every advertised Android locale is complete and format-safe."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src" / "app" / "src" / "main" / "res"

# Android uses legacy resource qualifiers for Indonesian and Hebrew.
LOCALES = [
    "en", "it", "es", "fr", "de", "pt", "ru", "ar", "hi", "zh-rCN",
    "ja", "ko", "in", "tr", "vi", "bn", "ur", "fa", "pl", "nl", "th",
    "ms", "sw", "ta", "te", "mr", "pa", "gu", "kn", "ml", "my", "ne",
    "uk", "iw", "el", "ro", "cs", "hu", "sv", "ha",
]

FORMAT_ARGS = re.compile(r"%\d+\$[sd]")
TECHNICAL = re.compile(
    r"#2563EB|appDataFolder|Dice Thrower|Google Drive|Google|Drive|MiB|"
    r"(?<![A-Za-z0-9])(?:d100|d20|d12|d10|d6|d4|d3|d2)(?![A-Za-z0-9])"
)


def read_directory(directory: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        for node in root.findall("string"):
            name = node.attrib["name"]
            if name in values:
                raise ValueError(f"{directory}: duplicate key {name} (including {path.name})")
            values[name] = "".join(node.itertext()).strip()
    return values


def main() -> int:
    source = read_directory(RES / "values")
    source_keys = set(source)
    failures = []

    for locale in LOCALES:
        directory = RES / ("values" if locale == "en" else f"values-{locale}")
        if not directory.is_dir():
            failures.append(f"{locale}: missing {directory.relative_to(ROOT)}")
            continue
        values = read_directory(directory)
        keys = set(values)
        missing = source_keys - keys
        extra = keys - source_keys
        if missing:
            failures.append(f"{locale}: missing keys: {', '.join(sorted(missing))}")
        if extra:
            failures.append(f"{locale}: unexpected keys: {', '.join(sorted(extra))}")
        # The Effects composer teaches users executable formulas. Keep the
        # function spelling and canonical Part reference syntax intact in every
        # translation, rather than only checking formatting placeholders.
        required_formula_tokens = ("f(x)", "c(x)", "r(x)", "{parts:")
        formula_help = values.get("effects_formula_help", "")
        if any(token not in formula_help for token in required_formula_tokens):
            failures.append(f"{locale}/effects_formula_help: required formula token missing")
        for key in ("effects_help", "effects_and_condition", "effects_or_group_number"):
            translated = values.get(key, "")
            if "AND" not in translated and key != "effects_or_group_number":
                failures.append(f"{locale}/{key}: AND operator missing")
            if key == "effects_or_group_number" and "OR" not in translated:
                failures.append(f"{locale}/{key}: OR operator missing")

        for key in sorted(source_keys & keys):
            if not values[key]:
                failures.append(f"{locale}/{key}: blank translation")
                continue
            expected_args = sorted(FORMAT_ARGS.findall(source[key]))
            actual_args = sorted(FORMAT_ARGS.findall(values[key]))
            if actual_args != expected_args:
                failures.append(
                    f"{locale}/{key}: format args differ; expected {expected_args}, got {actual_args}"
                )
            expected_technical = set(TECHNICAL.findall(source[key]))
            actual_technical = set(TECHNICAL.findall(values[key]))
            if not expected_technical.issubset(actual_technical):
                failures.append(
                    f"{locale}/{key}: missing technical token(s): "
                    f"{sorted(expected_technical - actual_technical)}"
                )
            if "<unk>" in values[key] or "91827364" in values[key] or "ZXQ" in values[key]:
                failures.append(f"{locale}/{key}: translation sentinel/unknown token leaked")

        for invariant in ("app_name", "dice_color_example"):
            if invariant in values and values[invariant] != source[invariant]:
                failures.append(f"{locale}/{invariant}: invariant value changed")

    if failures:
        print("Localization validation failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print(f"Localization validation passed: {len(LOCALES)} locales × {len(source)} strings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
