"""项目画像与轻量探测。"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from kaka_scripts.io import read_text


@dataclass
class ProjectProfile:
    project_name: str
    project_type: str
    tech_stack_summary: str
    api_style_actual: str
    compile_command: str
    test_command: str
    sql_dialect: str
    datetime_wire_format: str
    datetime_timezone_policy: str
    datetime_epoch_unit: str
    datetime_backend_serializer: str
    datetime_frontend_lib: str
    datetime_sql_column_type: str
    datetime_legacy_note: str
    status_map: dict[str, str]
    actual_map: dict[str, str]
    detected_modules: list[dict[str, Any]]
    source_layout: dict[str, Any]
    warnings: list[str]


_DEPENDENCY_HINTS: list[tuple[str, str]] = [
    ("spring-boot", "Spring Boot"),
    ("mybatis-plus", "MyBatis-Plus"),
    ("mybatis", "MyBatis"),
    ("mapstruct", "MapStruct"),
    ("jpa", "JPA"),
    ("postgresql", "PostgreSQL"),
    ("mysql", "MySQL"),
    ("redis", "Redis"),
    ("vue", "Vue"),
    ("react", "React"),
    ("vite", "Vite"),
    ("dayjs", "dayjs"),
]


def _safe_read(path: Path) -> str:
    try:
        return read_text(path)
    except Exception:
        return ""


def _find_first_existing(root: Path, names: list[str]) -> Path | None:
    for name in names:
        path = root / name
        if path.exists():
            return path
    return None


def detect_project_type(target_root: Path) -> str:
    has_java = any((target_root / name).exists() for name in ["pom.xml", "build.gradle", "build.gradle.kts"])
    has_node = (target_root / "package.json").exists()
    has_wx = any((target_root / name).exists() for name in ["app.json", "project.config.json", "app.wxss"])
    if has_java and has_node:
        return "fullstack"
    if has_wx and has_node:
        return "wxapp"
    if has_java:
        return "backend"
    if has_node:
        return "frontend"
    return "project"


def detect_commands(target_root: Path) -> tuple[str, str]:
    if (target_root / "pom.xml").exists():
        return ("mvn -q -DskipTests compile", "mvn -q test")
    if (target_root / "build.gradle").exists() or (target_root / "build.gradle.kts").exists():
        return ("./gradlew compileJava", "./gradlew test")
    package_json = target_root / "package.json"
    if package_json.exists():
        text = _safe_read(package_json)
        try:
            data = json.loads(text)
            scripts = data.get("scripts") or {}
            has_build = "build" in scripts
            has_test = "test" in scripts
        except Exception:
            has_build = '"build"' in text
            has_test = '"test"' in text
        if has_build and has_test:
            return ("npm run build", "npm test")
        if has_build:
            return ("npm run build", "echo \"set testCommand in project-spec\"")
        return ("echo \"set compileCommand in project-spec\"", "echo \"set testCommand in project-spec\"")
    return ("echo \"set compileCommand in project-spec\"", "echo \"set testCommand in project-spec\"")


def detect_sql_dialect(target_root: Path) -> str:
    dialect_path = target_root / ".claude" / "sql-dialect.json"
    if dialect_path.exists():
        try:
            data = json.loads(_safe_read(dialect_path))
            return str(data.get("dialect") or "unknown")
        except Exception:
            return "unknown"

    markers = {
        "mysql": ["mysql", "mariadb"],
        "postgresql": ["postgresql", "postgres"],
        "sqlite": ["sqlite"],
        "oracle": ["oracle"],
        "sqlserver": ["sqlserver", "mssql"],
    }
    for manifest in ["pom.xml", "build.gradle", "build.gradle.kts", "package.json"]:
        text = _safe_read(target_root / manifest).lower()
        if not text:
            continue
        for dialect, needles in markers.items():
            if any(needle in text for needle in needles):
                return dialect
    return "unknown"


def detect_tech_stack_summary(target_root: Path) -> tuple[str, list[dict[str, Any]]]:
    labels: list[str] = []
    modules: list[dict[str, Any]] = []

    if (target_root / "pom.xml").exists():
        text = _safe_read(target_root / "pom.xml").lower()
        labels.extend(["Java", "Maven"])
        for needle, label in _DEPENDENCY_HINTS:
            if needle in text and label not in labels:
                labels.append(label)
        modules.append({"type": "backend", "detectedBy": "pom.xml", "confidence": 0.95})
    elif (target_root / "build.gradle").exists() or (target_root / "build.gradle.kts").exists():
        text = _safe_read(_find_first_existing(target_root, ["build.gradle", "build.gradle.kts"]) or target_root)
        labels.extend(["Java", "Gradle"])
        lowered = text.lower()
        for needle, label in _DEPENDENCY_HINTS:
            if needle in lowered and label not in labels:
                labels.append(label)
        modules.append({"type": "backend", "detectedBy": "build.gradle", "confidence": 0.9})

    package_json = target_root / "package.json"
    if package_json.exists():
        text = _safe_read(package_json).lower()
        if "vue" in text and "Vue" not in labels:
            labels.append("Vue")
        if "react" in text and "React" not in labels:
            labels.append("React")
        if "vite" in text and "Vite" not in labels:
            labels.append("Vite")
        if "node" not in [label.lower() for label in labels]:
            labels.append("Node")
        modules.append({"type": "frontend", "detectedBy": "package.json", "confidence": 0.9})

    if not labels:
        labels.append("待人工确认")
    return (" + ".join(labels), modules)


def detect_source_layout(target_root: Path) -> dict[str, Any]:
    layout = {
        "javaSrcExists": False,
        "javaTopPackages": [],
        "frontendSrcExists": False,
        "frontendTopDirs": [],
    }

    java_src = target_root / "src" / "main" / "java"
    if java_src.exists():
        layout["javaSrcExists"] = True
        try:
            top = sorted(
                entry.name
                for entry in java_src.iterdir()
                if entry.is_dir() and not entry.name.startswith(".")
            )[:6]
            layout["javaTopPackages"] = top
        except Exception:
            pass

    frontend_src = target_root / "src"
    if frontend_src.exists():
        layout["frontendSrcExists"] = True
        try:
            top = sorted(
                entry.name
                for entry in frontend_src.iterdir()
                if entry.is_dir()
                and not entry.name.startswith(".")
                and entry.name not in {"main", "test", "dist", "build", "node_modules"}
            )[:8]
            layout["frontendTopDirs"] = top
        except Exception:
            pass

    return layout


def detect_api_style(target_root: Path) -> str:
    java_src = target_root / "src" / "main" / "java"
    if java_src.exists():
        post_count = 0
        get_count = 0
        for file_path in java_src.rglob("*Controller.java"):
            text = _safe_read(file_path)
            post_count += text.count("@PostMapping")
            get_count += text.count("@GetMapping")
            if "@RequestMapping" in text and "@PostMapping" not in text and "@GetMapping" not in text:
                return "检测到 Controller 以 @RequestMapping 为主，需在 project-spec 差异项确认具体接口风格"
            if post_count + get_count >= 3:
                break
        if post_count > 0 and get_count == 0:
            return "检测到 Controller 以 POST 为主，默认沿用 Full POST + HTTP 200 + errCode"
        if get_count > 0:
            return "检测到存量接口包含 GET/POST 混用，需在 project-spec 差异项确认"
    return "默认按 Full POST + HTTP 200 + errCode"


def detect_datetime_profile(target_root: Path, sql_dialect: str) -> dict[str, str]:
    result = {
        "datetime_wire_format": "yyyy-MM-dd HH:mm:ss",
        "datetime_timezone_policy": "Asia/Shanghai（默认；如与存量配置不一致请在差异项调整）",
        "datetime_epoch_unit": "N/A",
        "datetime_backend_serializer": "Jackson：date-format=yyyy-MM-dd HH:mm:ss + JavaTimeModule",
        "datetime_frontend_lib": "dayjs（默认；无前端填 N/A）",
        "datetime_sql_column_type": "待根据方言确认",
        "datetime_legacy_note": "无；如存量代码不一致请补充",
    }

    config_candidates = [
        target_root / "src" / "main" / "resources" / "application.yml",
        target_root / "src" / "main" / "resources" / "application.yaml",
        target_root / "src" / "main" / "resources" / "application.properties",
    ]
    for config in config_candidates:
        text = _safe_read(config)
        if not text:
            continue
        if "spring.jackson.date-format" in text or "date-format:" in text:
            match = re.search(r"date-format[:=]\s*([^\s]+)", text)
            if match:
                result["datetime_wire_format"] = match.group(1).strip().strip('"')
                result["datetime_backend_serializer"] = (
                    f"Jackson：date-format={result['datetime_wire_format']} + JavaTimeModule"
                )
        if "time-zone" in text:
            match = re.search(r"time-zone[:=]\s*([^\s]+)", text)
            if match:
                zone = match.group(1).strip().strip('"')
                result["datetime_timezone_policy"] = f"{zone}（从后端配置探测）"
        if "write-dates-as-timestamps" in text and "true" in text.lower():
            result["datetime_epoch_unit"] = "毫秒或秒（检测到 timestamps 序列化，需人工确认）"
            result["datetime_legacy_note"] = "检测到 write-dates-as-timestamps=true，需确认是否仍存在 epoch 序列化"
        break

    package_json = target_root / "package.json"
    package_text = _safe_read(package_json).lower()
    if package_text:
        if "dayjs" in package_text:
            result["datetime_frontend_lib"] = "dayjs"
        elif "date-fns" in package_text:
            result["datetime_frontend_lib"] = "date-fns"
        elif "moment" in package_text:
            result["datetime_frontend_lib"] = "moment"
        else:
            result["datetime_frontend_lib"] = "N/A"
    elif detect_project_type(target_root) == "backend":
        result["datetime_frontend_lib"] = "N/A"

    if sql_dialect == "mysql":
        result["datetime_sql_column_type"] = "DATETIME"
    elif sql_dialect == "postgresql":
        result["datetime_sql_column_type"] = "TIMESTAMPTZ"
    elif sql_dialect == "sqlite":
        result["datetime_sql_column_type"] = "TEXT / INTEGER（按项目约定）"

    return result


def _default_status_map() -> dict[str, str]:
    return {
        "statusNaming": "待确认",
        "statusDdd": "待确认",
        "statusApi": "待确认",
        "statusFrontend": "待确认",
        "statusSql": "待确认",
        "statusConv": "待确认",
        "statusEvent": "待确认",
        "statusCache": "待确认",
        "statusOrch": "待确认",
        "statusLog": "待确认",
    }


def _default_actual_map(api_style_actual: str) -> dict[str, str]:
    return {
        "actualNaming": "待根据现有代码与业务术语确认",
        "actualDdd": "待根据现有模块与聚合划分确认",
        "actualApi": api_style_actual,
        "actualFrontend": "待根据前端框架与类型声明确认；无前端填 N/A",
        "actualSql": "待根据 ORM、DDL 与 sql-dialect.json 确认",
        "actualConv": "待根据 DTO/Entity/PO 转换实现确认",
        "actualEvent": "待根据事件驱动实现确认；无事件链路填 N/A",
        "actualCache": "待根据缓存注解或配置确认；无缓存填 N/A",
        "actualOrch": "待根据外部调用、熔断与限流实现确认；无编排填 N/A",
        "actualLog": "待根据日志配置、traceId 与脱敏策略确认",
    }


def scan_project_profile(target_root: str | Path, *, project_name: str | None = None) -> ProjectProfile:
    root = Path(target_root).resolve()
    project_type = detect_project_type(root)
    compile_command, test_command = detect_commands(root)
    sql_dialect = detect_sql_dialect(root)
    tech_stack_summary, modules = detect_tech_stack_summary(root)
    source_layout = detect_source_layout(root)
    api_style_actual = detect_api_style(root)
    datetime_profile = detect_datetime_profile(root, sql_dialect)
    warnings: list[str] = []
    if tech_stack_summary == "待人工确认":
        warnings.append("未探测到明确技术栈，需人工确认 projectType、compileCommand、testCommand")
    if sql_dialect == "unknown":
        warnings.append("未探测到 SQL 方言，project-spec 中应人工确认 datetime SQL 列类型")
    if not source_layout["javaSrcExists"] and project_type in {"backend", "fullstack"}:
        warnings.append("未检测到 src/main/java，后端分层与 API 风格需人工确认")
    if project_type in {"frontend", "fullstack"} and not source_layout["frontendSrcExists"]:
        warnings.append("未检测到前端 src 目录，前端类型与日期处理需人工确认")

    return ProjectProfile(
        project_name=project_name or root.name,
        project_type=project_type,
        tech_stack_summary=tech_stack_summary,
        api_style_actual=api_style_actual,
        compile_command=compile_command,
        test_command=test_command,
        sql_dialect=sql_dialect,
        datetime_wire_format=datetime_profile["datetime_wire_format"],
        datetime_timezone_policy=datetime_profile["datetime_timezone_policy"],
        datetime_epoch_unit=datetime_profile["datetime_epoch_unit"],
        datetime_backend_serializer=datetime_profile["datetime_backend_serializer"],
        datetime_frontend_lib=datetime_profile["datetime_frontend_lib"],
        datetime_sql_column_type=datetime_profile["datetime_sql_column_type"],
        datetime_legacy_note=datetime_profile["datetime_legacy_note"],
        status_map=_default_status_map(),
        actual_map=_default_actual_map(api_style_actual),
        detected_modules=modules,
        source_layout=source_layout,
        warnings=warnings,
    )


def profile_to_dict(profile: ProjectProfile) -> dict[str, Any]:
    return {
        "projectName": profile.project_name,
        "projectType": profile.project_type,
        "techStackSummary": profile.tech_stack_summary,
        "apiStyleActual": profile.api_style_actual,
        "compileCommand": profile.compile_command,
        "testCommand": profile.test_command,
        "sqlDialect": profile.sql_dialect,
        "datetime": {
            "wireFormat": profile.datetime_wire_format,
            "timezonePolicy": profile.datetime_timezone_policy,
            "epochUnit": profile.datetime_epoch_unit,
            "backendSerializer": profile.datetime_backend_serializer,
            "frontendLib": profile.datetime_frontend_lib,
            "sqlColumnType": profile.datetime_sql_column_type,
            "legacyNote": profile.datetime_legacy_note,
        },
        "statusMap": profile.status_map,
        "actualMap": profile.actual_map,
        "detectedModules": profile.detected_modules,
        "sourceLayout": profile.source_layout,
        "warnings": profile.warnings,
    }
