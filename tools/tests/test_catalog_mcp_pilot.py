import json
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from catalog_mcp_pilot import PilotFailure, run_pilot


MOCK_SERVER = '''
import json
import sys

for line in sys.stdin:
    request = json.loads(line)
    method = request.get("method")
    if "id" not in request:
        continue
    if method == "initialize":
        result = {"serverInfo": {"name": "Fixture", "version": "1.0"}}
    elif method == "tools/list":
        result = {"tools": [{"name": "browser_navigate"}, {"name": "browser_run_code_unsafe"}]}
    elif method == "tools/call":
        assert request["params"]["name"] == "browser_navigate"
        assert request["params"]["arguments"]["url"].startswith("data:text/html,")
        with open("snapshot.yml", "w", encoding="utf-8") as output:
            output.write('heading "AgentFit Pilot"')
        result = {"content": [{"type": "text", "text": "### Snapshot\\n- [Snapshot](snapshot.yml)"}]}
    else:
        result = {}
    print(json.dumps({"jsonrpc": "2.0", "id": request["id"], "result": result}), flush=True)
'''


class CatalogMcpPilotTest(unittest.TestCase):
    TEMP_ROOT = Path(__file__).resolve().parents[2] / ".pilot-test-tmp"

    @classmethod
    def tearDownClass(cls):
        cls.TEMP_ROOT.rmdir()

    def test_two_fresh_processes_verify_local_navigation_without_installing(self):
        self.TEMP_ROOT.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=self.TEMP_ROOT) as directory:
            server = Path(directory) / "server.py"
            server.write_text(textwrap.dedent(MOCK_SERVER), encoding="utf-8")
            result = run_pilot([sys.executable, str(server)], timeout_seconds=5)

        self.assertEqual(result["runs"], 2)
        self.assertEqual(result["server"], "Fixture")
        self.assertEqual(result["toolCount"], 2)
        self.assertEqual(result["unsafeTools"], ["browser_run_code_unsafe"])
        self.assertEqual(result["status"], "LOCAL_PILOT_ONLY")

    def test_missing_navigation_tool_fails_closed(self):
        self.TEMP_ROOT.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=self.TEMP_ROOT) as directory:
            server = Path(directory) / "server.py"
            server.write_text(textwrap.dedent(MOCK_SERVER).replace('"browser_navigate"}, {"name"', '"other"}, {"name"'), encoding="utf-8")
            with self.assertRaisesRegex(PilotFailure, "browser_navigate"):
                run_pilot([sys.executable, str(server)], timeout_seconds=5)


if __name__ == "__main__":
    unittest.main()
