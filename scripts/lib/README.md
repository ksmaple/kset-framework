# scripts/lib — `kaka_scripts` 通用库

平台与业务仓 Python 脚本共享库，符合 `kaka-project-rules` **R036**、**R048**。

使用场景见 [kaka-util-scripts/references/script-usage-spec.md](../../.claude/skills/kaka-util-scripts/references/script-usage-spec.md)。

## 模块

| 模块 | 职责 |
|------|------|
| `paths` | 仓库根、`.claude` 路径解析 |
| `cli` | 错误输出、`die`、路径校验 |
| `io` | UTF-8 读写、JSON |
| `git` | `git status` / `diff --check` 封装 |
| `skills` | 技能目录、init 清单、分支名解析 |
| `rules` | 规则文件 R 编号解析与 init 规则复制 |
| `links` | 三端技能目录链接 |
| `dialect` | SQL 方言探测 |
| `guard` | 生成前置守卫 |
| `copy_skills` | init 技能复制 |

## 在其它脚本中引用

```python
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "lib"))

from kaka_scripts.paths import find_repo_root
from kaka_scripts.links import setup_ai_env_links
```

CLI 入口放在 `scripts/py/`，通过 `sys.path` 引入本库。
