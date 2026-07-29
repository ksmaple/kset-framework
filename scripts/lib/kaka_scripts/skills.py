"""技能目录相关工具。"""

from __future__ import annotations

import re
import shutil
from pathlib import Path

from kaka_scripts.io import copy_tree_utf8_nobom

INIT_COPY_SKILLS = ["kaka-coder-designer", "kaka-util-git-commit"]
INIT_COPY_SCRIPT_FILES = ["check.py", "file.py", "git.py", "init.py", "list_skills.py"]
LEGACY_SKILL_PREFIXES = ["kaka-l2-", "kaka-l3-", "kaka-l4-"]

_NAME_RE = re.compile(r"^name:\s*(.+)\s*$", re.MULTILINE)


def skill_frontmatter_name(skill_md: str | Path) -> str | None:
    try:
        from kaka_scripts.io import read_text

        head = read_text(skill_md)[:4000]
        match = _NAME_RE.search(head)
        return match.group(1).strip() if match else None
    except Exception:  # noqa: BLE001
        return None


def list_skill_dir_names(skills_root: str | Path) -> list[str]:
    root = Path(skills_root)
    if not root.exists():
        return []
    return sorted(
        entry.name
        for entry in root.iterdir()
        if entry.is_dir() and not entry.name.startswith(".")
    )


def parse_feat_dev_branch(branch: str) -> dict[str, str | None] | None:
    if not branch.startswith("feat/dev-"):
        return None
    rest = branch[len("feat/dev-") :]
    parts = rest.split("-")
    if len(parts) < 3:
        return None
    date = parts[0]
    if len(date) != 8 or not date.isdigit():
        return None

    sequence: str | None = None
    idx = 0
    middle = parts[1:]
    if len(middle) > 1 and len(middle[0]) == 2 and middle[0].isdigit():
        sequence = middle[0]
        idx = 1

    if len(middle) - idx < 2:
        return None

    author_slug = middle[-1]
    feature_slug = "-".join(middle[idx:-1])
    if not feature_slug or not author_slug:
        return None
    return {
        "date": date,
        "sequence": sequence,
        "featureSlug": feature_slug,
        "authorSlug": author_slug,
    }


def is_legacy_skill_dir(name: str) -> bool:
    if name == "kaka-skills-bridge":
        return True
    return any(name.startswith(pfx) for pfx in LEGACY_SKILL_PREFIXES)


def copy_skill_tree(source: str | Path, dest: str | Path, force: bool = False) -> bool:
    dst = Path(dest)
    if dst.exists():
        if not force:
            return False
        shutil.rmtree(dst, ignore_errors=True)
    copy_tree_utf8_nobom(source, dest)
    return True
