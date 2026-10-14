"""Author a v1 integrity manifest for a staged, unapproved AgentFit Catalog.

This tool computes hashes only. It neither verifies Client behavior nor approves a release.
"""

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path


REQUIRED = frozenset(
    {
        "capabilities.json",
        "client-capabilities.json",
        "permissions.json",
        "relations.json",
        "support-matrix.json",
        "tools.json",
    }
)
MAX_FILES = 100
MAX_FILE_BYTES = 1_048_576
MAX_TOTAL_BYTES = 8_388_608
MAX_MANIFEST_BYTES = 65_536


def _catalog_path(path: str) -> bool:
    return (
        re.fullmatch(r"[A-Za-z0-9._/-]+", path) is not None
        and not path.startswith("/")
        and "//" not in path
        and not path.endswith("/")
        and all(part not in (".", "..") for part in path.split("/"))
        and (path in REQUIRED or path.startswith(("templates/", "guides/")))
    )


def write_manifest(root: Path, release_id: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}", release_id):
        raise ValueError("invalid release ID")
    if root.is_symlink() or not root.is_dir():
        raise ValueError("release directory unavailable")
    manifest_path = root / "manifest.json"
    if manifest_path.exists() or manifest_path.is_symlink():
        raise ValueError("manifest already exists")

    entries = []
    total_bytes = 0
    seen = set()
    for current, directories, filenames in os.walk(root, followlinks=False):
        for name in directories:
            if (Path(current) / name).is_symlink():
                raise ValueError("catalog symlink present")
        for name in filenames:
            file = Path(current) / name
            path = file.relative_to(root).as_posix()
            if file.is_symlink():
                raise ValueError("catalog symlink present")
            if not _catalog_path(path):
                raise ValueError("unlisted catalog path: " + path)
            if path.casefold() in seen:
                raise ValueError("duplicate catalog path")
            seen.add(path.casefold())
            if not file.is_file() or file.stat().st_size > MAX_FILE_BYTES:
                raise ValueError("catalog file unavailable or oversized")
            content = file.read_bytes()
            content.decode("utf-8", errors="strict")
            total_bytes += len(content)
            if len(content) > MAX_FILE_BYTES or total_bytes > MAX_TOTAL_BYTES:
                raise ValueError("catalog file oversized")
            entries.append({"path": path, "sha256": hashlib.sha256(content).hexdigest()})
            if len(entries) > MAX_FILES:
                raise ValueError("too many catalog files")

    if not REQUIRED.issubset({entry["path"] for entry in entries}):
        raise ValueError("required catalog file missing")
    entries.sort(key=lambda entry: entry["path"])
    preimage = "agentfit-catalog-v1\n" + release_id + "\n"
    preimage += "".join(entry["path"] + "\t" + entry["sha256"] + "\n" for entry in entries)
    catalog_hash = hashlib.sha256(preimage.encode("utf-8")).hexdigest()
    manifest = {
        "schemaVersion": 1,
        "releaseId": release_id,
        "catalogHash": catalog_hash,
        "files": entries,
    }
    body = (json.dumps(manifest, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")
    if len(body) > MAX_MANIFEST_BYTES:
        raise ValueError("catalog manifest oversized")
    with manifest_path.open("xb") as output:
        output.write(body)
    return catalog_hash


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("release_directory", type=Path)
    parser.add_argument("release_id")
    args = parser.parse_args()
    try:
        print(write_manifest(args.release_directory, args.release_id))
        return 0
    except (OSError, UnicodeError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
