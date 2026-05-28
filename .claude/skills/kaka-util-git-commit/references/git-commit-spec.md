# Git Commit 执行细则

> 本文件为 `kaka-util-git-commit` 的步骤级约束；SKILL.md 仅保留门禁与工作流索引。

## 仓库检测

R001: 执行 `git rev-parse --git-dir` 检测是否为 Git 仓库
R002: 是 Git 仓库则进入变更检测（工作流 Step 2）
R003: 非 Git 仓库则在子目录搜索 `.git`（`find` 限定深度 ≤3），找到则询问用户是否切换目录，未找到则终止

## 变更检测

R010: 执行 `git status --porcelain` 检查工作区与暂存区
R011: 工作区与暂存区均为空则终止，提示无变更可提交
R012: 有变更则进入一致性检查（Step 2.5）、冲突检查（Step 2.6）、分支（Step 3）

## 变更一致性（Step 2.5）

R020: 检测工作区+暂存区是否存在疑似无关的多主题变更
R021: 启发式（**仅路径**，禁止读 diff）：顶层目录分散（≥3 个无公共前缀的一级路径）、技能与业务 src 混改、test 与 prod 源码无关联混改
R022: 疑似不一致时提示用户选择：206-a 同一批提交 / 206-b 拆暂存 / 206-c 分批分支
R023: 非交互场景须输出选项并暂停，用户确认后方可继续

## 同文件双改与冲突标记（Step 2.6）

R030: 检测 `git status --porcelain=v1` 中 MM/AM/RM/DM 等同文件双改
R031: 优先 `git diff --check` 检测冲突标记；禁止为 R031 而 Read 文件或查看 diff hunk 正文
R032: R030 或 R031 命中则终止，输出 Markdown 表（路径、恢复建议：git add / git restore --staged / 手工编辑）
R033: R030/R031 未清除前禁止 `git add -A`

## 分支策略（Step 3）

R039: Step 3 **开头**先据路径列表得到 `{feature-slug}`、`{author-slug}`（R044–R045），再计算 `<target>`；禁止为分支判定读 diff
R040: 每次执行计算候选目标分支 `<target>`（用于新建或精确匹配）；**不得**因当日日期与当前分支日期不同而否定 R049a 留驻
R041: 解析主分支 `{primary}`：优先 `git symbolic-ref refs/remotes/origin/HEAD`，回退 main/master/项目约定
R042: 禁止以 dev、develop、任意 `feat/dev-*` 作为新建分支基点
R043: 日期段 `{yyyyMMdd}` 取本机日历（PowerShell `Get-Date -Format yyyyMMdd` / Unix `date +%Y%m%d`）
R044: `{author-slug}` 来自 `git config user.name`，归一化为 `[a-z0-9-]{1,32}`
R045: `{feature-slug}` ≤28 字符、≤3 个英文词、kebab-case；**仅**据路径列表推断，规则见 [minimal-commit-inference.md](minimal-commit-inference.md)
R046: 大改动判定：变更文件数 ≥12；或 `git diff --shortstat` 的 insertions+deletions ≥400（仅统计，禁止读 patch）
R047: 分支尾 `{tail}`：大改动为 `dev-{yyyyMMdd}-{NN}-{feature-slug}-{author-slug}`，否则 `dev-{yyyyMMdd}-{feature-slug}-{author-slug}`
R048: 完整目标分支 `<target>` = `feat/{tail}`

### 当前分支解析（R049a 用）

R049p: 当前分支名匹配 `feat/dev-{yyyyMMdd}(-{NN})?-*` 时解析：
R049p-1: `{author-slug}` = 最后一段（须与 R044 一致）
R049p-2: 去掉前缀 `feat/dev-`、8 位日期、可选 2 位 `{NN}` 及紧随其后的 `-` 后，余下去掉末段即为分支内 `{feature-slug}`（kebab-case，可与路径推断结果比对）
R049p-3: 非 `feat/dev-*` 形态（如 `main`、`feat/foo`）视为无解析结果，不走 R049a

### 复用判定（按序，命中即停止 Step 3 其余动作）

