"""业务项目 init 生成与验收。"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

from kaka_scripts.io import read_text, write_text
from kaka_scripts.paths import ensure_dir, find_repo_root
from kaka_scripts.project_profile import profile_to_dict, scan_project_profile


@dataclass
class InitProjectContext:
    target_root: Path
    proj: str
    project_name: str
    project_type: str
    compile_command: str
    test_command: str
    sql_dialect: str
    generated_at: str
    tech_stack_summary: str
    api_style_actual: str
    datetime_wire_format: str
    datetime_timezone_policy: str
    datetime_epoch_unit: str
    datetime_backend_serializer: str
    datetime_frontend_lib: str
    datetime_sql_column_type: str
    datetime_legacy_note: str


INIT_INDEX_TEMPLATES: list[tuple[str, str]] = [
    (".claude/AGENTS.md", "dot-claude-agents.template.md"),
    ("AGENTS.md", "root-agents.template.md"),
    ("CLAUDE.md", "root-claude.template.md"),
]


def _template_dir(repo_root: Path) -> Path:
    return (
        repo_root
        / ".claude"
        / "skills"
        / "kaka-utils-project-init"
        / "references"
        / "templates"
    )
def build_init_context(
    target_root: str | Path,
    *,
    proj: str | None = None,
    project_name: str | None = None,
    project_type: str | None = None,
    compile_command: str | None = None,
    test_command: str | None = None,
) -> InitProjectContext:
    root = Path(target_root).resolve()
    proj_name = proj or root.name
    pretty_name = project_name or proj_name
    profile = scan_project_profile(root, project_name=project_name or proj_name)
    kind = project_type or profile.project_type
    return InitProjectContext(
        target_root=root,
        proj=proj_name,
        project_name=profile.project_name or pretty_name,
        project_type=kind,
        compile_command=compile_command or profile.compile_command,
        test_command=test_command or profile.test_command,
        sql_dialect=profile.sql_dialect,
        generated_at=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        tech_stack_summary=profile.tech_stack_summary,
        api_style_actual=profile.api_style_actual,
        datetime_wire_format=profile.datetime_wire_format,
        datetime_timezone_policy=profile.datetime_timezone_policy,
        datetime_epoch_unit=profile.datetime_epoch_unit,
        datetime_backend_serializer=profile.datetime_backend_serializer,
        datetime_frontend_lib=profile.datetime_frontend_lib,
        datetime_sql_column_type=profile.datetime_sql_column_type,
        datetime_legacy_note=profile.datetime_legacy_note,
    )


def _render(template_name: str, context: dict[str, str], repo_root: Path) -> str:
    template_path = _template_dir(repo_root) / template_name
    text = read_text(template_path)
    for key, value in context.items():
        text = text.replace(f"{{{key}}}", value)
    return text


def render_template_map(ctx: InitProjectContext, repo_root: Path | None = None) -> dict[str, str]:
    repo = repo_root or find_repo_root()
    profile = scan_project_profile(ctx.target_root, project_name=ctx.project_name)
    values = {
        "proj": ctx.proj,
        "projectName": ctx.project_name,
        "projectType": ctx.project_type,
        "compileCommand": ctx.compile_command,
        "testCommand": ctx.test_command,
        "sqlDialect": ctx.sql_dialect,
        "generatedAt": ctx.generated_at,
        "techStackSummary": ctx.tech_stack_summary,
        "apiStyleActual": ctx.api_style_actual,
        "datetimeWireFormat": ctx.datetime_wire_format,
        "datetimeTimezonePolicy": ctx.datetime_timezone_policy,
        "datetimeEpochUnit": ctx.datetime_epoch_unit,
        "datetimeBackendSerializer": ctx.datetime_backend_serializer,
        "datetimeFrontendLib": ctx.datetime_frontend_lib,
        "datetimeSqlColumnType": ctx.datetime_sql_column_type,
        "datetimeLegacyNote": ctx.datetime_legacy_note,
        "diffItem1": "待根据探测结果或用户确认补充项目差异",
        **profile.status_map,
        **profile.actual_map,
    }
    output: dict[str, str] = {}
    output[".claude/skills/{proj}-coder/SKILL.md"] = _render("proj-coder-SKILL.template.md", values, repo)
    output[".claude/skills/{proj}-fixer/SKILL.md"] = _render("proj-fixer-SKILL.template.md", values, repo)
    output[".claude/skills/{proj}-coder/references/project-spec.md"] = _render(
        "project-spec.template.md", values, repo
    )
    for rel_path, template_name in INIT_INDEX_TEMPLATES:
        output[rel_path] = _render(template_name, values, repo)
    return output


def generate_project_assets(
    target_root: str | Path,
    *,
    proj: str | None = None,
    project_name: str | None = None,
    project_type: str | None = None,
    compile_command: str | None = None,
    test_command: str | None = None,
    force: bool = False,
) -> list[str]:
    repo = find_repo_root()
    ctx = build_init_context(
        target_root,
        proj=proj,
        project_name=project_name,
        project_type=project_type,
        compile_command=compile_command,
        test_command=test_command,
    )
    rendered = render_template_map(ctx, repo)
    written: list[str] = []
    for rel_path, content in rendered.items():
        actual_rel = rel_path.replace("{proj}", ctx.proj)
        dest = ctx.target_root / actual_rel
        if dest.exists() and not force:
            continue
        ensure_dir(dest.parent)
        write_text(dest, content)
        written.append(actual_rel)
    return written


def verify_init_outputs(target_root: str | Path, *, proj: str | None = None) -> dict[str, Any]:
    root = Path(target_root).resolve()
    proj_name = proj or root.name
    required = [
        ".claude/rules/kaka-project-rules.md",
        ".claude/skills/kaka-coder-designer/SKILL.md",
        ".claude/skills/kaka-util-git-commit/SKILL.md",
        f".claude/skills/{proj_name}-coder/SKILL.md",
        f".claude/skills/{proj_name}-fixer/SKILL.md",
        f".claude/skills/{proj_name}-coder/references/project-spec.md",
        ".claude/AGENTS.md",
        "AGENTS.md",
        "CLAUDE.md",
        ".cursor/CLAUDE.md",
        ".codex/settings.json",
        ".claude/settings.local.json",
        "scripts/py/check.py",
        "scripts/py/init.py",
        "scripts/lib/kaka_scripts/cli.py",
    ]
    missing = [path for path in required if not (root / path).exists()]
    links_ready = {
        ".agents/skills": (root / ".agents" / "skills").exists(),
        ".cursor/skills": (root / ".cursor" / "skills").exists(),
    }
    details: dict[str, Any] = {
        "targetRoot": str(root),
        "proj": proj_name,
        "missing": missing,
        "hasSqlDialect": (root / ".claude/sql-dialect.json").exists(),
        "linksReady": links_ready,
    }
    spec_path = root / ".claude" / "skills" / f"{proj_name}-coder" / "references" / "project-spec.md"
    details["projectSpecHasDatetimeSection"] = (
        spec_path.exists() and "## 3. 时间格式（前后端）" in read_text(spec_path)
    )
    details["ok"] = (
        not missing
        and details["projectSpecHasDatetimeSection"]
        and all(bool(v) for v in links_ready.values())
    )
    return details


def read_project_profile(target_root: str | Path, *, project_name: str | None = None) -> dict[str, Any]:
    profile = scan_project_profile(target_root, project_name=project_name)
    return profile_to_dict(profile)
