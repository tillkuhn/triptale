#!/usr/bin/env python3
"""Renames the tracks in <dataDir>/radio/ to "Title - Artist.ext" (todo 98), like
toolbox/91radio/main.go: strip < > : " / \\ | ? *, trim, title-case each word. Tags are read
with ffprobe (part of ffmpeg). Files without title/artist tags, already correct names and
name clashes are left alone. Not part of the app.

Dry run by default; the bucket sync later sees each rename as delete + upload.

Usage:
    python3 scripts/radio_normalize.py            # show what would be renamed
    python3 scripts/radio_normalize.py --apply    # do it
    python3 scripts/radio_normalize.py --data-dir ~/some/data
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

from triptale_settings import SettingsError, data_dir

EXTENSIONS = {".mp3", ".m4a", ".aac", ".wav", ".aif", ".aiff"}
INVALID_CHARS = re.compile(r'[<>:"/\\|?*]+')


def go_title(text: str) -> str:
    """Like Go's strings.Title: upper-case the first letter of each word, leave the rest."""
    out, prev_letter = [], False
    for ch in text:
        out.append(ch if prev_letter else ch.upper())
        prev_letter = ch.isalpha() or ch.isdigit() or ch in "_'"
    return "".join(out)


def target_name(title: str, artist: str, ext: str) -> str:
    base = INVALID_CHARS.sub("", f"{title} - {artist}")
    return go_title(base.strip()) + ext.lower()


def name_parts(stem: str) -> tuple[str, str] | None:
    """Fallback without tags: a stem with exactly one '-' is 'title-artist'; '_' become blanks."""
    if stem.count("-") != 1:
        return None
    title, artist = (re.sub(r"\s+", " ", p.replace("_", " ")).strip() for p in stem.split("-"))
    return (title, artist) if title and artist else None


def read_tags(ffprobe: str, file: Path) -> tuple[str, str]:
    proc = subprocess.run(
        [ffprobe, "-v", "error", "-show_entries", "format_tags=title,artist", "-of", "json", str(file)],
        capture_output=True, text=True, encoding="utf-8", check=False)
    if proc.returncode != 0:
        return "", ""
    tags = {k.lower(): v for k, v in json.loads(proc.stdout or "{}").get("format", {}).get("tags", {}).items()}
    return tags.get("title", "").strip(), tags.get("artist", "").strip()


def tracks(root: Path) -> list[Path]:
    return sorted(
        p for p in root.rglob("*")
        if p.is_file() and p.suffix.lower() in EXTENSIONS
        and not any(part.startswith(".") for part in p.relative_to(root).parts))


def shorten(text: str, width: int) -> str:
    """Cuts the middle of an over-long text: 'Land Of Th…Gamma Ray.mp3'."""
    if len(text) <= width:
        return text
    keep = width - 1
    head = keep - keep // 2
    return text[:head] + "…" + text[len(text) - keep // 2:]


def print_table(rows: list[tuple[str, str, str, str]]) -> None:
    if not rows:
        return
    headers = ("ACTION", "BASED ON", "CURRENT", "NEW NAME / REASON")
    # each name column gets at most half of what the terminal has left after the short columns
    cap = max(20, (shutil.get_terminal_size((120, 24)).columns - 26) // 2)
    cells = [(a, s, shorten(b, cap), shorten(c, cap)) for a, s, b, c in rows]
    widths = [max(len(h), *(len(r[i]) for r in cells)) for i, h in enumerate(headers)]
    line = "  ".join("{:<%d}" % w for w in widths)
    print(line.format(*headers))
    print("  ".join("-" * w for w in widths))
    for r in cells:
        print(line.format(*r))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("-a", "--apply", action="store_true", help="really rename (default: dry run)")
    ap.add_argument("--data-dir", type=Path, help="override dataDir from settings.yml")
    args = ap.parse_args()

    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        print("error: ffprobe not found on PATH (install ffmpeg)", file=sys.stderr)
        return 2
    try:
        root = (args.data_dir.expanduser() if args.data_dir else data_dir()) / "radio"
    except SettingsError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    if not root.is_dir():
        print(f"error: {root} does not exist", file=sys.stderr)
        return 2

    renamed = unchanged = skipped = 0
    rows: list[tuple[str, str, str, str]] = []
    claimed: set[Path] = set()
    for file in tracks(root):
        rel = str(file.relative_to(root))
        title, artist = read_tags(ffprobe, file)
        source = "[tag]"
        if not title or not artist:
            parts = name_parts(file.stem)
            if parts is None:
                rows.append(("skip", "-", rel, "no tags, filename is not 'title-artist'"))
                skipped += 1
                continue
            title, artist = parts
            source = "[filename]"
        target = file.with_name(target_name(title, artist, file.suffix))
        if target.name == file.name:
            unchanged += 1
            continue
        # a case-only change on a case-insensitive file system (macOS/Windows) is the same file
        clash = (target.exists() and not target.samefile(file)) or target in claimed
        if clash:
            rows.append(("skip", source, rel, f"{target.name} already exists"))
            skipped += 1
            continue
        claimed.add(target)
        rows.append(("rename" if args.apply else "would", source, rel, target.name))
        if args.apply:
            file.rename(target)
        renamed += 1

    print_table(rows)
    verb = "renamed" if args.apply else "would rename"
    print(f"\n{verb} {renamed}, unchanged {unchanged}, skipped {skipped}"
          + ("" if args.apply or not renamed else "  (dry run, use --apply)"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
