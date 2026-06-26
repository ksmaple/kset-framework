#!/usr/bin/env python3
"""统一质量检查入口。

子命令：env、utf8、trailing-whitespace、rule-ids、init-output
"""

from __future__ import annotations

import argparse
import platform
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))

from kaka_scripts.cli import configure_utf8_streams, die
from kaka_scripts.env import SCRIPT_RUNTIME, check_script_runtime
from kaka_scripts.io import has_utf8_bom, strip_utf8_bom
from kaka_scripts.paths import find_repo_root, rules_dir, scripts_dir, skills_dir
from kaka_scripts.project_init import verify_init_outputs
from kaka_scripts.rules import max_rule_number, parse_rule_numbers

TEXT_EXT = re.compile(r"\.(md|py|ts|json|mdc|ya?ml)$", re.IGNORECASE)
SKIP_DIRS = {
    ".git",
    ".mypy_cache",
    ".pytest_cache",
    ".venv",
    "__pycache__",
    "node_modules",
    "vendor",
}


def _run(cmd: list[str]) -> tuple[bool, str]:
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8", check=False
        )
        out = f"{result.stdout or ''}{result.stderr or ''}".strip()
        return result.returncode == 0, out
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)


def _walk_text_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for item in root.rglob("*"):
        if any(part in SKIP_DIRS for part in item.parts):
            continue
        if item.is_file() and TEXT_EXT.search(item.name):
            files.append(item)
    return files


def cmd_env(args: argparse.Namespace) -> int:
    repo_root_found = False
    try:
        repo_root = find_repo_root()
        repo_root_found = True
    except RuntimeError:
        repo_root = Path.cwd().resolve()

    python_v = platform.python_version()
    git_ok, git_out = _run(["git", "--version"])

    issues = check_script_runtime(
        python_version=python_v,
        repo_root_found=repo_root_found,
        git_available=git_ok,
    )

    scripts_root = scripts_dir(repo_root)

    print("## scripts 运行环境检测\n")
    print(f"- Python: {python_v}（要求 >= {SCRIPT_RUNTIME['pythonMin']}，推荐 {SCRIPT_RUNTIME['pythonRecommended']}）")
    print(f"- scripts 根: {scripts_root}")
    print(f"- 仓库根: {repo_root if repo_root_found else '(未识别)'}")
    print(f"- git: {git_out.splitlines()[0] if git_ok else '未找到'}\n")

    if not issues:
        print("环境检查通过。")
        return 0

    for issue in issues:
        print(f"[{issue.level.upper()}] {issue.message}")
        if issue.hint:
            print(f"  → {issue.hint}")
    print("\n详见 scripts/README.md「运行环境」「常见异常」。")
    return 1 if any(i.level == "error" for i in issues) else 0


def cmd_utf8(args: argparse.Namespace) -> int:
    root = Path(args.repo_root).resolve() if args.repo_root else find_repo_root()
    files = _walk_text_files(root / ".claude") + _walk_text_files(root / "scripts")

    bom_files: list[str] = []
    for p in files:
        buf = p.read_bytes()
        if not has_utf8_bom(buf):
            continue
        rel = p.relative_to(root).as_posix()
        bom_files.append(rel)
        if args.fix:
            p.write_bytes(strip_utf8_bom(buf))

    if not bom_files:
        print(f"OK: {len(files)} text files, no UTF-8 BOM.")
        return 0

    print(f"UTF-8 BOM found: {len(bom_files)}", file=sys.stderr)
    for f in bom_files:
        print(f"  {f}", file=sys.stderr)
    if args.fix:
        print(f"Fixed {len(bom_files)} file(s).")
        return 0
    return 1


def _process_trailing_whitespace(path: Path, fix: bool) -> tuple[bool, int]:
    raw = path.read_bytes()
    newline = "\r\n" if b"\r\n" in raw else None
    text = raw.decode("utf-8-sig")
    lines = text.splitlines(keepends=True)
    fixed_lines: list[str] = []
    dirty = False
    count = 0
    for line in lines:
        stripped = line.rstrip(" \t\r\n")
        if stripped != line.rstrip("\r\n"):
            dirty = True
            count += 1
            if line.endswith("\r\n"):
                fixed_lines.append(stripped + "\r\n")
            elif line.endswith("\n"):
                fixed_lines.append(stripped + "\n")
            elif line.endswith("\r"):
                fixed_lines.append(stripped + "\r")
            else:
                fixed_lines.append(stripped)
        else:
            fixed_lines.append(line)

    if dirty and fix:
        path.write_text("".join(fixed_lines), encoding="utf-8", newline=newline or "")
    return dirty, count


