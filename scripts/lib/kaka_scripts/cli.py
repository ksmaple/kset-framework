"""命令行公共工具：错误输出、退出、路径校验。"""

from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import NoReturn

__all__ = ["eprint", "die", "resolve_project_root", "coerce_dir_path", "configure_utf8_streams"]


def configure_utf8_streams() -> None:
    """将 stdout/stderr 显式重配置为 UTF-8，避免 Windows 终端默认编码导致中文乱码。"""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


def eprint(message: str, level: str = "info") -> None:
    prefix = {"info": "", "warn": "WARN: ", "error": "ERROR: "}.get(level, "")
    print(f"{prefix}{message}", file=sys.stderr)


def die(message: str, code: int = 1) -> NoReturn:
    eprint(message, "error")
    sys.exit(code)


def resolve_project_root(project_root: str | Path | None = None) -> Path:
    return Path(project_root or os.getcwd()).resolve()


def coerce_dir_path(value: str | Path, label: str) -> Path:
    path = Path(value).resolve()
    if not path.exists():
        die(f"{label}: path does not exist: {path}")
    if not path.is_dir():
        die(f"{label}: not a directory: {path}")
    return path
