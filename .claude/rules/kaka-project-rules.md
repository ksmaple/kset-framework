# 项目规则（全局 Kaka 规则）

> 本文件为平台与业务项目共用的全局规则；条文一律 `R{NNN}: 描述`。  
> 书写细则：[format-convention.md](../skills/kaka-coder-designer/references/format-convention.md)。研发规范见 `kaka-coder-designer` 与 `{proj}-coder`。

R001: 所有回复必须使用中文
R001a: 每轮执行或继续工作时，必须声明引用了哪些技能（skill）和规则（rule），格式：「当前执行引用：技能 `{skill-name}`，规则 `{文件名}` R{NNN}、…」。项目规则须带文件名（如 `kaka-project-rules` R007、R028），避免与各技能内 R001/R007 混读。若仅依赖项目默认规则，声明「当前执行引用：项目默认规则」
R001b: 若任务明显属于某个技能的触发条件范围但未引用该技能，须暂停执行并提示用户：「当前任务涉及 {功能描述}，建议引用技能 `{skill-name}`。是否确认不引用直接执行？」用户确认后方可继续
R001c: 技能引用声明须放在回复开头，用户可见；禁止在上下文内部隐藏引用信息
R002: 代码中技术标识符、文件名、API 名保留英文，不强制翻译
R003: YAGNI — 三个相似块优于一个假设的抽象
R004: 遵循现有代码风格，不重构未触及的代码
R005: 自解释代码优先，注释只解释 why 不解释 what
R005a: 源码中的注释（行注释、块注释、文档注释）须使用中文，技术标识符仍按 R002 保留英文
R005b: 下列可豁免 R005a：第三方或工具生成代码、须保留原文的许可证头、框架或契约强制英文的文档块
R006: 一次变更只解决一个问题，新功能不格式化未触及的模块
R007: 编码、多文件修改或跨业务域任务执行前须先列执行清单（todo list）；禁止无清单直接开始编码、修改文件或执行命令
R007d: 下列情况可豁免 R007，但须在引用声明中写明「豁免 R007」：纯问答/代码阅读、单文件 ≤10 行且无副作用、用户明确只要结论不要改代码
R007a: 任务清单是编排状态的唯一权威来源（single source of truth），对话记忆不得替代文件记录
R007b: 切换子任务前须先更新任务清单状态，未更新状态不得启动下一子任务
R007c: 长任务（>3 分钟或 >1 业务域）任务清单须包含：目标（1 句话）、子任务列表（含 ID、业务域标签、状态、目标文件）、依赖关系、验收标准。短任务清单可简化，但至少包含待办项和状态
R008: 子任务粒度要求：单一业务域、可独立执行（输入输出明确）、预估 ≤3 分钟且 ≤10 轮对话。子任务状态严格流转：pending → in_progress → completed | blocked，无其他状态
R008a: 子任务完成后立即更新任务清单状态并输出摘要（≤5 行），禁止在上下文中累积已完成子任务的详细记录
R008b: 所有子任务完成后，任务清单归档到 tmp/archive/{yyyyMMdd}/，保留 30 天；标记 keep 的可延期删除
R009: 多分支并行或 AI 改码场景须使用 git worktree，禁止在主工作树反复 checkout（工作区隔离细则见 R039）
R010: 所有临时文件放入 tmp/，任务完成后清理
R011: Markdown 为默认文档格式，图表使用 Mermaid
R012: 配置按技能隔离，技能内 config/*.example 模板 + config/*.local 密钥，禁止全局 .env
R013: 上下文窗口预算按模型实际容量预留 20%，有效预算 = 80% 标称值；子任务超过 10 轮对话或读取 >20 个文件须触发 checkpoint 和上下文清理
R014: 子任务启动时只传递该子任务必需的上下文（相关文件路径、前置子任务摘要），禁止传递整个项目背景
R015: 子任务完成后上下文只保留：摘要（≤5 行）、输出文件路径、阻塞项；详细产物（代码片段、探索路径、错误日志）必须从上下文中丢弃
R016: 跨子任务切换前须将当前状态写入 checkpoint 文件，然后主动清理前一子任务的详细上下文
R017: 禁止用「怕丢失信息」为由拒绝清理已完成子任务的详细记录；禁止将历史详情塞回 prompt 补偿跳过的清理
R018: 对话中声称的进度须与任务清单中的状态一致，无文件记录支撑的进度声明无效
R019: 范围变更、环境漂移、工具 flaky、外部依赖变更视为干扰项，须记录时间戳和影响，禁止静默扩大验收标准
R020: 所有字符读取、文件读写操作默认使用 UTF-8 编码，禁止依赖系统默认编码；涉及中文内容时必须显式指定 charset=UTF-8，防止乱码
R020a: 发现中文乱码时，须据源码、配置或运行环境语义重新生成正确中文并整段写入
R020b: 禁止对乱码文本做字符级替换、转码修补或基于乱码字面猜测填空
R030: 修改仓库内源码、配置或 DDL 前须先输出变更计划（目标、影响文件、要点与风险）；未经用户明确授权（如「确认执行」「按方案修改」）禁止写入文件或执行会改变仓库状态的命令
R030a: R030 与 R007 配合：先提交变更计划并获授权，再列任务清单后实施；用户已授权且任务为单文件 ≤10 行无副作用时，可在引用声明中写明「豁免 R030」
R031: 同一功能在短期内多次迭代时，变更计划须盘点并列出将被替代的旧实现（类、方法、API、路由、配置等）
R031a: 新方案落地后须删除已废弃的旧代码，或按用户明确要求回退到指定旧版；禁止仓库内并存多套同功能平行实现
R031b: 禁止以持续新增文件、类或接口「叠罗汉」保留旧版；若用户要求新旧并存，须在变更计划中写明并存原因、入口边界与下线计划
R032: 用户可见的 Markdown 文档、设计说明与对话回复须采用金字塔原理：首段给出结论或核心判断，其后按「要点 → 论据 → 细节」自上而下展开
R033: 变更计划与任务清单（含 R007、R030 产出）须金字塔编排：顶层一行目标或结论，其下为分步任务、影响范围与验收标准；同层条目逻辑并列、互不重叠（MECE）
R034: 进度汇报与收尾摘要须结论前置（≤3 行），过程细节、日志与排查路径放在其后或附录，禁止先写过程后给结论
R035: 源码中引用类型、方法、常量须通过文件头 import/using/from 等引入，禁止在方法体或字段声明中使用包名或模块路径的全称限定写法（细则见 coding-spec C031）
R035a: 仅当简单名冲突且无法删去其一 import 时，允许对其中一个类型保留全称，须在变更说明中注明冲突原因
R036: 新建或补充仓库内自动化脚本（含工具、批处理、迁移与校验脚本）须统一放入 `scripts/`，禁止散落在仓库根或其他业务目录
R036a: `scripts/` 内自动化脚本默认使用 TypeScript，实现置于 `scripts/ts/` 与 `scripts/lib/kaka-scripts/`，扩展名 `.ts`
R036b: 下列情形可豁免 R036a：用户明确要求其他语言；终端单行命令（如 `git`、`npm`）；scripts 入口统一为 `scripts/package.json` 的 `npm run` 或在 `scripts/` 内执行 `npx tsx ts/…`，禁止再增 `.ps1`/`.sh` 薄封装
R037: `scripts/` 内 TypeScript 源码、脚本 README 及相关说明 Markdown 须以 UTF-8 保存与读写，与 R020 一致；纯文本优先 UTF-8 无 BOM
R037a: Node/TypeScript 读写文本或 JSON 须经 `scripts/lib/kaka-scripts/io` 或显式 `utf-8`/`utf8` 选项，禁止依赖进程或系统默认 locale
R037b: 在 PowerShell 中执行 `npm --prefix scripts run` 且输出含中文时，建议将 `$OutputEncoding` 与 `[Console]::OutputEncoding` 设为 UTF-8
R037c: 在 Bash 中执行 `npm --prefix scripts run` 且输出含中文时，建议设置 `LC_ALL=C.UTF-8` 或等效 UTF-8 locale
R037d: 仓库根须提供 `.editorconfig`，对 `scripts/**`、`.claude/**` 下 Markdown 与规则文件统一 `charset = utf-8`
R037e: `.claude/rules`、`.claude/skills` 及 init 同步到业务项目的文本文件须 UTF-8 无 BOM 保存，禁止 EF BB BF 文件头
R037f: `npm --prefix scripts run copy:init-skills` 复制文本文件须经 `io.copyFileUtf8NoBom` 写出，确保目标无 BOM
R037g: 可提供 `npm --prefix scripts run check:utf8-nobom` 扫描 `.claude/**` 与 `scripts/**`；`-- --fix` 去除已存在的 BOM
R038: `scripts/package.json` 的 `engines` 须声明 scripts 最低 Node.js（≥18.18.0）与 npm（≥9.0.0）；`scripts/README.md` 须含「运行环境」节，版本号与 engines 一致
R038a: `scripts/README.md` 须含「常见异常」节，列典型现象、成因与处理，随脚本行为变更同步更新；文件编码/路径类问题与 `kaka-project-rules`「文件常见问题」表互补
R038b: 可提供 `npm --prefix scripts run check:scripts-env`（`scripts/ts/check_env.ts`）与 `scripts/.nvmrc`（推荐 Node 20 LTS）辅助自检；新增或升级 tsx/typescript 主版本时须同步 engines 与 README

## 文件与路径（R040–R047）

R040: 持久化文件路径须为仓库内相对路径或项目约定目录，禁止在规则/技能正文写入平台外绝对路径
R041: 临时与一次性产物须放 `tmp/`，任务结束须清理（与 R010 一致；checkpoint 可暂存后归档）
R042: 文本文件换行符以 LF 为准；`.ps1`/`.bat`/`.cmd` 可为 CRLF，见 `.gitattributes` 与 `.editorconfig`
R043: 文本文件须单个换行符结尾，保存时去除行尾空格（见 `.editorconfig` 的 `insert_final_newline`、`trim_trailing_whitespace`）
R044: 禁止将密钥、令牌、私钥、数据库连接串写入仓库；配置用 `*.example` 模板 + `*.local` 本地密钥或环境变量注入
R045: 生成物须写入约定目录：`tmp/`（临时）、`.claude/design/`（设计文档）、`scripts/`（自动化工具），未经用户要求禁止在仓库根新增说明类 Markdown
R046: 技能、规则、设计文档以 `.claude/` 为物理单源；`.agents/`、`.cursor/` 仅薄索引或目录链接，禁止维护正文副本
R047: 禁止将编译产物、运行日志、数据库转储提交入库；`tmp/`、`node_modules/`、`dist/`、`build/` 等须列入 `.gitignore`

### 文件常见问题（参考）

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 中文乱码、问号方块 | 非 UTF-8 或依赖系统默认 locale | 先修环境 UTF-8（R020、R037b/c）；再按代码/环境**重新生成**中文（R020a），禁止替换乱码（R020b） |
| 文件首行异常、grep 首列匹配失败 | UTF-8 BOM（EF BB BF） | R037e；`npm --prefix scripts run check:utf8-nobom -- --fix` |
| Git 整文件 diff、仅换行变化 | CRLF 与 LF 混用 | R042；统一 LF，查 `.gitattributes` |
| `git diff --check` 报 trailing whitespace | 行尾空格未清理 | R043；编辑器开启 trim；保存前自检 |
| 业务仓规则/技能落后于平台 | 未重跑 init 复制 | `npm --prefix scripts run copy:init-skills -- <path> --force`（R037f） |
| 三端技能内容不一致 | `.agents`/`.cursor` 实体副本未改链接 | R046；`npm --prefix scripts run setup:ai-env-links` |
| 误提交 `.env`/密钥 | 未排除敏感路径 | R044；`kaka-util-git-commit` 敏感文件确认 |
| 根目录堆积 `NOTES.md`/临时 md | 未用约定目录 | R045；移入 `.claude/design/` 或 `tmp/` |
| `copy:init-skills` 退出码 2 | 目标技能/规则已存在 | 加 `--force` 或先备份后覆盖 |
| 控制台中文乱码 | PowerShell 代码页非 UTF-8 | `chcp 65001` 或 Windows Terminal；仍乱码则按 R020a 重新生成中文，禁止替换乱码 |

## Git 工作区与分支（R009、R039）

R039: AI 新会话且任务将修改仓库内源码、配置或 DDL 时，须在独立 git worktree 内实施
R039a: 会话 worktree 的基点分支须据任务选定：用户指定优先，其次主工作树当前检出分支，再次仓库默认集成分支（origin/HEAD 或 main/master）
R039b: 会话分支优先复用基点分支或用户指定的既有功能分支
R039c: 须新建会话分支时，分支名须可识别本次任务（可含 session/agent，或与 `kaka-util-git-commit` 分支命名一致）
R039d: 禁止在主工作树检出会话专用分支
R039e: 禁止在主工作树修改将被 git 提交的源码、配置或 DDL
R039f: 禁止未经用户明确要求向默认集成分支直接提交或合并会话变更
R039g: 下列可豁免 R039：纯问答与代码阅读、用户明确指定在主工作树改码、非 git 仓库且用户确认
R039h: git commit、push 及提交分支策略以 `kaka-util-git-commit` 为准，与本节工作区隔离规则互补

## 技能与规则书写（R021–R029）

R021: 平台 `.claude/skills/**/SKILL.md` 与技能 `references/` 中强制约束条文必须使用 `R{NNN}: 描述` 格式，一行一条
R022: `.claude/rules/kaka-*.md` 条文必须使用 `R{NNN}: 描述` 格式，且在同一仓库内所有 rules 文件间编号全局唯一
R023: R 编号从 001 连续；允许字母后缀（如 R007a）；禁止跳号、重复编号、空规则行
R024: 一条 R 条文只表达一个约束，禁止一条内多个「须/禁止」
R025: 技能 **核心规则** 与项目规则中，禁止用 Markdown 无序/有序列表代替 R 编号表达强制约束（索引表、协作路由表除外）
R026: 工作流步骤可用列表或简短代码块，其中不得写入须/禁止类约束，约束须写在 R 编号条文中
R027: API/DDD/SQL/命名等研发方法论不得写入本规则文件重复全文，权威细则在 `kaka-coder-designer/references/*-spec.md`（域前缀 N/A/S 等，禁止用 R 前缀）
R028: 新建或修订技能、规则、域 spec 前须阅读 `kaka-coder-designer/references/format-convention.md`
R029: 引用规范须标明出处：项目规则写 `kaka-project-rules R{NNN}`，技能写 `{skill-name}` 与 R 编号，域 spec 写域前缀与编号（如 naming N008）
