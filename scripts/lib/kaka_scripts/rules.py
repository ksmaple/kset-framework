"""规则文件相关工具。"""

from __future__ import annotations

import re
from collections.abc import Callable
from pathlib import Path

from kaka_scripts.io import copy_file_utf8_nobom, read_text
from kaka_scripts.paths import ensure_dir

INIT_COPY_RULES = ["kaka-project-rules.md"]

# 平台规则文件名集合，避免本地规则迁移时重复复制
PLATFORM_RULE_FILES = {"kaka-project-rules.md"}

# 本地规则扫描源：(目录构建函数, 优先级数字，越小越优先)
_LOCAL_RULE_SCAN_SOURCES: list[tuple[Callable[[Path], Path], int]] = [
    (lambda root: root / ".kaka" / "rules", 1),
    (lambda root: root / ".rules", 2),
    (lambda root: root / "rules", 4),
]

_RULE_LINE_RE = re.compile(r"^R(\d{3})([a-z])?:", re.MULTILINE)


def copy_rule_file(source: str | Path, dest: str | Path, force: bool = False) -> bool:
    dst = Path(dest)
    if dst.exists() and not force:
        return False
    ensure_dir(dst.parent)
    copy_file_utf8_nobom(source, dest)
    return True


def parse_rule_numbers(text: str) -> list[tuple[int, str | None]]:
    out: list[tuple[int, str | None]] = []
    for m in _RULE_LINE_RE.finditer(text):
        suffix = m.group(2)
        out.append((int(m.group(1)), suffix))
    return out


def max_rule_number(text: str) -> int:
    nums = [n for n, _ in parse_rule_numbers(text)]
    return max(nums) if nums else 0


def next_rule_number(rules_path: str | Path) -> int:
    try:
        return max_rule_number(read_text(rules_path)) + 1
    except Exception:  # noqa: BLE001
        return 1


def rule_ids_in_file(path: str | Path) -> set[str]:
    text = read_text(path)
    return {
        f"R{str(n).zfill(3)}{s or ''}"
        for n, s in parse_rule_numbers(text)
    }


def scan_local_rule_files(target_root: str | Path) -> list[tuple[Path, str, int]]:
    """扫描目标项目已有的本地规则文件。

    按 LOCAL_RULE_SCAN_SOURCES 配置的优先级扫描目录型源，并扫描根目录下
    `*.rules.md` 文件。同名文件保留优先级最高者。

    返回：[(源文件路径, 目标文件名, 优先级), ...]，按优先级升序排列。
    """
    root = Path(target_root).resolve()
    found: dict[str, tuple[Path, str, int]] = {}

    for path_builder, priority in _LOCAL_RULE_SCAN_SOURCES:
        src_dir = path_builder(root)
        if not src_dir.exists() or not src_dir.is_dir():
            continue
        for file_path in src_dir.iterdir():
            if (
                file_path.is_file()
                and file_path.suffix == ".md"
                and not file_path.name.startswith(".")
                and file_path.name not in PLATFORM_RULE_FILES
            ):
                prev = found.get(file_path.name)
                if prev is None or priority < prev[2]:
                    found[file_path.name] = (file_path, file_path.name, priority)

    for file_path in sorted(root.glob("*.rules.md")):
        if (
            file_path.is_file()
            and not file_path.name.startswith(".")
            and file_path.name not in PLATFORM_RULE_FILES
        ):
            priority = 3
            prev = found.get(file_path.name)
            if prev is None or priority < prev[2]:
                found[file_path.name] = (file_path, file_path.name, priority)

    return sorted(found.values(), key=lambda item: item[2])


def migrate_local_rules(
    target_project_root: str | Path,
    dest_rules_root: str | Path,
    force: bool = False,
) -> list[tuple[str, bool, str | None]]:
    """将目标项目已有的本地规则文件迁移到 .claude/rules/。

    参数：
        target_project_root: 目标项目根目录，用于扫描本地规则源。
        dest_rules_root: 目标 .claude/rules/ 目录。
        force: 是否强制覆盖目标已存在的同名文件。

    返回：[(文件名, 是否成功, 错误信息或 None), ...]
    """
    scanned = scan_local_rule_files(target_project_root)
    results: list[tuple[str, bool, str | None]] = []

    for src_path, file_name, _priority in scanned:
        dest_path = Path(dest_rules_root) / file_name
        try:
            if dest_path.exists() and not force:
                results.append((file_name, False, "目标已存在（跳过，可用 --force 覆盖）"))
                continue
            copy_rule_file(src_path, dest_path, force=True)
            results.append((file_name, True, None))
        except Exception as exc:  # noqa: BLE001
            results.append((file_name, False, str(exc)))

    return results
