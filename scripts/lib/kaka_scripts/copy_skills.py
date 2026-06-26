"""init 清单技能与通用规则复制。"""

from __future__ import annotations

from pathlib import Path

from kaka_scripts.io import copy_file_utf8_nobom, copy_tree_utf8_nobom, read_text, write_text
from kaka_scripts.paths import ensure_dir, find_repo_root, rules_dir, scripts_dir, skills_dir
from kaka_scripts.project_init import generate_project_assets
from kaka_scripts.rules import INIT_COPY_RULES, copy_rule_file, migrate_local_rules
from kaka_scripts.skills import INIT_COPY_SCRIPT_FILES, INIT_COPY_SKILLS, copy_skill_tree


class CopyReport:
    def __init__(self) -> None:
        self.copied: list[str] = []
        self.skipped: list[str] = []
        self.copied_rules: list[str] = []
        self.skipped_rules: list[str] = []
        self.migrated_local_rules: list[str] = []
        self.skipped_local_rules: list[str] = []
        self.migrate_errors: list[str] = []
        self.copied_scripts: list[str] = []
        self.skipped_scripts: list[str] = []
        self.generated_assets: list[str] = []


def copy_init_rules(
    target_project_root: str | Path,
    *,
    source_rules_root: str | Path | None = None,
    force: bool = False,
    rule_files: list[str] | None = None,
    migrate_local: bool = True,
) -> CopyReport:
    repo = find_repo_root()
    src_root = Path(source_rules_root) if source_rules_root else rules_dir(repo)
    dest_root = ensure_dir(Path(target_project_root) / ".claude" / "rules")
    files = rule_files or list(INIT_COPY_RULES)
    report = CopyReport()

    for file in files:
        src = src_root / file
        dest = dest_root / file
        if not src.exists():
            raise RuntimeError(f"Missing source rule: {src}")
        if copy_rule_file(src, dest, force):
            report.copied_rules.append(file)
        else:
            report.skipped_rules.append(str(dest))

    if migrate_local:
        for file_name, success, error in migrate_local_rules(
            target_project_root, dest_root, force=force
        ):
            if success:
                report.migrated_local_rules.append(file_name)
            elif error and "跳过" in error:
                report.skipped_local_rules.append(f"{file_name}: {error}")
            else:
                report.migrate_errors.append(f"{file_name}: {error}")

    return report


def copy_init_skills(
    target_project_root: str | Path,
    *,
    source_skills_root: str | Path | None = None,
    force: bool = False,
    skill_names: list[str] | None = None,
) -> CopyReport:
    repo = find_repo_root()
    src_root = Path(source_skills_root) if source_skills_root else skills_dir(repo)
    dest_root = ensure_dir(Path(target_project_root) / ".claude" / "skills")
    names = skill_names or list(INIT_COPY_SKILLS)
    report = CopyReport()

    for name in names:
        src = src_root / name
        dest = dest_root / name
        if not src.exists():
            raise RuntimeError(f"Missing source skill: {src}")
        if copy_skill_tree(src, dest, force):
            report.copied.append(name)
        else:
            report.skipped.append(str(dest))
    return report


def copy_init_assets(
    target_project_root: str | Path,
    *,
    source_skills_root: str | Path | None = None,
    source_rules_root: str | Path | None = None,
    force: bool = False,
    skill_names: list[str] | None = None,
    rule_files: list[str] | None = None,
    migrate_local: bool = True,
) -> CopyReport:
    skills_report = copy_init_skills(
        target_project_root,
        source_skills_root=source_skills_root,
        force=force,
        skill_names=skill_names,
    )
    rules_report = copy_init_rules(
        target_project_root,
        source_rules_root=source_rules_root,
        force=force,
        rule_files=rule_files,
        migrate_local=migrate_local,
    )
    report = CopyReport()
    report.copied = skills_report.copied
    report.skipped = skills_report.skipped
    report.copied_rules = rules_report.copied_rules
    report.skipped_rules = rules_report.skipped_rules
    report.migrated_local_rules = rules_report.migrated_local_rules
    report.skipped_local_rules = rules_report.skipped_local_rules
    report.migrate_errors = rules_report.migrate_errors
    return report


