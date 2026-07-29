"""Git 操作封装。"""

from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Sequence


def run_git(args: Sequence[str], cwd: str | Path | None = None, check: bool = True) -> str:
    cmd = ["git", *args]
    kwargs: dict[str, object] = {
        "capture_output": True,
        "text": True,
        "encoding": "utf-8",
    }
    if cwd is not None:
        kwargs["cwd"] = Path(cwd).resolve()
    result = subprocess.run(cmd, **kwargs)  # noqa: S603
    if check and result.returncode != 0:
        stderr = result.stderr or result.stdout or ""
        raise RuntimeError(f"git {' '.join(args)} failed: {stderr}")
    return (result.stdout or "").strip()


def git_status_porcelain(cwd: str | Path | None = None) -> list[str]:
    out = run_git(["status", "--porcelain"], cwd)
    return [line for line in out.splitlines() if line.strip()]


def changed_paths_from_porcelain(lines: Sequence[str]) -> list[str]:
    paths: list[str] = []
    for line in lines:
        if len(line) < 4:
            continue
        entry = line[3:].strip()
        if " -> " in entry:
            entry = entry.split(" -> ")[-1]
        paths.append(entry)
    return paths


def current_branch(cwd: str | Path | None = None) -> str:
    return run_git(["branch", "--show-current"], cwd)


def git_diff_check(cwd: str | Path | None = None) -> str:
    cmd = ["git", "diff", "--check"]
    kwargs: dict[str, object] = {
        "capture_output": True,
        "text": True,
        "encoding": "utf-8",
    }
    if cwd is not None:
        kwargs["cwd"] = Path(cwd).resolve()
    result = subprocess.run(cmd, **kwargs)  # noqa: S603
    return f"{result.stdout or ''}{result.stderr or ''}"
