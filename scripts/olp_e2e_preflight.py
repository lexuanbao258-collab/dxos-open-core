#!/usr/bin/env python3
"""Non-destructive DX-OS Stage 6 / OLP integration preflight.

This script does not provision users, start containers, execute migrations, run
Gradle tests, change files, or grant privileges. A READY result is NOT security
approval or evidence that the E2E workflow has passed.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

EXPECTED_CONTAINER = "dxos-stage6-pg17-disposable"
EXPECTED_DB = "stage6_disposable_test"
EXPECTED_USER = "stage6_test"
EXPECTED_PORT = "55427"
EXPECTED_ADDRESS = "127.0.0.1"
GIT_SHA = re.compile(r"^[a-f0-9]{40}$")
PG17_IMAGE = re.compile(r"^postgres:17(?:[.\-]|$)")


class PreflightBlocked(Exception):
    """Preflight requirement not met; no mutation was attempted."""


def command(args: list[str], *, cwd: Path | None = None, timeout: int = 15) -> str:
    """Run a bounded, explicitly allowlisted read-only inspection command."""
    try:
        process = subprocess.run(
            args, cwd=cwd, capture_output=True, text=True, timeout=timeout, check=False
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PreflightBlocked(f"Command unavailable or timed out: {args[0]}") from exc
    if process.returncode != 0:
        # Never print command stderr: it may contain private operator metadata.
        raise PreflightBlocked(f"Inspection failed (exit {process.returncode}): {args[0]}")
    return process.stdout.strip()


def check_checkout(repo: Path, expected_head: str) -> None:
    if not GIT_SHA.fullmatch(expected_head):
        raise PreflightBlocked("Expected head must be a full 40-character lowercase SHA")
    actual = command(["git", "rev-parse", "HEAD"], cwd=repo)
    if actual != expected_head:
        raise PreflightBlocked("Checkout HEAD differs from the requested pinned HEAD")
    dirty = command(["git", "status", "--porcelain"], cwd=repo)
    if dirty:
        raise PreflightBlocked("Checkout has local changes: preserve them; use a clean review worktree")


def check_java() -> None:
    try:
        process = subprocess.run(
            ["java", "-version"], capture_output=True, text=True, timeout=10, check=False
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PreflightBlocked("Java 21 is unavailable") from exc
    combined = process.stdout + process.stderr
    if process.returncode != 0 or not re.search(r'\bversion\s+"21(?:[.\-]|\")', combined):
        raise PreflightBlocked("Active Java runtime is not JDK 21")


def validate_docker_inspect(document: Any) -> None:
    if not isinstance(document, list) or len(document) != 1:
        raise PreflightBlocked("Container inspection must return exactly one object")
    data = document[0]
    if not isinstance(data, dict):
        raise PreflightBlocked("Invalid container metadata")
    if data.get("Name") != "/" + EXPECTED_CONTAINER:
        raise PreflightBlocked("Unexpected Docker container identity")
    if data.get("State", {}).get("Running") is not True:
        raise PreflightBlocked("Disposable PG17 container is not running")
    image = data.get("Config", {}).get("Image", "")
    if not isinstance(image, str) or not PG17_IMAGE.match(image):
        raise PreflightBlocked("Container image is not identified as PostgreSQL 17")
    env = data.get("Config", {}).get("Env", [])
    if not isinstance(env, list) or f"POSTGRES_DB={EXPECTED_DB}" not in env or f"POSTGRES_USER={EXPECTED_USER}" not in env:
        raise PreflightBlocked("Disposable database identity differs from pinned fixture")
    ports = data.get("NetworkSettings", {}).get("Ports", {}).get("5432/tcp")
    if not isinstance(ports, list) or len(ports) != 1:
        raise PreflightBlocked("Expected exactly one PostgreSQL loopback port mapping")
    port = ports[0]
    if port.get("HostIp") != EXPECTED_ADDRESS or str(port.get("HostPort")) != EXPECTED_PORT:
        raise PreflightBlocked("Docker port must be 127.0.0.1:55427, not a shared/exposed endpoint")
    if data.get("Mounts"):
        raise PreflightBlocked("Disposable container has mounted volumes; provenance requires review")


def check_postgres17() -> None:
    raw = command(["docker", "inspect", "--type", "container", EXPECTED_CONTAINER])
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise PreflightBlocked("Docker inspect did not return valid JSON") from exc
    validate_docker_inspect(data)
    # psql only performs server_version_num and current_database SELECT-style reads.
    version = command([
        "docker", "exec", EXPECTED_CONTAINER, "psql", "-X", "-A", "-t",
        "-U", EXPECTED_USER, "-d", EXPECTED_DB, "-c", "SHOW server_version_num",
    ])
    if not re.fullmatch(r"17\d{4}", version):
        raise PreflightBlocked("Connected database does not report PostgreSQL 17.x")
    name = command([
        "docker", "exec", EXPECTED_CONTAINER, "psql", "-X", "-A", "-t",
        "-U", EXPECTED_USER, "-d", EXPECTED_DB, "-c", "SELECT current_database()",
    ])
    if name != EXPECTED_DB:
        raise PreflightBlocked("Connected database is not the disposable Stage 6 database")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-head", required=True, help="Exact 40-hex Git commit from the Stage 6 candidate")
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args(argv)
    print("OPERATION=READ_ONLY_PREFLIGHT")
    print("STAGE6_D2_D3_D4_ACCEPTANCE=NOT_VERIFIED")
    print("PORTAL_BFF_E2E=NOT_EXECUTED")
    print("LIVE=BLOCKED")
    try:
        check_checkout(args.repo_root.resolve(), args.expected_head)
        print("GIT_PIN_AND_CLEAN=PASS")
        check_java()
        print("JAVA_21=PASS")
        check_postgres17()
        print("DISPOSABLE_PG17_IDENTITY=PASS_READ_ONLY")
    except PreflightBlocked as exc:
        print(f"PREFLIGHT=BLOCKED: {exc}")
        return 2
    print("PREFLIGHT=READY_FOR_MANUAL_TEST_ONLY")
    return 0


if __name__ == "__main__":
    sys.exit(main())