def copy_init_scripts(
    target_project_root: str | Path,
    *,
    source_repo_root: str | Path | None = None,
    force: bool = False,
) -> CopyReport:
    repo = Path(source_repo_root) if source_repo_root else find_repo_root()
    src_root = scripts_dir(repo)
    dest_root = ensure_dir(Path(target_project_root) / "scripts")
    report = CopyReport()

    for rel_path in INIT_COPY_SCRIPT_FILES:
        src = src_root / "py" / rel_path
        dest = dest_root / "py" / rel_path
        if dest.exists() and not force:
            report.skipped_scripts.append(dest.relative_to(Path(target_project_root)).as_posix())
            continue
        copy_file_utf8_nobom(src, dest)
        report.copied_scripts.append(dest.relative_to(Path(target_project_root)).as_posix())

    readme_src = src_root / "README.md"
    readme_dest = dest_root / "README.md"
    if readme_dest.exists() and not force:
        report.skipped_scripts.append(readme_dest.relative_to(Path(target_project_root)).as_posix())
    else:
        copy_file_utf8_nobom(readme_src, readme_dest)
        report.copied_scripts.append(readme_dest.relative_to(Path(target_project_root)).as_posix())

    lib_src = src_root / "lib" / "kaka_scripts"
    lib_dest = dest_root / "lib" / "kaka_scripts"
    if lib_dest.exists() and not force:
        report.skipped_scripts.append(lib_dest.relative_to(Path(target_project_root)).as_posix())
    else:
        if lib_dest.exists() and force:
            import shutil

            shutil.rmtree(lib_dest, ignore_errors=True)
        copy_tree_utf8_nobom(lib_src, lib_dest)
        report.copied_scripts.append(lib_dest.relative_to(Path(target_project_root)).as_posix())

    lib_readme_src = src_root / "lib" / "README.md"
    lib_readme_dest = dest_root / "lib" / "README.md"
    if lib_readme_src.exists():
        if lib_readme_dest.exists() and not force:
            report.skipped_scripts.append(lib_readme_dest.relative_to(Path(target_project_root)).as_posix())
        else:
            copy_file_utf8_nobom(lib_readme_src, lib_readme_dest)
            report.copied_scripts.append(lib_readme_dest.relative_to(Path(target_project_root)).as_posix())

    return report


AI_PERMISSION_TEMPLATES: list[tuple[str, str]] = [
    (".claude/settings.local.json", "claude-settings-local.template.json"),
    (".codex/settings.json", "codex-settings.template.json"),
    (".cursor/CLAUDE.md", "cursor-claude.template.md"),
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


def write_ai_permissions(
    target_project_root: str | Path,
    *,
    proj: str,
    project_name: str | None = None,
    source_repo_root: str | Path | None = None,
) -> list[str]:
    """将 AI 权限配置模板写入目标项目。

    参数：
        target_project_root: 目标项目根目录
        proj: 项目标识（如 kset-rag）
        project_name: 项目显示名；默认与 proj 相同
        source_repo_root: 平台仓根目录；默认从当前工作目录推断

    返回：写入的文件相对路径列表
    """
    repo = Path(source_repo_root) if source_repo_root else find_repo_root()
    src_dir = _template_dir(repo)
    root = Path(target_project_root).resolve()
    name = project_name or proj
    written: list[str] = []

    for dest_rel, template_name in AI_PERMISSION_TEMPLATES:
        src = src_dir / template_name
        if not src.exists():
            continue
        dest = root / dest_rel
        ensure_dir(dest.parent)
        text = read_text(src)
        text = text.replace("{proj}", proj).replace("{projectName}", name)
        write_text(dest, text)
        written.append(dest_rel)

    return written


def generate_init_project_assets(
    target_project_root: str | Path,
    *,
    proj: str,
    project_name: str | None = None,
    project_type: str | None = None,
    compile_command: str | None = None,
    test_command: str | None = None,
    force: bool = False,
) -> list[str]:
    return generate_project_assets(
        target_project_root,
        proj=proj,
        project_name=project_name,
        project_type=project_type,
        compile_command=compile_command,
        test_command=test_command,
        force=force,
    )
