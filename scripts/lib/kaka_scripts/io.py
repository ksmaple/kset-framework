"""统一 UTF-8 文件读写（无 BOM）与 BOM 处理。

遵守 kaka-project-rules R020、R037a。
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ENCODING = "utf-8"
UTF8_BOM = b"\xef\xbb\xbf"


def strip_utf8_bom(buf: bytes) -> bytes:
    if len(buf) >= 3 and buf[:3] == UTF8_BOM:
        return buf[3:]
    return buf


def has_utf8_bom(buf: bytes) -> bool:
    return len(buf) >= 3 and buf[:3] == UTF8_BOM


def read_text(path: str | Path) -> str:
    raw = Path(path).read_bytes()
    return strip_utf8_bom(raw).decode(ENCODING)


def write_text(path: str | Path, content: str) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding=ENCODING, newline="")


def copy_file_utf8_nobom(source: str | Path, dest: str | Path) -> None:
    """复制文本文件并去除 UTF-8 BOM（init 同步默认行为）。"""
    src = Path(source)
    dst = Path(dest)
    dst.parent.mkdir(parents=True, exist_ok=True)
    raw = strip_utf8_bom(src.read_bytes())
    dst.write_bytes(raw)


def copy_tree_utf8_nobom(source: str | Path, dest: str | Path) -> None:
    """复制目录树内全部文件并去除 UTF-8 BOM（init 技能同步默认行为）。"""
    src = Path(source)
    dst = Path(dest)
    dst.mkdir(parents=True, exist_ok=True)
    for item in src.rglob("*"):
        if item.is_file():
            rel = item.relative_to(src)
            copy_file_utf8_nobom(item, dst / rel)


def read_json(path: str | Path) -> Any:
    return json.loads(read_text(path))


def write_json(path: str | Path, data: Any, indent: int = 2) -> None:
    text = json.dumps(data, ensure_ascii=False, indent=indent) + "\n"
    write_text(path, text)
