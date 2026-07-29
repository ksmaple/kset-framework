"""SQL 方言探测与 sql-dialect.json 写入。"""

from __future__ import annotations

import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from kaka_scripts.io import read_text, write_json
from kaka_scripts.paths import rel_posix

DIALECT_VERSIONS = {
    "mysql": "8.0",
    "postgresql": "15",
    "sqlite": "3.35",
}

SCAN_FILES = [
    "pom.xml",
    "build.gradle",
    "build.gradle.kts",
    "application.yml",
    "application.yaml",
    "application.properties",
    "application-dev.yml",
    "application-dev.yaml",
]

SIGNAL_PATTERNS = {
    "mysql": ["jdbc:mysql", "mysql-connector", "com.mysql.", "mariadb"],
    "postgresql": ["jdbc:postgresql", "org.postgresql", "postgresql", "postgres"],
    "sqlite": ["jdbc:sqlite", "sqlite-jdbc", ".sqlite", "sqlite:"],
}

SKIP_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    ".vscode",
    "build",
    "dist",
    "node_modules",
    "out",
    "target",
    "tmp",
    "temp",
}


def _format_tz_offset() -> str:
    now = datetime.now(timezone.utc).astimezone()
    offset = now.utcoffset()
    if offset is None:
        return "+00:00"
    total_minutes = int(offset.total_seconds() // 60)
    sign = "+" if total_minutes >= 0 else "-"
    total_minutes = abs(total_minutes)
    hours = total_minutes // 60
    minutes = total_minutes % 60
    return f"{sign}{hours:02d}:{minutes:02d}"


def _to_json_dict(result: dict[str, Any]) -> dict[str, Any]:
    selected_at = f"{datetime.now(timezone.utc).astimezone().strftime('%Y-%m-%dT%H:%M:%S')}{_format_tz_offset()}"
    return {
        "dialect": result["dialect"],
        "version": result["version"],
        "profileRelPath": "skills/kaka-coder-designer/references/sql-spec.md",
        "commonSpecRelPath": "skills/kaka-coder-designer/references/naming-spec.md",
        "detectedFrom": "; ".join(result["hits"]),
        "selectedAt": selected_at,
        "source": result["source"],
    }


def _score_text(path_label: str, text: str, scores: dict[str, int], hits: list[str]) -> None:
    lower = text.lower()
    for dialect, needles in SIGNAL_PATTERNS.items():
        for needle in needles:
            if needle.lower() in lower:
                scores[dialect] = scores.get(dialect, 0) + 1
                hits.append(f"{path_label} -> {dialect}")
                break


def _collect_application_configs(root: Path, max_depth: int) -> list[Path]:
    pattern = re.compile(r"^application.*\.ya?ml$", re.IGNORECASE)
    found: list[Path] = []

    def walk(dir_: Path, depth: int) -> None:
        if depth > max_depth:
            return
        try:
            entries = list(dir_.iterdir())
        except OSError:
            return
        for e in entries:
            if e.is_dir():
                if e.name in SKIP_DIRS:
                    continue
                walk(e, depth + 1)
            elif pattern.match(e.name):
                found.append(e)

    walk(root, 0)
    return found


def detect_sql_dialect(
    project_root: str | Path,
    *,
    dialect: str | None = None,
    source: str = "auto",
) -> dict[str, Any]:
    root = Path(project_root).resolve()
    scores = {k: 0 for k in SIGNAL_PATTERNS}
    hits: list[str] = []

    for name in SCAN_FILES:
        path = root / name
        if path.exists() and path.is_file():
            _score_text(name, read_text(path), scores, hits)

    for path in _collect_application_configs(root, 6):
        label = rel_posix(path, root)
        _score_text(label, read_text(path), scores, hits)

    if dialect:
        chosen = dialect
        src = "user" if source == "auto" else source
        hits.insert(0, f"{src} override -> {chosen}")
    else:
        ordered = sorted(scores.items(), key=lambda x: (-x[1], x[0]))
        top_name, top_score = ordered[0]
        second_score = ordered[1][1] if len(ordered) > 1 else 0
        if top_score == 0:
            chosen = "mysql"
            hits.append("no signal -> default mysql (WARN)")
        else:
            chosen = top_name
            if second_score >= top_score:
                hits.append(
                    f"WARN ambiguous: mysql={scores['mysql']} pg={scores['postgresql']} sqlite={scores['sqlite']}"
                )
        src = source

    return {
        "dialect": chosen,
        "version": DIALECT_VERSIONS.get(chosen, "8.0"),
        "hits": hits,
        "source": src,
    }


def write_sql_dialect_json(project_root: str | Path, result: dict[str, Any]) -> Path:
    out_dir = Path(project_root).resolve() / ".claude"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "sql-dialect.json"
    write_json(out_path, _to_json_dict(result))
    return out_path
