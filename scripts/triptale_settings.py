"""Reads TripTale's settings.yml for helper scripts (stdlib only, no side effects on import).

Mirrors the app: the settings directory is `TRIPTALE_SETTINGS_DIR`, else `%APPDATA%\\triptale`
(Windows) or `$HOME/.config/triptale`; `dataDir` supports `${HOME}`, `${USER}` and a leading `~`
and has no default.

Only top-level scalar keys are read (`dataDir`, `radioEnabled`, ...); nested blocks such as
`git:` or `attachments:` are skipped. Failures raise `SettingsError`, callers decide how to exit.

Usage from a sibling script:
    from triptale_settings import data_dir
"""
import getpass
import os
import re
from pathlib import Path

SETTINGS_FILE = "settings.yml"

_SCALAR_RE = re.compile(r"^([A-Za-z_][\w-]*)\s*:\s*(.*?)\s*$")


class SettingsError(Exception):
    pass


def settings_dir() -> Path:
    override = os.environ.get("TRIPTALE_SETTINGS_DIR")
    if override:
        return Path(override).expanduser()
    appdata = os.environ.get("APPDATA")
    if os.name == "nt" and appdata:
        return Path(appdata) / "triptale"
    return Path.home() / ".config" / "triptale"


def settings_file() -> Path:
    return settings_dir() / SETTINGS_FILE


def _unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    # strip a trailing " # comment" on bare values
    return re.sub(r"\s+#.*$", "", value)


def parse_scalar_settings(text: str) -> dict[str, str]:
    """Top-level `key: value` pairs of a YAML text, unquoted. Indented lines, comments and
    keys without an inline value (nested blocks) are ignored."""
    result: dict[str, str] = {}
    for line in text.lstrip("\ufeff").splitlines():
        if not line or line[0] in " \t#-":
            continue
        m = _SCALAR_RE.match(line)
        if m and m.group(2) != "":
            result[m.group(1)] = _unquote(m.group(2))
    return result


def load_settings() -> dict[str, str]:
    path = settings_file()
    if not path.is_file():
        raise SettingsError(f"settings file not found: {path} (set TRIPTALE_SETTINGS_DIR?)")
    return parse_scalar_settings(path.read_text(encoding="utf-8"))


def expand(value: str) -> str:
    """Expands `${HOME}`, `${USER}` and a leading `~` like AppSettings.resolvedDataDir."""
    home = str(Path.home())
    out = value.replace("${HOME}", home).replace("${USER}", getpass.getuser())
    if out.startswith("~"):
        out = home + out[1:]
    return out


def data_dir() -> Path:
    raw = load_settings().get("dataDir", "")
    if not raw.strip():
        raise SettingsError(f"dataDir is not set in {settings_file()}")
    return Path(expand(raw)).resolve()
