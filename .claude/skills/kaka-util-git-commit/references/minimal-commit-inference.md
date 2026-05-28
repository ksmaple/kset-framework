# 极简提交推断（仅路径）

> 供 Step 5 与 `{feature-slug}` 使用；禁止为推断而 `git diff`、Read 工具读文件或逐行扫内容。

## 输入

1. `git status --porcelain` 全量路径（工作区 + 暂存区，去重）
2. 用户 slash/消息中显式给出的 `feature-slug` 或提交说明（有则优先）
3. 可选：`git diff --stat` / `git diff --shortstat` **仅**用于 R046 大改动计数（不看 hunk 正文）

## `{feature-slug}`（≤28 字符、≤3 词、kebab-case）

| 优先级 | 规则 | 示例 |
|--------|------|------|
| P0 | 用户已给 slug | 原样归一化 |
| P1 | 单技能/单模块目录下集中变更 | `.claude/skills/kaka-util-git-commit/**` → `git-commit` |
| P2 | 最长公共路径段（≥2 文件） | `src/order/OrderService.java` + `src/order/OrderRepo.java` → `order` |
| P3 | 单文件：去扩展名与常见后缀 | `OrderController.java` → `order`；`SKILL.md` 取父目录名 |
| P4 | 多顶层目录且无公共段 | `mixed-{首目录}` 或 `batch-{yyyyMMdd}` |

归一化：小写；去 `kaka-`、`{proj}-` 前缀；`Test`/`Spec`/`Fixture` 后缀剥离后再 kebab-case。

## `<type>`（路径启发，不读 diff）

| type | 路径信号 |
|------|----------|
| test | `**/test/**`、`**/*Test.*`、`**/*.spec.*`、`**/__tests__/**` |
| docs | `**/*.md`（非 SKILL 主入口时可 docs）、`docs/**`、`README*` |
| chore | 根配置、`scripts/**`、`.github/**`、仅 lock/gradle/pom 无 src |
| style | 仅 `**/*.{css,scss,less}` 或格式化配置 |
| refactor | 路径含 `refactor`/`rename` 或用户说明含重构 |
| fix | 路径含 `fix`/`hotfix`/`bugfix` 或用户说明含修复 |
| feat | 默认（含 `src/**`、领域/API/技能业务路径） |

多信号并存：用户说明 > fix > feat > chore > docs > test。

## 中文标题（≤50 字）

1. 用户已给说明 → 压缩为祈使句标题，去掉「请」「一下」
2. 否则由**主导路径**生成，不罗列文件名：
   - 技能：`.claude/skills/kaka-util-git-commit` → `优化 Git 提交技能`
   - 领域模块：`src/.../order/...` → `调整订单模块…`（动词按 type：feat→新增/调整，fix→修复，docs→更新文档）
   - 单文件：用去后缀的语义名，如 `git-commit-spec.md` → `更新 Git 提交细则`
3. 禁止标题仅为 `更新 a.java、b.java`；多目录无主题时用「同步多处配置与脚本」类概括

## 与当前分支比对（Step 3，仅分支名）

在得到本次 `{feature-slug}`、`{author-slug}` 后，解析 `git branch --show-current`（规则见 git-commit-spec R049p）：

| 当前分支解析 | 本次推断 | Step 3 |
|--------------|----------|--------|
| feature、author 均一致 | 同左 | **留驻**，不 checkout |
| 不一致或当前为 `main`/`master` | 任意 | checkout 已有或新建 `<target>` |
| 无法解析（非 `feat/dev-*`） | — | 按 spec R049c–R050 |

示例：当前 `feat/dev-20260527-git-commit-kmb`，变更均在 `kaka-util-git-commit/**` → 推断 `git-commit` + `kmb` → **不切换**。

## 禁止（提交推断阶段）

- `git diff`、`git diff --staged`、`git show` 的 patch/hunk
- Read/Grep 打开变更文件正文
- 为写 commit 而 `git log -p` 看历史 diff

## 允许（非推断）

- `git diff --check`：冲突标记（R031）
- `git diff --shortstat`：仅 R046 行数统计
- `git status`、`git rev-parse`、`git branch` 等元数据命令
