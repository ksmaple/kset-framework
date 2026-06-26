#!/usr/bin/env python3
"""列出 .claude/skills/ 下所有技能目录及 frontmatter 名称。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))

from kaka_scripts.cli import configure_utf8_streams
from kaka_scripts.paths import find_repo_root, skills_dir
from kaka_scripts.skills import list_skill_dir_names, skill_frontmatter_name


def main() -> int:
    configure_utf8_streams()
    parser = argparse.ArgumentParser(description="列出技能目录")
    parser.add_argument("--repo-root", help="仓库根目录")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    root = Path(args.repo_root).resolve() if args.repo_root else find_repo_root()
    skills_root = skills_dir(root)
    names = list_skill_dir_names(skills_root)

    items: list[dict[str, object]] = []
    for name in names:
        skill_md = skills_root / name / "SKILL.md"
        fm_name = skill_frontmatter_name(skill_md) if skill_md.exists() else None
        refs = len(list((skills_root / name / "references").rglob("*"))) if (skills_root / name / "references").exists() else 0
        items.append({"dir": name, "name": fm_name, "references": refs})

    if args.json:
        print(json.dumps(items, ensure_ascii=False, indent=2))
    else:
        rel = skills_root.relative_to(root).as_posix()
        print(f"Skills in {rel}:\n")
        for item in items:
            name_tag = f" [{item['name']}]" if item["name"] else ""
            print(f"- {item['dir']}{name_tag}  ({item['references']} reference files)")
        print(f"\nTotal: {len(items)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