def cmd_trailing_whitespace(args: argparse.Namespace) -> int:
    root = Path(args.repo_root).resolve() if args.repo_root else find_repo_root()
    files = _walk_text_files(root / ".claude") + _walk_text_files(root / "scripts")

    offenders: list[tuple[str, int]] = []
    total = 0
    for p in files:
        dirty, count = _process_trailing_whitespace(p, args.fix)
        if dirty:
            rel = p.relative_to(root).as_posix()
            offenders.append((rel, count))
            total += count

    if not offenders:
        print(f"OK: {len(files)} text files, no trailing whitespace.")
        return 0

    if args.fix:
        print(f"Fixed {total} trailing whitespace(s) in {len(offenders)} file(s).")
    else:
        print(f"Trailing whitespace found: {total} line(s) in {len(offenders)} file(s).", file=sys.stderr)
        for rel, count in offenders:
            print(f"  {rel}: {count}", file=sys.stderr)
        return 1
    return 0


def cmd_rule_ids(args: argparse.Namespace) -> int:
    root = Path(args.repo_root).resolve() if args.repo_root else find_repo_root()
    rules_root = rules_dir(root)
    if not rules_root.exists():
        print(f"Rules directory not found: {rules_root}", file=sys.stderr)
        return 1

    files = sorted(p for p in rules_root.iterdir() if p.is_file() and p.suffix == ".md")
    all_numbers: list[int] = []
    per_file: dict[Path, list[tuple[int, str | None]]] = {}
    has_error = False

    for path in files:
        nums = parse_rule_numbers(path.read_text(encoding="utf-8"))
        per_file[path] = nums
        for n, _ in nums:
            all_numbers.append(n)

    id_locations: dict[str, list[str]] = defaultdict(list)
    for path, nums in per_file.items():
        for n, suffix in nums:
            rid = f"R{str(n).zfill(3)}{suffix or ''}"
            id_locations[rid].append(path.name)
    for rid, locs in id_locations.items():
        if len(locs) > 1:
            print(f"[ERROR] {rid} appears in {len(locs)} files: {', '.join(locs)}", file=sys.stderr)
            has_error = True

    for path, nums in per_file.items():
        seen_full: dict[str, int] = defaultdict(int)
        for n, suffix in nums:
            rid = f"R{str(n).zfill(3)}{suffix or ''}"
            seen_full[rid] += 1
        for rid, count in seen_full.items():
            if count > 1:
                print(f"[ERROR] {path.name}: {rid} appears {count} times", file=sys.stderr)
                has_error = True

        seen_main = {n for n, _ in nums}
        file_max = max((n for n, _ in nums), default=0)
        missing = [n for n in range(1, file_max + 1) if n not in seen_main]
        if missing:
            print(f"[WARN] {path.name}: missing numbers {', '.join(f'R{str(n).zfill(3)}' for n in missing)}")

    if not has_error:
        next_num = (max(all_numbers) if all_numbers else 0) + 1
        print(f"OK: {len(files)} rule file(s), {len(all_numbers)} rule number(s).")
        print(f"Next available rule number: R{str(next_num).zfill(3)}")
        return 0
    return 1


def cmd_init_output(args: argparse.Namespace) -> int:
    root = Path(args.project_root).resolve() if args.project_root else find_repo_root()
    result = verify_init_outputs(root, proj=args.proj or None)
    if result["ok"]:
        print(f"OK: init outputs verified for {result['proj']}")
        return 0
    print(f"ERROR: init outputs incomplete for {result['proj']}", file=sys.stderr)
    for item in result["missing"]:
        print(f"  missing: {item}", file=sys.stderr)
    if not result["projectSpecHasDatetimeSection"]:
        print("  missing: project-spec 时间格式（前后端）章节", file=sys.stderr)
    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="check.py", description="统一质量检查入口")
    sub = parser.add_subparsers(dest="command", required=True)

    p_env = sub.add_parser("env", help="检测 Python/git/仓库根")
    p_env.set_defaults(func=cmd_env)

    p_utf8 = sub.add_parser("utf8", help="扫描 UTF-8 BOM")
    p_utf8.add_argument("--fix", action="store_true", help="去除检测到的 BOM")
    p_utf8.add_argument("--repo-root", help="仓库根目录")
    p_utf8.set_defaults(func=cmd_utf8)

    p_tw = sub.add_parser("trailing-whitespace", help="检查行尾空格")
    p_tw.add_argument("--fix", action="store_true", help="自动清理")
    p_tw.add_argument("--repo-root", help="仓库根目录")
    p_tw.set_defaults(func=cmd_trailing_whitespace)

    p_rid = sub.add_parser("rule-ids", help="检查规则 R 编号")
    p_rid.add_argument("--repo-root", help="仓库根目录")
    p_rid.set_defaults(func=cmd_rule_ids)

    p_init = sub.add_parser("init-output", help="检查 init 产物完整性")
    p_init.add_argument("--project-root", help="目标项目根目录")
    p_init.add_argument("--proj", help="项目标识（默认取项目目录名）")
    p_init.set_defaults(func=cmd_init_output)

    return parser


def main() -> int:
    configure_utf8_streams()
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
