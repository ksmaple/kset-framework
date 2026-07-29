"""生成前置守卫。"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

from kaka_scripts.links import is_reparse_or_symlink
from kaka_scripts.paths import find_repo_root, rules_dir, skills_dir
from kaka_scripts.skills import INIT_COPY_SKILLS, LEGACY_SKILL_PREFIXES, skill_frontmatter_name

Level = Literal["INFO", "WARN", "BLOCK"]


@dataclass
class GuardLine:
    level: Level
    message: str


@dataclass
class GuardReport:
    lines: list[GuardLine] = field(default_factory=list)
    operation: str = ""
    root: str = ""


def emit(report: GuardReport, level: Level, message: str) -> None:
    report.lines.append(GuardLine(level=level, message=message))


def has_block(report: GuardReport) -> bool:
    return any(line.level == "BLOCK" for line in report.lines)


def format_report(report: GuardReport) -> str:
    buf = [
        "## 生成前置守卫报告\n",
        f"- 操作: {report.operation}\n",
        f"- 根目录: {report.root}\n\n",
    ]
    for line in report.lines:
        buf.append(f"[{line.level}] {line.message}\n")
    buf.append(
        "\nSee .claude/skills/kaka-utils-project-init/references/pre-generation-guard-spec.md\n"
    )
    return "".join(buf)


def _hierarchy_has_skill(hierarchy: Path, skill_name: str) -> bool:
    if not hierarchy.exists():
        return True
    return skill_name in hierarchy.read_text(encoding="utf-8")


def _test_skill_path(report: GuardReport, skill_name: str, skills_root: Path) -> None:
    directory = skills_root / skill_name
    skill_md = directory / "SKILL.md"
    hierarchy = skills_root / "SKILL-HIERARCHY.md"

    if not directory.exists():
        emit(report, "INFO", f"skill NEW (no dir): {skill_name}")
        return
    if not skill_md.exists():
        emit(report, "BLOCK", f"skill PATH_CONFLICT: dir exists without SKILL.md -> {directory}")
        return

    fm = skill_frontmatter_name(skill_md)
    if fm and fm != skill_name:
        emit(report, "BLOCK", f"skill NAME_CONFLICT: dir={skill_name} frontmatter name={fm}")
    else:
        emit(report, "WARN", f"skill EXISTS: {skill_name} -> {skill_md} (SKIP/UPDATE/OVERWRITE)")

    if not _hierarchy_has_skill(hierarchy, skill_name):
        emit(report, "WARN", "skill HIERARCHY_CONFLICT: not listed in SKILL-HIERARCHY.md")


def _test_all_skill_name_duplicates(report: GuardReport, skills_root: Path) -> None:
    if not skills_root.exists():
        return
    seen: dict[str, str] = {}
    for entry in skills_root.iterdir():
        if not entry.is_dir():
            continue
        skill_md = entry / "SKILL.md"
        if not skill_md.exists():
            continue
        fm = skill_frontmatter_name(skill_md)
        if not fm:
            continue
        if fm in seen:
            emit(report, "BLOCK", f"skill NAME_CONFLICT: duplicate frontmatter name={fm}")
        else:
            seen[fm] = entry.name


def _test_legacy_design_skills(
    report: GuardReport, skills_root: Path, rules_root_path: Path
) -> None:
    if skills_root.exists():
        for entry in skills_root.iterdir():
            if not entry.is_dir():
                continue
            n = entry.name
            for prefix in LEGACY_SKILL_PREFIXES:
                if n.startswith(prefix):
                    emit(report, "WARN", f"legacy skill dir (remove after migrate): {n}")
                    break
            if n == "kaka-skills-bridge":
                emit(
                    report,
                    "WARN",
                    "legacy kaka-skills-bridge (remove; rules merged into coder-designer / proj-coder)",
                )

        for obsolete in ["DESIGN-LAYER-SKILLS.md", "SKILLS-INDEX.md"]:
            if (skills_root / obsolete).exists():
                emit(report, "WARN", f"obsolete file: .claude/skills/{obsolete}")

    bridge_md = rules_root_path / "kaka-skills-bridge.md"
    if bridge_md.exists():
        emit(
            report,
            "WARN",
            "legacy rules/kaka-skills-bridge.md (remove or merge into kaka-project-rules)",
        )


def _test_rule_file(
    report: GuardReport, rule_file: str, rule_id: str, rules_root_path: Path
) -> None:
    path = rules_root_path / rule_file
    if not path.exists():
        emit(report, "INFO", f"rule NEW file: {rule_file}")
        return
    if not rule_id:
        emit(report, "WARN", f"rule EXISTS file: {path} (no RuleId, skip number check)")
        return

    tag = f"R{int(rule_id):03d}:"
    text = path.read_text(encoding="utf-8")
    count = sum(1 for line in text.splitlines() if line.startswith(tag))
    if count == 0:
        emit(report, "INFO", f"rule NEW number: {tag} in {rule_file}")
    elif count == 1:
        emit(report, "WARN", f"rule EXISTS: {tag} in {rule_file}")
    else:
        emit(report, "BLOCK", f"rule NUMBER_CONFLICT: {tag} appears {count} times in {rule_file}")

    for other in rules_root_path.iterdir():
        if not other.is_file() or not other.name.endswith(".md") or other.name == rule_file:
            continue
        if tag in other.read_text(encoding="utf-8"):
            emit(report, "BLOCK", f"rule NUMBER_CONFLICT: {tag} also in {other}")


def _skills_link_not_reparse(root: Path, link_rel: str) -> bool:
    link = root / link_rel
    if not link.exists():
        return False
    try:
        return link.is_dir() and not is_reparse_or_symlink(link)
    except OSError:
        return False


def run_guard(
    operation: str,
    *,
    repo_root: str | Path | None = None,
    name: str = "",
    proj: str = "",
    rule_file: str = "kaka-project-rules.md",
    rule_id: str = "",
) -> GuardReport:
    root = Path(repo_root).resolve() if repo_root else find_repo_root()
    skills_root_path = skills_dir(root)
    rules_root_path = rules_dir(root)
    report = GuardReport(lines=[], operation=operation, root=str(root))

    if operation == "skill":
        if not name:
            raise ValueError("skill 类型需要 --name")
        _test_skill_path(report, name, skills_root_path)
        _test_all_skill_name_duplicates(report, skills_root_path)
    elif operation == "project-skill":
        if not proj:
            raise ValueError("project-skill 需要 --proj")
        _test_skill_path(report, f"{proj}-coder", skills_root_path)
        _test_skill_path(report, f"{proj}-fixer", skills_root_path)
        spec = skills_root_path / f"{proj}-coder" / "references" / "project-spec.md"
        if spec.exists():
            emit(report, "WARN", f"project-spec EXISTS: {spec} (init 默认 SKIP 覆盖正文)")
    elif operation == "rule":
        _test_rule_file(report, rule_file, rule_id, rules_root_path)
    elif operation == "init":
        if proj:
            _test_skill_path(report, f"{proj}-coder", skills_root_path)
            _test_skill_path(report, f"{proj}-fixer", skills_root_path)
        else:
            emit(report, "WARN", "init: no --proj, scan rules and kaka-coder-designer only")

        report.lines.append(
            GuardLine(
                level="INFO",
                message="--- Step 4b init copy (python scripts/py/init.py copy; OVERWRITE needs --force) ---",
            )
        )
        for skill in INIT_COPY_SKILLS:
            _test_skill_path(report, skill, skills_root_path)
        _test_legacy_design_skills(report, skills_root_path, rules_root_path)
        _test_rule_file(report, "kaka-project-rules.md", "", rules_root_path)

        if (root / ".kaka").exists():
            emit(report, "WARN", "legacy .kaka/ still exists, migrate recommended")
        if (root / ".claude" / "design-standards").exists():
            emit(
                report,
                "WARN",
                "legacy .claude/design-standards/ (use project-spec + kaka-coder-designer)",
            )
        if _skills_link_not_reparse(root, ".agents/skills"):
            emit(
                report,
                "WARN",
                ".agents/skills is not a directory link; run python scripts/py/init.py links",
            )
        if _skills_link_not_reparse(root, ".cursor/skills"):
            emit(
                report,
                "WARN",
                ".cursor/skills is not a directory link; run python scripts/py/init.py links",
            )
    else:
        raise ValueError(f"未知操作类型: {operation}")

    return report