R049a: **留驻当前（同功能）**：R049p 解析出的 `{feature-slug}`、`{author-slug}` 与本次路径推断**均一致** → **禁止** checkout、stash、`checkout -b`；直接 Step 4；输出「同功能分支，未切换」
R049b: **留驻当前（精确名）**：当前分支名 **等于** `<target>` → 同 R049a
R049c: **切换精确 target**：R049a/b 未命中，且本地或远程存在分支 `<target>` → `git checkout <target>`（禁止 `-b`）
R049d: **切换同功能异名**：R049a–c 未命中，且 `git branch` / `git branch -r` 存在 `feat/dev-*-{feature-slug}-{author-slug}`（feature、author 与本次推断一致，日期或 `{NN}` 可不同）→ `git checkout` 该分支（多个时取本地最近更新者；仍禁止 `-b`）
R050: 仅当 R049a–d **均未**命中时新建：stash → fetch → checkout `{primary}` → pull --ff-only → 验证 HEAD 为 `{primary}` → `checkout -b <target>` → stash pop
R051: checkout 或 stash pop 失败则终止，由用户修复后重试
R052: 当前在 `{primary}` 且变更路径推断的 feature 与当前分支（若可解析）不一致时，**不得** R049a 留驻，须 R049c/d 或 R050

## 暂存（Step 4）

R060: 已有暂存内容则复用，禁止重复全量暂存
R061: 无暂存内容时使用 `git add -A`（须已通过 R030/R031）
R062: 扫描敏感文件：`.env`、`*.pem`、`credentials*`、`secrets*` 等，命中须用户确认
R063: 用户拒绝敏感文件暂存则终止

## 提交信息（Step 5，极简路径推断）

R070: 生成 `<type>: <中文标题>`，标题 ≤50 字
R071: **禁止**为生成提交信息执行 `git diff` / `git diff --staged` / `git show` 或 Read/Grep 变更文件正文；输入为 `git status --porcelain` 路径列表 + 用户显式说明
R072: type 前缀：feat/fix/docs/style/refactor/test/chore/perf；路径启发见 [minimal-commit-inference.md](minimal-commit-inference.md)
R073: 提交信息使用中文；祈使语气；概括功能而非罗列文件名
R074: 需正文时用 HEREDOC；可含影响范围；关联 issue 写 `关联 #123`
R075: `{feature-slug}` 与 Step 3 分支名中的功能段共用同一套路径推断（R045、minimal-commit-inference）
R076: 用户 slash/消息已给 feature-slug 或标题时，归一化后优先采用，覆盖启发式
R077: 单主题变更（路径集中）时标题应点出模块/技能名；禁止「更新多个文件」式空泛标题
R078: 推断不确定时可用一句保守概括（如「调整订单模块相关代码」），**不得**为此去读 diff
R079: 自检：本次 commit 流程未调用过带 patch 输出的 diff 命令（`--check`/`--shortstat` 除外）

## 提交（Step 6）

R080: 执行 `git commit -m "<标题>"`（用户已明确要求 commit 时）
R081: 输出 commit hash 与标题
R082: 禁止 `--no-verify`、`--force`、`--force-with-lease`

## 推送（Step 7）

R090: push **仅当**用户明确要求 push，或触发描述含 push、或 slash 命令含 push 意图时执行
R091: `git ls-remote --heads origin <target>` 检测远程分支
R092: 远程不存在则 `git push -u origin <target>`
R093: 远程已存在则 `git push origin <target>`
R094: non-fast-forward 时提示 rebase，禁止强推；网络错误保留本地 commit 并报告

## 输出物

R100: 须输出分支名、提交信息、commit hash；若执行 push 则输出推送结果
R100a: R049a/b 留驻时须注明「同功能分支，未切换」及解析出的 feature-slug
R101: R021 触发时输出用户选项 206-a/b/c 及简要理由
R102: R032 触发时输出路径表与恢复选项

## 自检（执行后）

R110: 已检测 Git 仓库、有变更、一致性/冲突检查已通过
R111: `{primary}` 非 dev/develop；`<target>` 格式正确
R112: R049a/b 未执行 checkout；R049c/d 未用 `checkout -b`；R050 新建时 HEAD 曾为 `{primary}`
R113: 提交信息符合 `<type>: <中文标题>` 且 ≤50 字；且符合 R071/R079（未读 diff 推断）
R114: 未使用 R082 禁止的参数；push 仅在 R090 允许时执行
