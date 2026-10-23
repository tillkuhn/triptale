#!/usr/bin/env python3
"""One-time migration for todo 72: reformat the packed `weather:` frontmatter value so it
stays parseable once the app understands an `elevation` field.

Before todo 72: `weather: "min;max;code[;place]"` — a 4th field was always a place.
After todo 72:  `weather: "min;max;code;elevation[;place]"` — a 4th field is now elevation,
and a place (with no elevation) is written with an explicit *empty* elevation field instead:
`"min;max;code;;place"`. The two 4-field shapes (old "...;place", new "...;elevation") are
positionally identical and can't be told apart automatically — this script disambiguates the
*old* data by rewriting every legacy place-only value into the new, unambiguous 5-field shape.

This does **not** backfill elevation itself (no network calls) — existing entries simply have
no elevation until the Coordinator's 🔄 is used to re-fetch that day's weather by hand, which
will pick up elevation for free from the same Open-Meteo response. See docs/72_weather_elevation.md.

Only touches `weather:` values shaped exactly like the old 3- or 4-field scheme (3 fields:
min;max;code, left untouched since they're unambiguous either way; 4 fields: min;max;code;place,
rewritten to 5 fields with an empty elevation slot). A value that already looks like the new
5-field shape, or anything unparseable, is left alone. Not part of the app -- run manually, once,
against a data dir.

Usage:
    python3 scripts/migrate_weather_elevation.py ~/Pictures/triptale-data --dry-run
    python3 scripts/migrate_weather_elevation.py ~/Pictures/triptale-data
"""
import argparse
import re
import sys
from pathlib import Path

# Matches a `weather:` frontmatter line, quoted or bare, capturing the raw packed value.
WEATHER_LINE_RE = re.compile(r'^(weather:\s*)(".*"|\S.*)\s*$')


def unquote(raw: str) -> tuple[str, bool]:
    """Strips a surrounding '"..."' if present; returns (value, was_quoted)."""
    if len(raw) >= 2 and raw[0] == '"' and raw[-1] == '"':
        return raw[1:-1], True
    return raw, False


def migrate_value(raw: str) -> str | None:
    """Returns the rewritten raw value, or None if this value doesn't need (or can't safely
    get) the migration."""
    value, was_quoted = unquote(raw.strip())
    parts = value.split(";")
    if len(parts) != 4:
        # 3 fields (no place, unambiguous either way) or already 5+ (already new shape): leave alone.
        return None
    min_s, max_s, code_s, place = parts
    try:
        int(min_s.strip())
        int(max_s.strip())
        int(code_s.strip())
    except ValueError:
        return None  # not even old-shape weather data; don't touch it
    if place.strip() == "":
        return None  # a trailing empty 4th field is already the new "no place" shape
    new_value = f"{min_s};{max_s};{code_s};;{place}"
    needs_quotes = was_quoted or "," in new_value or ":" in new_value or new_value.startswith("-")
    return f'"{new_value}"' if needs_quotes else new_value


def migrate_file(path: Path) -> str | None:
    content = path.read_text(encoding="utf-8")
    lines = content.splitlines(keepends=True)
    changed = False
    for i, line in enumerate(lines):
        m = WEATHER_LINE_RE.match(line.rstrip("\n"))
        if not m:
            continue
        new_value = migrate_value(m.group(2))
        if new_value is None:
            continue
        newline = "\n" if line.endswith("\n") else ""
        lines[i] = f"{m.group(1)}{new_value}{newline}"
        changed = True
    return "".join(lines) if changed else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("data_dir", type=Path, help="Path to the triptale data dir")
    parser.add_argument("--dry-run", action="store_true", help="Print changes without writing")
    args = parser.parse_args()

    data_dir: Path = args.data_dir.expanduser()
    if not data_dir.is_dir():
        print(f"Not a directory: {data_dir}", file=sys.stderr)
        return 1

    changed = 0
    skipped = 0
    for entry_file in sorted(data_dir.glob("*/*/*.md")):
        if entry_file.name == "README.md":
            continue
        new_content = migrate_file(entry_file)
        if new_content is None:
            skipped += 1
            continue
        changed += 1
        rel = entry_file.relative_to(data_dir)
        if args.dry_run:
            print(f"  WOULD UPDATE: {rel}")
        else:
            entry_file.write_text(new_content, encoding="utf-8")
            print(f"  UPDATED: {rel}")

    mode = "DRY RUN — " if args.dry_run else ""
    print(f"{mode}Done. {changed} file(s) updated, {skipped} unchanged (no legacy weather place field found).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
