"""路径与仓库根定位工具。"""

from __future__ import annotations

import os
from pathlib import Path

GIT_MARKER = ".git"


def find_repo_root(start: str | Path | None = None) -> Path:
    cur = Path(start or os.getcwd()).resolve()
    while True:
        if (cur / GIT_MARKER).exists():
            return cur
        if (cur / ".claude" / "skills").exists():
            return cur
        parent = cur.parent
        if parent == cur:
            raise RuntimeError(f"repo root not found from {cur}")
        cur = parent


def claude_dir(repo_root: str | Path | None = None) -> Path:
    root = Path(repo_root) if repo_root else find_repo_root()
    return root.resolve() / ".claude"


def skills_dir(repo_root: str | Path | None = None) -> Path:
    return claude_dir(repo_root) / "skills"


def rules_dir(repo_root: str | Path | None = None) -> Path:
    return claude_dir(repo_root) / "rules"


def scripts_dir(repo_root: str | Path | None = None) -> Path:
    root = Path(repo_root) if repo_root else find_repo_root()
    return root.resolve() / "scripts"


def ensure_dir(path: str | Path) -> Path:
    p = Path(path)
    p.mkdir(parents=True, exist_ok=True)
    return p


def rel_posix(path: str | Path, base: str | Path) -> str:
    rel = Path(path).resolve().relative_to(Path(base).resolve())
    return rel.as_posix()
