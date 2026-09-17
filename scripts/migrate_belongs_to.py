#!/usr/bin/env python3
"""One-time migration for todo 20: backfill `belongs_to` frontmatter on existing
tale entries so Tolaria can resolve the trip -> entry backlink.

Walks <data-dir>/<year>/<slug>/*.md (skipping README.md, which is the trip
itself, not an entry) and inserts:

    belongs_to: "[[<year>/<slug>/README]]"

in alphabetical position within the existing frontmatter block. Entries that
already have a belongs_to key are left untouched. Not part of the app --
run manually, once, against a data dir.

Usage:
    python3 scripts/migrate_belongs_to.py ~/Pictures/triptale-data --dry-run
    python3 scripts/migrate_belongs_to.py ~/Pictures/triptale-data
"""
import argparse
import re
import sys
from pathlib import Path

FRONTMATTER_RE = re.compile(r"\A---\n(.*?\n)---\n", re.DOTALL)
YEAR_RE = re.compile(r"^\d{4}$")


def build_belongs_to_line(year: str, slug: str) -> str:
    return f'belongs_to: "[[{year}/{slug}/README]]"\n'


def migrate_file(path: Path, year: str, slug: str) -> str | None:
    content = path.read_text(encoding="utf-8")
    match = FRONTMATTER_RE.match(content)
    if not match:
        print(f"  SKIP (no frontmatter): {path}")
        return None

    fm_body = match.group(1)
    lines = fm_body.splitlines(keepends=True)

    if any(line.startswith("belongs_to:") for line in lines):
        return None

    new_line = build_belongs_to_line(year, slug)
    insert_at = len(lines)
    for i, line in enumerate(lines):
        key = line.split(":", 1)[0]
        if key > "belongs_to":
            insert_at = i
            break
    lines.insert(insert_at, new_line)

    new_fm_body = "".join(lines)
    return content[: match.start(1)] + new_fm_body + content[match.end(1) :]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("data_dir", type=Path, help="Path to the triptale data dir")
    parser.add_argument("--dry-run", action="store_true", help="Print changes without writing")
    args = parser.parse_args()

    data_dir: Path = args.data_dir.expanduser()
    if not data_dir.is_dir():
        print(f"Not a directory: {data_dir}", file=sys.stderr)
        return 1

    changed = 0
    skipped = 0
    for year_dir in sorted(p for p in data_dir.iterdir() if p.is_dir()):
        if not YEAR_RE.match(year_dir.name):
            continue
        year = year_dir.name
        for trip_dir in sorted(p for p in year_dir.iterdir() if p.is_dir()):
            slug = trip_dir.name
            for entry_file in sorted(trip_dir.glob("*.md")):
                if entry_file.name == "README.md":
                    continue
                new_content = migrate_file(entry_file, year, slug)
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
    print(f"{mode}Done. {changed} file(s) updated, {skipped} already had belongs_to or had no frontmatter.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
