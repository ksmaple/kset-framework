#!/usr/bin/env python3
"""统一项目 init 入口。

子命令：links、copy、sync、dialect、guard
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))

from kaka_scripts.cli import coerce_dir_path, configure_utf8_streams, die, resolve_project_root
from kaka_scripts.copy_skills import (
    copy_init_assets,
    copy_init_scripts,
    generate_init_project_assets,
    write_ai_permissions,
)
from kaka_scripts.dialect import detect_sql_dialect, write_sql_dialect_json
from kaka_scripts.guard import format_report, has_block, run_guard
from kaka_scripts.links import setup_ai_env_links
from kaka_scripts.paths import find_repo_root, rules_dir, skills_dir
from kaka_scripts.project_init import verify_init_outputs
from kaka_scripts.project_init import read_project_profile


def cmd_links(args: argparse.Namespace) -> int:
    root = args.repo_root or find_repo_root()
    try:
        for message in setup_ai_env_links(root):
            print(message)
    except Exception as exc:  # noqa: BLE001
        die(str(exc))
    return 0


def cmd_copy(args: argparse.Namespace) -> int:
    target_root = coerce_dir_path(args.target, "target")
    repo = find_repo_root()
    src_skills = (
        coerce_dir_path(args.source_skills_root, "source-skills-root")
        if args.source_skills_root
        else skills_dir(repo)
    )
    src_rules = (
        coerce_dir_path(args.source_rules_root, "source-rules-root")
        if args.source_rules_root
        else rules_dir(repo)
    )

    report = copy_init_assets(
        target_root,
        source_skills_root=src_skills,
        source_rules_root=src_rules,
        force=args.force,
    )
    scripts_report = copy_init_scripts(target_root, source_repo_root=repo, force=args.force)

    proj = args.proj or target_root.name
    generated_assets = generate_init_project_assets(
        target_root,
        proj=proj,
        project_name=args.project_name,
        project_type=args.project_type,
        compile_command=args.compile_command,
        test_command=args.test_command,
        force=args.force,
    )
    written = write_ai_permissions(target_root, proj=proj)
    for rel_path in written:
        print(f"Wrote AI permissions: {rel_path}")
    for rel_path in generated_assets:
        print(f"Generated asset: {rel_path}")

    for name in report.copied:
        print(f"Copied skill: {name}")
    for file in report.copied_rules:
        print(f"Copied rule: {file}")
    for file in report.migrated_local_rules:
        print(f"Migrated local rule: {file}")
    for dest in report.skipped:
        print(f"WARN: skill target exists: {dest} (use --force)", file=sys.stderr)
    for dest in report.skipped_rules:
        print(f"WARN: rule target exists: {dest} (use --force)", file=sys.stderr)
    for item in report.skipped_local_rules:
        print(f"WARN: local rule skipped: {item} (use --force)", file=sys.stderr)
    for err in report.migrate_errors:
        print(f"ERROR: local rule migrate failed: {err}", file=sys.stderr)
    for file in scripts_report.copied_scripts:
        print(f"Copied script: {file}")
    for file in scripts_report.skipped_scripts:
        print(f"WARN: script target exists: {file} (use --force)", file=sys.stderr)

    if report.skipped or report.skipped_rules or scripts_report.skipped_scripts:
        return 2
    print("Done.")
    return 0


def cmd_sync(args: argparse.Namespace) -> int:
    targets_file = Path(args.targets)
    if not targets_file.exists():
        print(f"Targets file not found: {targets_file}", file=sys.stderr)
        print("Create it with one project root per line.", file=sys.stderr)
        return 1

    targets: list[Path] = []
    for line in targets_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        targets.append(Path(line).resolve())

    if not targets:
        print("No target projects found in list.")
        return 0

    has_error = False
    for target in targets:
        if not target.is_dir():
            print(f"[ERROR] Not a directory: {target}", file=sys.stderr)
            has_error = True
            continue
        try:
            report = copy_init_assets(target, force=args.force)
            written = write_ai_permissions(target, proj=target.name)
            copied = report.copied + report.copied_rules
            skipped = report.skipped + report.skipped_rules
            migrated = len(report.migrated_local_rules)
            migrate_skipped = len(report.skipped_local_rules)
            migrate_errors = len(report.migrate_errors)
            print(
                f"{target}: copied {len(copied)}, skipped {len(skipped)}, "
                f"permissions {len(written)}, migrated {migrated}, "
                f"migrate-skipped {migrate_skipped}, migrate-errors {migrate_errors}"
            )
            if skipped:
                has_error = True
            if migrate_errors:
                has_error = True
        except Exception as exc:  # noqa: BLE001
            print(f"[ERROR] {target}: {exc}", file=sys.stderr)
            has_error = True

    return 1 if has_error else 0


def cmd_dialect(args: argparse.Namespace) -> int:
    root = resolve_project_root(args.project_root)
    result = detect_sql_dialect(
        root,
        dialect=args.dialect or None,
        source=args.source,
    )
    out = write_sql_dialect_json(root, result)
    print(f"Wrote {out} dialect={result['dialect']} version={result['version']}")
    return 0


def cmd_guard(args: argparse.Namespace) -> int:
    valid = {"skill", "project-skill", "rule", "init"}
    if args.type not in valid:
        die(f"未知类型: {args.type}")

    root = args.repo_root or find_repo_root()

    try:
        report = run_guard(
            args.type,
            repo_root=root,
            name=args.name,
            proj=args.proj,
            rule_file=args.rule_file,
            rule_id=args.rule_id,
        )
        sys.stdout.write(format_report(report))
        return 1 if has_block(report) else 0
    except Exception as exc:  # noqa: BLE001
        die(str(exc))


def cmd_verify(args: argparse.Namespace) -> int:
    root = resolve_project_root(args.project_root)
    result = verify_init_outputs(root, proj=args.proj or None)
    print(f"targetRoot={result['targetRoot']}")
    print(f"proj={result['proj']}")
    print(f"projectSpecHasDatetimeSection={result['projectSpecHasDatetimeSection']}")
    print(f"hasSqlDialect={result['hasSqlDialect']}")
    print(f"linksReady={result['linksReady']}")
    if result["missing"]:
        print("missing:")
        for item in result["missing"]:
            print(f"  - {item}")
    print(f"ok={result['ok']}")
    return 0 if result["ok"] else 1


def cmd_profile(args: argparse.Namespace) -> int:
    root = resolve_project_root(args.project_root)
    data = read_project_profile(root, project_name=args.project_name or None)
    import json

    print(json.dumps(data, ensure_ascii=False, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="init.py", description="统一项目 init 入口")
    sub = parser.add_subparsers(dest="command", required=True)

    p_links = sub.add_parser("links", help="建立三端技能目录链接")
    p_links.add_argument("--repo-root", help="仓库根目录")
    p_links.set_defaults(func=cmd_links)

    p_copy = sub.add_parser("copy", help="复制 init 技能与规则到目标项目")
    p_copy.add_argument("target", help="目标项目根目录")
    p_copy.add_argument("--proj", help="项目标识（默认取目标目录名）")
    p_copy.add_argument("--project-name", help="项目展示名")
    p_copy.add_argument("--project-type", help="项目类型：backend/frontend/fullstack/project")
    p_copy.add_argument("--compile-command", help="覆盖默认编译命令")
    p_copy.add_argument("--test-command", help="覆盖默认测试命令")
    p_copy.add_argument("--source-skills-root", help="源 skills 根目录")
    p_copy.add_argument("--source-rules-root", help="源 rules 根目录")
    p_copy.add_argument("--force", "-f", action="store_true", help="强制覆盖")
    p_copy.set_defaults(func=cmd_copy)

    p_sync = sub.add_parser("sync", help="批量同步到多个目标项目")
    p_sync.add_argument("--targets", default="tmp/target-projects.txt", help="目标项目根目录列表文件")
    p_sync.add_argument("--force", "-f", action="store_true", help="强制覆盖")
    p_sync.set_defaults(func=cmd_sync)

    p_dialect = sub.add_parser("dialect", help="检测 SQL 方言")
    p_dialect.add_argument("--project-root", help="项目根目录")
    p_dialect.add_argument("--dialect", default="", help="强制指定方言")
    p_dialect.add_argument("--source", default="auto", help="来源标记")
    p_dialect.set_defaults(func=cmd_dialect)

    p_guard = sub.add_parser("guard", help="生成前置守卫")
    p_guard.add_argument("--type", default="init", help="操作类型：init|skill|project-skill|rule")
    p_guard.add_argument("--name", default="", help="skill 名称")
    p_guard.add_argument("--proj", default="", help="项目标识")
    p_guard.add_argument("--rule-file", default="kaka-project-rules.md", help="规则文件名")
    p_guard.add_argument("--rule-id", default="", help="规则编号")
    p_guard.add_argument("--repo-root", help="仓库根目录")
    p_guard.set_defaults(func=cmd_guard)

    p_verify = sub.add_parser("verify", help="校验 init 产物完整性")
    p_verify.add_argument("--project-root", help="目标项目根目录")
    p_verify.add_argument("--proj", help="项目标识（默认取项目目录名）")
    p_verify.set_defaults(func=cmd_verify)

    p_profile = sub.add_parser("profile", help="输出项目画像与默认 init 输入")
    p_profile.add_argument("--project-root", help="目标项目根目录")
    p_profile.add_argument("--project-name", help="项目展示名")
    p_profile.set_defaults(func=cmd_profile)

    return parser


def main() -> int:
    configure_utf8_streams()
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
