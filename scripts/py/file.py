#!/usr/bin/env python3
"""统一文件操作入口。

子命令：create、touch、delete、copy、move、replace、append、insert、remove-lines
"""

from __future__ import annotations

import argparse
import difflib
import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))

from kaka_scripts.cli import configure_utf8_streams, die
from kaka_scripts.io import (
    copy_file_utf8_nobom,
    copy_tree_utf8_nobom,
    read_text,
    write_text,
)


def _read_or_text(args: argparse.Namespace) -> str:
    if args.template:
        return read_text(args.template)
    text = args.text or ""
    if not text.endswith("\n"):
        text += "\n"
    return text


def _preview(old: str, new: str, max_lines: int = 40) -> str:
    old_lines = old.splitlines(keepends=True)
    new_lines = new.splitlines(keepends=True)
    diff = list(difflib.unified_diff(old_lines, new_lines, lineterm=""))
    if len(diff) > max_lines:
        head = diff[: max_lines // 2]
        tail = diff[-max_lines // 2 :]
        diff = head + ["\n... (truncated) ...\n\n"] + tail
    return "".join(diff)


def cmd_create(args: argparse.Namespace) -> int:
    dest = Path(args.path)
    if dest.exists() and not args.force:
        die(f"File already exists: {dest} (use --force to overwrite)")
    content = args.content if args.content is not None else ""
    write_text(dest, content)
    print(f"Created: {dest.resolve()}")
    return 0


def cmd_touch(args: argparse.Namespace) -> int:
    target = Path(args.path)
    if args.create_dirs:
        target.parent.mkdir(parents=True, exist_ok=True)
    target.touch(exist_ok=True)
    print(f"Touched: {target.resolve()}")
    return 0


def cmd_delete(args: argparse.Namespace) -> int:
    target = Path(args.path)
    if not target.exists() and not target.is_symlink():
        die(f"Path does not exist: {target}")
    if not args.yes:
        die(f"Use --yes to confirm deletion: {target}")

    if target.is_symlink() or target.is_file():
        target.unlink()
    elif target.is_dir():
        if args.recursive:
            shutil.rmtree(target)
        else:
            try:
                target.rmdir()
            except OSError as exc:
                die(f"Directory not empty (use --recursive): {exc}")
    else:
        die(f"Unsupported path type: {target}")
    print(f"Deleted: {target}")
    return 0


def cmd_copy(args: argparse.Namespace) -> int:
    src = Path(args.source)
    dst = Path(args.dest)
    if not src.exists():
        die(f"Source not found: {src}")
    if dst.exists() and not args.force:
        die(f"Destination already exists: {dst} (use --force)")

    if src.is_file():
        if args.force and dst.exists():
            dst.unlink() if dst.is_file() else shutil.rmtree(dst)
        if args.keep_bom:
            shutil.copy2(src, dst)
        else:
            copy_file_utf8_nobom(src, dst)
    elif src.is_dir():
        if not args.tree:
            die(f"Source is a directory; use --tree to copy recursively: {src}")
        if args.force and dst.exists():
            shutil.rmtree(dst)
        if args.keep_bom:
            shutil.copytree(src, dst)
        else:
            copy_tree_utf8_nobom(src, dst)
    else:
        die(f"Unsupported source type: {src}")
    print(f"Copied: {src} -> {dst}")
    return 0


def cmd_move(args: argparse.Namespace) -> int:
    src = Path(args.source)
    dst = Path(args.dest)
    if not src.exists():
        die(f"Source not found: {src}")
    if dst.exists() and not args.force:
        die(f"Destination already exists: {dst} (use --force)")
    shutil.move(str(src), str(dst))
    print(f"Moved: {src} -> {dst}")
    return 0


def cmd_replace(args: argparse.Namespace) -> int:
    target = Path(args.path)
    if not target.exists():
        die(f"File not found: {target}")
    original = read_text(target)
    if args.regex:
        try:
            pattern = re.compile(args.old)
        except re.error as exc:
            die(f"Invalid regex: {exc}")
        result, count = pattern.subn(args.new, original, count=args.count or 0)
    else:
        count = original.count(args.old) if args.count == 0 else min(original.count(args.old), args.count)
        result = original.replace(args.old, args.new, args.count)

    if count == 0:
        print("No occurrences found.")
        return 0

    if not args.apply:
        print(f"Found {count} occurrence(s). Preview diff (use --apply to write):")
        print(_preview(original, result))
        return 0

    write_text(target, result)
    print(f"Replaced {count} occurrence(s) in {target}")
    return 0


def cmd_append(args: argparse.Namespace) -> int:
    target = Path(args.path)
    append_text = _read_or_text(args)
    if args.no_final_newline:
        append_text = append_text.rstrip("\n")

    existing = read_text(target) if target.exists() else ""
    if args.ensure_blank_line and existing and not existing.endswith("\n\n"):
        existing += "\n" if existing.endswith("\n") else "\n\n"

    write_text(target, existing + append_text)
    print(f"Appended to: {target.resolve()}")
    return 0


def cmd_insert(args: argparse.Namespace) -> int:
    target = Path(args.path)
    if not target.exists():
        die(f"File not found: {target}")

    insert_text = _read_or_text(args)
    original = read_text(target)
    lines = original.splitlines(keepends=True)

    if args.line is not None:
        idx = max(0, min(args.line - 1, len(lines)))
        lines.insert(idx, insert_text)
        pos_desc = f"line {args.line}"
    else:
        pattern = re.compile(args.after or args.before) if args.regex else (args.after or args.before)
        before = args.before is not None
        inserted = False
        for i, line in enumerate(lines, start=1):
            match = pattern.search(line) if args.regex else (pattern in line)
            if match:
                idx = i if before else i + 1
                lines.insert(idx, insert_text)
                inserted = True
                pos_desc = f"{'before' if before else 'after'} line {i}"
                break
        if not inserted:
            die(f"Pattern not found: {pattern}")

    write_text(target, "".join(lines))
    print(f"Inserted at {pos_desc}: {target.resolve()}")
    return 0


def cmd_remove_lines(args: argparse.Namespace) -> int:
    target = Path(args.path)
    if not target.exists():
        die(f"File not found: {target}")

    original = read_text(target)
    lines = original.splitlines(keepends=True)
    removed: list[int] = []

    if args.pattern is not None:
        compiled = re.compile(args.pattern) if args.regex else None
        new_lines: list[str] = []
        for i, line in enumerate(lines, start=1):
            match = compiled.search(line) if compiled else args.pattern in line
            if match:
                removed.append(i)
                continue
            new_lines.append(line)
        lines = new_lines
    else:
        start = max(1, args.start)
        end = min(len(lines), args.end)
        removed = list(range(start, end + 1))
        lines = lines[: start - 1] + lines[end:]

    if not removed:
        print("No lines removed.")
        return 0

    result = "".join(lines)
    if not args.apply:
        print(f"Will remove {len(removed)} line(s): {removed}\nUse --apply to write.")
        return 0

    write_text(target, result)
    print(f"Removed {len(removed)} line(s) from {target}: {removed}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="file.py", description="统一文件操作入口")
    sub = parser.add_subparsers(dest="command", required=True)

    p_create = sub.add_parser("create", help="创建文件")
    p_create.add_argument("path", help="目标文件路径")
    p_create.add_argument("--content", default="", help="文件内容")
    p_create.add_argument("--from", dest="template", help="模板文件路径")
    p_create.add_argument("--force", action="store_true", help="覆盖已存在文件")
    p_create.set_defaults(func=cmd_create)

    p_touch = sub.add_parser("touch", help="创建空文件或更新 mtime")
    p_touch.add_argument("path", help="目标文件路径")
    p_touch.add_argument("--create-dirs", action="store_true", help="自动创建父目录")
    p_touch.set_defaults(func=cmd_touch)

    p_delete = sub.add_parser("delete", help="删除文件或目录")
    p_delete.add_argument("path", help="要删除的路径")
    p_delete.add_argument("--recursive", "-r", action="store_true", help="递归删除非空目录")
    p_delete.add_argument("--yes", "-y", action="store_true", help="跳过确认")
    p_delete.set_defaults(func=cmd_delete)

    p_copy = sub.add_parser("copy", help="复制文件或目录")
    p_copy.add_argument("source", help="源路径")
    p_copy.add_argument("dest", help="目标路径")
    p_copy.add_argument("--tree", "-r", action="store_true", help="递归复制目录")
    p_copy.add_argument("--keep-bom", action="store_true", help="保留 UTF-8 BOM")
    p_copy.add_argument("--force", action="store_true", help="覆盖已存在目标")
    p_copy.set_defaults(func=cmd_copy)

    p_move = sub.add_parser("move", help="移动或重命名")
    p_move.add_argument("source", help="源路径")
    p_move.add_argument("dest", help="目标路径")
    p_move.add_argument("--force", action="store_true", help="覆盖已存在目标")
    p_move.set_defaults(func=cmd_move)

    p_replace = sub.add_parser("replace", help="替换文件内文本")
    p_replace.add_argument("path", help="目标文件路径")
    p_replace.add_argument("--old", required=True, help="要替换的文本或正则")
    p_replace.add_argument("--new", default="", help="替换后的文本")
    p_replace.add_argument("--regex", action="store_true", help="将 --old 视为正则")
    p_replace.add_argument("--count", type=int, default=0, help="最大替换次数")
    p_replace.add_argument("--apply", action="store_true", help="实际写入")
    p_replace.set_defaults(func=cmd_replace)

    p_append = sub.add_parser("append", help="追加文本到文件末尾")
    p_append.add_argument("path", help="目标文件路径")
    g_append = p_append.add_mutually_exclusive_group(required=True)
    g_append.add_argument("--text", help="要追加的文本")
    g_append.add_argument("--from", dest="template", help="来源文件路径")
    p_append.add_argument("--ensure-blank-line", action="store_true", help="追加前确保空行")
    p_append.add_argument("--no-final-newline", action="store_true", help="追加后不加换行")
    p_append.set_defaults(func=cmd_append)

    p_insert = sub.add_parser("insert", help="在指定位置插入文本")
    p_insert.add_argument("path", help="目标文件路径")
    g_insert = p_insert.add_mutually_exclusive_group(required=True)
    g_insert.add_argument("--text", help="要插入的文本")
    g_insert.add_argument("--from", dest="template", help="来源文件路径")
    p_insert.add_argument("--line", type=int, help="插入到指定行号之前")
    p_insert.add_argument("--after", help="插入到首次匹配行之后")
    p_insert.add_argument("--before", help="插入到首次匹配行之前")
    p_insert.add_argument("--regex", action="store_true", help="将 --after/--before 视为正则")
    p_insert.set_defaults(func=cmd_insert)

    p_remove = sub.add_parser("remove-lines", help="删除匹配行或行范围")
    p_remove.add_argument("path", help="目标文件路径")
    p_remove.add_argument("--pattern", help="匹配行内容的文本或正则")
    p_remove.add_argument("--regex", action="store_true", help="将 --pattern 视为正则")
    p_remove.add_argument("--start", type=int, help="起始行号（1-based）")
    p_remove.add_argument("--end", type=int, help="结束行号（1-based）")
    p_remove.add_argument("--apply", action="store_true", help="实际写入")
    p_remove.set_defaults(func=cmd_remove_lines)

    return parser


def main() -> int:
    configure_utf8_streams()
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
