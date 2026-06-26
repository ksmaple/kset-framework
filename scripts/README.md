# scripts

仓库自动化脚本。**实现为 Python 3.x**（`py/` + `lib/kaka_scripts/`）。直接在仓库根通过 `python scripts/py/<脚本>.py <子命令>` 调用。

## 结构

```text
scripts/
├── lib/kaka_scripts/     # 可复用库
└── py/                   # CLI 入口
```

## 直接调用（推荐入口）

完整场景映射见 [.claude/skills/kaka-util-scripts/references/script-usage-spec.md](../.claude/skills/kaka-util-scripts/references/script-usage-spec.md)（`kaka-project-rules` R048、`kaka-util-scripts`）。

## 使用场景（R048）

| 类别 | 子命令 | 典型场景 |
|------|--------|----------|
| **环境** | `check.py env` | 克隆后验证 Python/git/仓库根 |
| **质量** | `check.py utf8` | 扫描/修复 UTF-8 BOM |
| **质量** | `check.py trailing-whitespace` | 检查/修复行尾空格 |
| **质量** | `check.py rule-ids` | 规则 R 编号重复/跳号 |
| **质量** | `check.py init-output` | 检查 init 产物是否完整 |
| **init** | `init.py guard` | 生成前置守卫（skill/rule/init） |
| **init** | `init.py profile` | 输出项目画像、默认命令与 DateTime 输入 |
| **init** | `init.py copy` | 复制技能/规则/最小 scripts（含 lib）并生成业务 init 产物 |
| **init** | `init.py sync` | 批量同步多个目标项目 |
| **init** | `init.py dialect` | 探测 SQL 方言 → `sql-dialect.json` |
| **init** | `init.py links` | 三端技能目录链接 |
| **init** | `init.py verify` | 校验业务仓 init 产物完整性 |
| **文件** | `file.py` | create/append/insert/replace/remove-lines/copy/move/touch/delete |
| **Git 辅助** | `git.py changed-paths` | 输出变更路径（非 commit） |
| **Git 辅助** | `git.py recent-files` | 最近 N 次提交涉及的文件 |
| **Git 辅助** | `git.py cleanup-merged` | 列出/删除已合并分支 |
| **索引** | `list_skills.py` | 列出技能目录与 frontmatter |

> **优先原则**：上表有对应子命令时，禁止用手工 shell 或临时脚本替代（R048）。Git commit/push 见 `kaka-util-git-commit`。

## 命令明细

| 命令 | 说明 | 示例 |
|------|------|------|
| `check.py env` | 检测 Python/git、仓库根 | `python scripts/py/check.py env` |
| `check.py utf8` | 扫描 UTF-8 BOM | `python scripts/py/check.py utf8`；修复加 `--fix` |
| `check.py trailing-whitespace` | 检查行尾空格 | `python scripts/py/check.py trailing-whitespace`；修复加 `--fix` |
| `check.py rule-ids` | 检查规则 R 编号重复/跳号 | `python scripts/py/check.py rule-ids` |
| `check.py init-output` | 检查 init 产物完整性 | `python scripts/py/check.py init-output --project-root D:\myapp` |
| `init.py links` | 三端技能目录链接 | `python scripts/py/init.py links` |
| `init.py copy` | init 技能/规则/最小 scripts 复制并生成业务产物 | `python scripts/py/init.py copy D:\myapp --proj myapp --force` |
| `init.py sync` | 批量同步到多个目标项目 | `python scripts/py/init.py sync --targets tmp/target-projects.txt --force` |
| `init.py guard` | 生成前置守卫 | `python scripts/py/init.py guard --type init --proj my-proj` |
| `init.py profile` | 输出项目画像、源码目录结构、默认命令与 DateTime 输入 | `python scripts/py/init.py profile --project-root D:\myapp` |
| `init.py dialect` | SQL 方言探测 | `python scripts/py/init.py dialect --project-root D:\myapp` |
| `init.py verify` | 校验 init 产物 | `python scripts/py/init.py verify --project-root D:\myapp --proj myapp` |
| `git.py changed-paths` | 输出 git 变更路径 | `python scripts/py/git.py changed-paths` |
| `git.py recent-files` | 最近 N 次提交涉及的文件 | `python scripts/py/git.py recent-files -n 10` |
| `git.py cleanup-merged` | 列出/清理已合并分支 | `python scripts/py/git.py cleanup-merged --delete` |
| `file.py` | 统一文件操作（子命令：`create`/`touch`/`delete`/`copy`/`move`/`replace`/`append`/`insert`/`remove-lines`） | `python scripts/py/file.py create tmp/hello.md --content "# Hi"` |
| `list_skills.py` | 列出技能目录及 frontmatter 名称 | `python scripts/py/list_skills.py` |

