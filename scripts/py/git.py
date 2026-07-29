#!/usr/bin/env python3
"""统一 Git 辅助入口。

子命令：changed-paths、recent-files、cleanup-merged
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))

from kaka_scripts.cli import configure_utf8_streams, die
from kaka_scripts.git import (
    changed_paths_from_porcelain,
    current_branch,
    git_status_porcelain,
    run_git,
)


def cmd_changed_paths(args: argparse.Namespace) -> int:
    cwd = Path(args.cwd).resolve() if args.cwd else None
    lines = git_status_porcelain(cwd)
    for path in changed_paths_from_porcelain(lines):
        print(path)
    return 0


def cmd_recent_files(args: argparse.Namespace) -> int:
    cwd = Path(args.cwd).resolve() if args.cwd else None
    out = run_git(
        ["log", f"-{args.n}", "--pretty=format:", "--name-only"],
        cwd=cwd,
        check=False,
    )
    seen: set[str] = set()
    for line in out.splitlines():
        line = line.strip()
        if line and line not in seen:
            seen.add(line)
            print(line)
    return 0


def _merged_branches(base: str, cwd: Path | None = None) -> list[str]:
    out = run_git(["branch", "--merged", base], cwd=cwd)
    branches: list[str] = []
    for line in out.splitlines():
        line = line.strip().lstrip("* ")
        if line and line != base:
            branches.append(line)
    return branches


def cmd_cleanup_merged(args: argparse.Namespace) -> int:
    cwd = Path(args.cwd).resolve() if args.cwd else None
    base = args.base or current_branch(cwd)
    branches = _merged_branches(base, cwd)

    if not branches:
        print(f"No local branches merged into {base}.")
        return 0

    for branch in branches:
        print(branch)

    if args.delete:
        for branch in branches:
            try:
                run_git(["branch", "-d", branch], cwd=cwd)
                print(f"Deleted: {branch}")
            except RuntimeError as exc:
                print(f"Failed to delete {branch}: {exc}", file=sys.stderr)
    else:
        print(f"\nUse --delete to remove these {len(branches)} branch(es) merged into {base}.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="git.py", description="统一 Git 辅助入口")
    sub = parser.add_subparsers(dest="command", required=True)

    p_cp = sub.add_parser("changed-paths", help="输出 git status --porcelain 中的变更路径")
    p_cp.add_argument("--cwd", help="git 工作目录")
    p_cp.set_defaults(func=cmd_changed_paths)

    p_rf = sub.add_parser("recent-files", help="最近 N 次提交涉及的文件")
    p_rf.add_argument("-n", type=int, default=5, help="提交数量（默认 5）")
    p_rf.add_argument("--cwd", help="git 工作目录")
    p_rf.set_defaults(func=cmd_recent_files)

    p_cm = sub.add_parser("cleanup-merged", help="列出/清理已合并分支")
    p_cm.add_argument("--base", default="", help="集成分支（默认当前分支）")
    p_cm.add_argument("--delete", action="store_true", help="删除这些分支")
    p_cm.add_argument("--cwd", help="git 工作目录")
    p_cm.set_defaults(func=cmd_cleanup_merged)

    return parser


def main() -> int:
    configure_utf8_streams()
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
