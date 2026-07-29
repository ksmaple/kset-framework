"""Codex/Cursor 技能目录链接管理。

Windows 使用 Junction（mklink /J）降低权限要求；Unix 使用符号链接。
"""

from __future__ import annotations

import os
import stat
import subprocess
from pathlib import Path

from kaka_scripts.paths import find_repo_root


def is_reparse_or_symlink(path: str | Path) -> bool:
    p = Path(path)
    if not p.exists() and not p.is_symlink():
        return False
    try:
        st = p.lstat()
        if stat.S_ISLNK(st.st_mode):
            return True
        if os.name == "nt":
            try:
                os.readlink(p)
                return True
            except OSError:
                return os.path.realpath(p) != os.path.abspath(p)
    except OSError:
        return False
    return False


def ensure_dir_link(link_path: str | Path, target_path: str | Path) -> str:
    # 使用 absolute() 而非 resolve()，避免把已存在的符号链接解析到 target 路径
    link = Path(link_path).absolute()
    target = Path(target_path).resolve()
    link.parent.mkdir(parents=True, exist_ok=True)

    if link.exists() or link.is_symlink() or is_reparse_or_symlink(link):
        if is_reparse_or_symlink(link):
            return f"OK (exists): {link}"
        st = link.lstat()
        if stat.S_ISDIR(st.st_mode):
            entries = list(link.iterdir())
            if entries:
                raise RuntimeError(f"Path exists and is not empty (not a link): {link}")
            os.rmdir(link)
        else:
            link.unlink()

    if os.name == "nt":
        # Windows Junction 不需要管理员权限
        subprocess.run(
            ["cmd", "/c", "mklink", "/J", str(link), str(target)],
            check=True,
            capture_output=True,
        )
    else:
        os.symlink(target, link, target_is_directory=True)
    return f"Linked: {link} -> {target}"


def setup_ai_env_links(repo_root: str | Path | None = None) -> list[str]:
    root = Path(repo_root) if repo_root else find_repo_root()
    source = (root / ".claude" / "skills").resolve()
    if not source.exists():
        raise RuntimeError(f"Missing source: {source}")

    messages: list[str] = []
    for link in [".agents/skills", ".cursor/skills"]:
        messages.append(ensure_dir_link(root / link, source))
    messages.append("Done. Skills source: .claude/skills")
    return messages
