"""scripts 运行环境版本检测。

须与 scripts/README.md 一致（R038）。
"""

from __future__ import annotations

from dataclasses import dataclass

SCRIPT_RUNTIME = {
    "pythonMin": "3.10.0",
    "pythonRecommended": "3.14",
}

SemverTriple = tuple[int, int, int]


@dataclass
class EnvCheckIssue:
    level: str  # 'error' | 'warn'
    message: str
    hint: str | None = None


def parse_semver(version: str) -> SemverTriple | None:
    cleaned = version.strip().lstrip("vV")
    parts = cleaned.split(".")
    if len(parts) < 3:
        return None
    try:
        return (int(parts[0]), int(parts[1]), int(parts[2].split("-")[0]))
    except ValueError:
        return None


def compare_semver(a: SemverTriple, b: SemverTriple) -> int:
    for x, y in zip(a, b):
        if x > y:
            return 1
        if x < y:
            return -1
    return 0


def meets_min_version(current: str, minimum: str) -> bool:
    cur = parse_semver(current)
    min_ = parse_semver(minimum)
    if cur is None or min_ is None:
        return False
    return compare_semver(cur, min_) >= 0


def check_script_runtime(
    *,
    python_version: str,
    repo_root_found: bool | None = None,
    git_available: bool | None = None,
) -> list[EnvCheckIssue]:
    issues: list[EnvCheckIssue] = []
    rt = SCRIPT_RUNTIME

    if not meets_min_version(python_version, rt["pythonMin"]):
        issues.append(
            EnvCheckIssue(
                level="error",
                message=f"Python {python_version} 低于要求 >= {rt['pythonMin']}",
                hint=f"请安装 Python {rt['pythonRecommended']}（https://www.python.org/）",
            )
        )

    if repo_root_found is False:
        issues.append(
            EnvCheckIssue(
                level="error",
                message="未检测到仓库根（缺少 .git 或 .claude/skills）",
                hint="请在 kset-developer 克隆目录内运行脚本",
            )
        )

    if git_available is False:
        issues.append(
            EnvCheckIssue(
                level="warn",
                message="未找到 git 命令",
                hint="git_changed_paths 等脚本需要 git 在 PATH 中",
            )
        )

    return issues
