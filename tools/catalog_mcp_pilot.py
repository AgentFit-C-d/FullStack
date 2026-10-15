"""Repeat an isolated, local-only MCP smoke test; never approves a Catalog release.

Example: python tools/catalog_mcp_pilot.py -- node <isolated-prefix>/node_modules/@playwright/mcp/cli.js --browser msedge --headless --isolated
The caller installs a pinned package in a disposable prefix. This command does not
install packages, change Claude Code settings, or open a remote page.
"""

import argparse
import hashlib
import json
import queue
import re
import subprocess
import tempfile
import threading
from pathlib import Path
from urllib.parse import quote


class PilotFailure(RuntimeError):
    pass


PAGE = "data:text/html," + quote("<h1>AgentFit Pilot</h1>")


def _one_run(command, timeout_seconds):
    # The MCP server writes snapshots under its cwd. Keep all generated output
    # in one disposable directory rather than the user's project or home.
    working_dir = Path.cwd()
    scratch = tempfile.TemporaryDirectory(dir=working_dir)
    isolated_dir = Path(scratch.name)
    resolved_command = [str((working_dir / arg).resolve()) if Path(arg).exists() else arg for arg in command]
    process = subprocess.Popen(
        resolved_command,
        cwd=isolated_dir,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        bufsize=1,
    )
    lines = queue.Queue()
    reader = threading.Thread(target=lambda: [lines.put(line) for line in process.stdout], daemon=True)
    reader.start()
    next_id = 0

    def request(method, params=None):
        nonlocal next_id
        next_id += 1
        message = {"jsonrpc": "2.0", "id": next_id, "method": method}
        if params is not None:
            message["params"] = params
        process.stdin.write(json.dumps(message) + "\n")
        process.stdin.flush()
        while True:
            try:
                line = lines.get(timeout=timeout_seconds)
            except queue.Empty as error:
                raise PilotFailure(f"{method}: timed out") from error
            response = json.loads(line)
            if response.get("id") != next_id:
                continue
            if "error" in response:
                raise PilotFailure(f"{method}: server returned an error")
            if not isinstance(response.get("result"), dict):
                raise PilotFailure(f"{method}: invalid response")
            return response["result"]

    try:
        initialized = request("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "agentfit-local-pilot", "version": "1.0"},
        })
        info = initialized.get("serverInfo")
        if not isinstance(info, dict) or not info.get("name"):
            raise PilotFailure("initialize: missing server identity")
        process.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized"}) + "\n")
        process.stdin.flush()
        listed = request("tools/list", {})
        tools = listed.get("tools")
        if not isinstance(tools, list):
            raise PilotFailure("tools/list: invalid tools")
        names = [tool.get("name") for tool in tools if isinstance(tool, dict)]
        if "browser_navigate" not in names:
            raise PilotFailure("tools/list: missing browser_navigate")
        navigated = request("tools/call", {"name": "browser_navigate", "arguments": {"url": PAGE}})
        if navigated.get("isError"):
            raise PilotFailure("browser_navigate: tool failed")
        content = navigated.get("content")
        if not isinstance(content, list):
            raise PilotFailure("browser_navigate: missing content")
        observed = [part.get("text", "") for part in content if isinstance(part, dict)]
        for message in tuple(observed):
            match = re.search(r"\[Snapshot\]\(([^)]+)\)", message)
            if not match:
                continue
            snapshot = (isolated_dir / match.group(1)).resolve()
            if not snapshot.is_relative_to(isolated_dir.resolve()) or not snapshot.is_file():
                raise PilotFailure("browser_navigate: unsafe or missing snapshot path")
            observed.append(snapshot.read_text(encoding="utf-8"))
        if not any('heading "AgentFit Pilot"' in item for item in observed):
            raise PilotFailure("browser_navigate: local heading missing")
        return {"server": info["name"], "version": info.get("version"),
                "toolCount": len(tools), "unsafeTools": sorted(name for name in names if name and "unsafe" in name),
                "toolListHash": hashlib.sha256(json.dumps(tools, sort_keys=True,
                    ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()}
    finally:
        process.kill()
        process.communicate(timeout=timeout_seconds)
        scratch.cleanup()


def run_pilot(command, timeout_seconds=30):
    if not command or timeout_seconds <= 0:
        raise ValueError("command and positive timeout are required")
    first = _one_run(command, timeout_seconds)
    second = _one_run(command, timeout_seconds)
    if first != second:
        raise PilotFailure("MCP identity or tool list changed after restart")
    first.pop("toolListHash")
    return {"status": "LOCAL_PILOT_ONLY", "runs": 2, **first}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    try:
        print(json.dumps(run_pilot(command, args.timeout), ensure_ascii=False, sort_keys=True))
    except (PilotFailure, OSError, ValueError) as error:
        parser.exit(1, f"LOCAL_PILOT_FAILED: {error}\n")


if __name__ == "__main__":
    main()
