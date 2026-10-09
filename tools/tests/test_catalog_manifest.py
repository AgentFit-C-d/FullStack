import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "catalog_manifest.py"
REQUIRED = (
    "capabilities.json",
    "client-capabilities.json",
    "permissions.json",
    "relations.json",
    "support-matrix.json",
    "tools.json",
)
KNOWN_HASH = "38e92a97ab9126a133c5f3bb85ebfdd2d55e43c82515d887b6ded60b6698d3a5"


class CatalogManifestCliTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2])
        self.addCleanup(self.temp.cleanup)
        self.release = Path(self.temp.name)
        for name in REQUIRED:
            (self.release / name).write_text("{}", encoding="utf-8")

    def run_cli(self):
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(self.release), "release-test-1"],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_writes_manifest_matching_java_loader_known_vector(self):
        result = self.run_cli()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(KNOWN_HASH, result.stdout.strip())
        manifest = json.loads((self.release / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(1, manifest["schemaVersion"])
        self.assertEqual("release-test-1", manifest["releaseId"])
        self.assertEqual(KNOWN_HASH, manifest["catalogHash"])
        self.assertEqual(list(REQUIRED), [entry["path"] for entry in manifest["files"]])

    def test_rejects_unlisted_file_without_writing_manifest(self):
        (self.release / "notes.txt").write_text("not a catalog file", encoding="utf-8")
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unlisted catalog path", result.stderr)
        self.assertFalse((self.release / "manifest.json").exists())

    def test_does_not_replace_existing_manifest(self):
        existing = self.release / "manifest.json"
        existing.write_text("reviewed manifest", encoding="utf-8")
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("manifest already exists", result.stderr)
        self.assertEqual("reviewed manifest", existing.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