文件操作完整示例：

```bash
python scripts/py/file.py create tmp/hello.md --content "# Hi"
python scripts/py/file.py append tmp/hello.md --text "footer"
python scripts/py/file.py insert tmp/hello.md --after "# Hi" --text "## Section"
python scripts/py/file.py replace tmp/hello.md --old Hi --new Hello --apply
python scripts/py/file.py remove-lines tmp/hello.md --pattern "^# " --regex --apply
python scripts/py/file.py copy tmp/hello.md tmp/hello-copy.md
python scripts/py/file.py move tmp/hello-copy.md tmp/notes.md
python scripts/py/file.py touch tmp/marker.txt --create-dirs
python scripts/py/file.py delete tmp/notes.md --yes
```

在 `scripts/` 目录内也可直接执行：

```bash
cd scripts && python py/init.py guard --type init --proj my-proj
```

## 运行环境（R038）

| 项 | 要求 |
|----|------|
| **Python** | ≥ 3.10（推荐 3.14） |
| **git** | 命令行可用（部分脚本需要） |

## 编码（R020、R037）

- 脚本与说明统一 **UTF-8**（优先无 BOM）；读写走 `lib/kaka_scripts/io.py`。
- Python 读取文本可用 `utf-8-sig` 自动去除 BOM，写入须用 `utf-8` 保证无 BOM。
- 见仓库根 [`.editorconfig`](../.editorconfig)、[`.gitattributes`](../.gitattributes)。

## 常见异常（R038a）

| 现象 | 处理 |
|------|------|
| `python: command not found` | 安装 Python 3.10+ 并加入 PATH |
| `ModuleNotFoundError: No module named 'kaka_scripts'` | 检查 `scripts/lib/kaka_scripts/` 是否存在；确保在仓库根或 `scripts/` 目录内运行 |
| Python 版本过低 | 升级至 3.10+，`python scripts/py/check.py env` |
| `repo root not found` | 在含 `.git` 或 `.claude/skills` 的目录执行 |
| `.agents/skills` 非空目录 | 备份后删除，再 `python scripts/py/init.py links` |
| 守卫退出码 1 | 报告含 `[BLOCK]`，按 spec 处理 |
| `init.py copy` 退出码 2 | 目标技能或规则已存在，加 `--force` |
| `check.py utf8` 退出码 1 | 存在 UTF-8 BOM；加 `--fix` 去除 |
| 控制台中文乱码 | PowerShell 代码页非 UTF-8 | `chcp 65001` 或 Windows Terminal；文件/文案仍错则 `kaka-project-rules` R020a 重新生成 |
| `git diff --check` 失败 | 行尾空格；见 `kaka-project-rules` R043 与 `.editorconfig` |
| 业务仓 `.claude` 与平台不一致 | 未同步；`python scripts/py/init.py copy <path> --force` |

## 扩展

1. 在 `lib/kaka_scripts/` 增加逻辑
2. 在 `py/` 增加入口
3. 在本文档的「直接调用」表中注册示例
