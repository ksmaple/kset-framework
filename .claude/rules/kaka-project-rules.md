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
R007b: 切换子任务前须先更新任务清单状态，未更新状态不得启动下一子任务
R007c: 长任务（>3 分钟或 >1 业务域）任务清单须包含：目标（1 句话）、子任务列表（含 ID、业务域标签、状态、目标文件）、依赖关系、验收标准。短任务清单可简化，但至少包含待办项和状态
R008: 子任务粒度要求：单一业务域、可独立执行（输入输出明确）、预估 ≤3 分钟且 ≤10 轮对话。子任务状态严格流转：pending → in_progress → completed | blocked，无其他状态
R009: 多分支并行或 AI 改码场景须使用 git worktree，禁止在主工作树反复 checkout（工作区隔离细则见 R039）
R010: 所有临时文件放入 tmp/，任务完成后清理
R011: Markdown 为默认文档格式，图表使用 Mermaid
R012: AI 任务研发开始前须先明确目标、约束、交付物与验证方式；信息不足以安全改码时须先澄清再实施
R013: AI 执行长任务、多步骤任务或跨业务域任务时，须维护用户可见的任务清单；可记录在对话回复、约定文档或用户指定载体中
R014: AI 读取上下文须遵循最小够用原则，优先当前任务相关文件、最近改动与直接依赖，禁止为防遗漏一次性铺满整个项目背景
R015: 子任务完成后的进度汇报须优先保留结论、影响文件、阻塞项与下一步，避免在对话中重复堆叠长日志、大段代码或已失效上下文
R016: 当用户需求、仓库现状、外部依赖或执行策略发生变化时，须先更新计划与影响评估，再继续后续子任务
R017: 仅当任务需要跨会话延续、多人协作或显式交接时，才要求将必要背景沉淀为文档；一次性短任务不强制持久化任务记忆

R018: 对话中声称的进度须与用户可见的任务清单或已落盘记录一致，禁止凭印象汇报进度
R019: 范围变更、环境漂移、工具 flaky、外部依赖变更视为干扰项，须记录时间戳和影响，禁止静默扩大验收标准
R020: 所有字符读取、文件读写操作默认使用 UTF-8 编码，禁止依赖系统默认编码；涉及中文内容时必须显式指定 charset=UTF-8，防止乱码
R020a: 发现中文乱码时，须据源码、配置或运行环境语义重新生成正确中文并整段写入
R020b: 禁止对乱码文本做字符级替换、转码修补或基于乱码字面猜测填空
R020c: 修改文件前后须确认中文内容未出现乱码，禁止提交或保留因编辑、复制、转码、终端输出导致的乱码文本
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
R036a: `scripts/` 内自动化脚本默认使用 Python 3.x，实现置于 `scripts/py/` 与 `scripts/lib/kaka_scripts/`，扩展名 `.py`
R036b: 下列情形可豁免 R036a：用户明确要求其他语言；终端单行命令（如 `git`、`python`）；scripts 入口统一为 `python scripts/py/…` 或在 `scripts/` 内执行 `python py/…`，禁止再增 `.ps1`/`.sh` 薄封装
R037: `scripts/` 内 Python 源码、脚本 README 及相关说明 Markdown 须以 UTF-8 保存与读写，与 R020 一致；纯文本优先 UTF-8 无 BOM
R037a: Python 读写文本或 JSON 须经 `scripts/lib/kaka_scripts/io` 或显式 `encoding='utf-8'` 选项，禁止依赖进程或系统默认 locale；读取可用 `utf-8-sig` 自动去除 BOM，写入必须用 `utf-8` 保证无 BOM
R037b: 在 PowerShell 中执行 `python scripts/py/…` 且输出含中文时，建议将 `$OutputEncoding` 与 `[Console]::OutputEncoding` 设为 UTF-8
R037c: 在 Bash 中执行 `python scripts/py/…` 且输出含中文时，建议设置 `LC_ALL=C.UTF-8` 或等效 UTF-8 locale
R037d: 仓库根须提供 `.editorconfig`，对 `scripts/**`、`.claude/**` 下 Markdown 与规则文件统一 `charset = utf-8`
R037e: `.claude/rules`、`.claude/skills` 及 init 同步到业务项目的文本文件须 UTF-8 无 BOM 保存，禁止 EF BB BF 文件头
R037f: `python scripts/py/init.py copy` 复制文本文件须经 `scripts/lib/kaka_scripts/io.copy_file_utf8_nobom` 写出，确保目标无 BOM
R037g: 可提供 `python scripts/py/check.py utf8` 扫描 `.claude/**` 与 `scripts/**`；`--fix` 去除已存在的 BOM
R038: `scripts/README.md` 须含「运行环境」节，声明 Python 版本要求（≥3.10），版本号与实现一致
R038a: `scripts/README.md` 须含「常见异常」节，列典型现象、成因与处理，随脚本行为变更同步更新；文件编码/路径类问题与 `kaka-project-rules`「文件常见问题」表互补
R038b: 可提供 `python scripts/py/check.py env` 检测 Python/git 可用性；新增或升级 Python 版本要求时须同步 README 与检测逻辑

## 脚本使用（R048–R049）

R048: AI 或自动化任务在仓库内执行文件批量操作、init 子步骤、质量检查、Git 辅助（非 commit/push）时，须**优先**使用 `scripts/py/` 已有 Python 子命令；权威场景表见 `kaka-util-scripts/references/script-usage-spec.md` 与 `scripts/README.md`「使用场景」
R048a: 已有内置子命令时，禁止用手工 shell 拼凑、根目录临时脚本或 `.ps1`/`.sh` 薄封装实现同等逻辑（与 R036b 一致）；无覆盖场景且非一次性只读探索时，须先扩展 `scripts/` 并注册文档，禁止写仓库外或散落脚本
R048b: 下列可豁免 R048，但须在引用声明写明「豁免 R048」：用户明确要求其他工具；只读单行 `git`；内置无子命令且任务为一次性只读探索；Git commit/push 全流程（以 `kaka-util-git-commit` 为准）
R049: 执行或推荐上述内置脚本时，须引用技能 `kaka-util-scripts`；init 流程编排仍以 `kaka-utils-project-init` 为准，其 Step 内脚本调用须符合 script-usage-spec

## init 与前后端契约（R050–R050a）

R050: `kaka-utils-project-init` 写 `project-spec` 时须登记前后端 DateTime 映射（wire 格式、时区、epoch 单位、后端序列化、前端日期库）；默认 API JSON 为 `yyyy-MM-dd HH:mm:ss` 字符串，后端 Jackson（api A027、A029、frontend F011）
R050a: 已有存量项目 init 时，若探测到与默认 DateTime 约定不一致，须写入 project-spec 差异项与「时间格式（前后端）」节，禁止静默按默认覆盖导致前后端或 DB 偏移

## 测试执行（R051–R060）

R051: 代码开发完成后默认不执行测试，除用户、任务或 CI 显式要求外测试阶段为 `SKIPPED`
R052: 显式测试默认只针对单个 Controller 接口做单点调用验证
R053: 显式测试须使用真实 Spring Boot 上下文与真实 Bean 方法调用
R054: 显式测试禁止使用 Mock、stub、fake 或内存替身替代真实依赖
R055: 显式测试须通过日志输出目标方法、上下文、输入入参与返回结果
R056: 显式测试禁止使用断言、Hamcrest、AssertJ 或 Spring ResultMatcher 判断结果
R057: 显式测试结果须仅通过日志框架输出
R058: 仅当无可调用 Controller 或用户明确点名 Service 时，才允许单个 Service 方法测试
R059: 显式测试禁止编排前置业务流程准备数据
R060: 显式测试报告只保留目标、上下文、入参、返回结果或异常摘要

## 文件与路径（R040–R047）

R040: 持久化文件路径须为仓库内相对路径或项目约定目录，禁止在规则/技能正文写入平台外绝对路径
R041: 临时与一次性产物须放 `tmp/`，任务结束须清理（与 R010 一致）
R042: 文本文件换行符以 LF 为准；`.ps1`/`.bat`/`.cmd` 可为 CRLF，见 `.gitattributes` 与 `.editorconfig`
R043: 文本文件须单个换行符结尾，保存时去除行尾空格（见 `.editorconfig` 的 `insert_final_newline`、`trim_trailing_whitespace`）
R044: 需要跨会话交接的设计说明、任务背景或决策记录须写入 `.claude/design/` 或用户指定目录，禁止在仓库根散落任务备忘
R045: 生成物须写入约定目录：`tmp/`（临时）、`.claude/design/`（设计文档）、`scripts/`（自动化工具），未经用户要求禁止在仓库根新增说明类 Markdown
R046: 技能、规则、设计文档以 `.claude/` 为物理单源；`.agents/`、`.cursor/` 仅薄索引或目录链接，禁止维护正文副本
R047: 禁止将编译产物、运行日志、数据库转储提交入库；`tmp/`、`node_modules/`、`dist/`、`build/` 等须列入 `.gitignore`

### 文件常见问题（参考）

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 中文乱码、问号方块 | 非 UTF-8 或依赖系统默认 locale | 先修环境 UTF-8（R020、R037b/c）；再按代码/环境**重新生成**中文（R020a），禁止替换乱码（R020b） |
| 文件首行异常、grep 首列匹配失败 | UTF-8 BOM（EF BB BF） | R037e；`python scripts/py/check.py utf8 --fix` |
| Git 整文件 diff、仅换行变化 | CRLF 与 LF 混用 | R042；统一 LF，查 `.gitattributes` |
| `git diff --check` 报 trailing whitespace | 行尾空格未清理 | R043；编辑器开启 trim；保存前自检 |
| 业务仓规则/技能落后于平台 | 未重跑 init 复制 | `python scripts/py/init.py copy <path> --force`（R037f） |
| 三端技能内容不一致 | `.agents`/`.cursor` 实体副本未改链接 | R046；`python scripts/py/init.py links` |
| 用手工命令代替内置脚本 | 未查 script-usage-spec | R048；引用 `kaka-util-scripts` 后用 `python scripts/py/…` |
| 根目录堆积 `NOTES.md`/临时 md | 未用约定目录 | R045；移入 `.claude/design/` 或 `tmp/` |
| `init.py copy` 退出码 2 | 目标技能/规则已存在 | 加 `--force` 或先备份后覆盖 |
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
R023: R 编号须在同一仓库规则文件内全局唯一；主编号默认使用未占用三位数，允许字母后缀（如 R007a）；为保持引用稳定，删除后允许保留历史空洞，但禁止复用旧编号、重复编号或空规则行
R024: 一条 R 条文只表达一个约束，禁止一条内多个「须/禁止」
R025: 技能 **核心规则** 与项目规则中，禁止用 Markdown 无序/有序列表代替 R 编号表达强制约束（索引表、协作路由表除外）
R026: 工作流步骤可用列表或简短代码块，其中不得写入须/禁止类约束，约束须写在 R 编号条文中
R027: API/DDD/SQL/命名等研发方法论不得写入本规则文件重复全文，权威细则在 `kaka-coder-designer/references/*-spec.md`（域前缀 N/A/S 等，禁止用 R 前缀）
R028: 新建或修订技能、规则、域 spec 前须阅读 `kaka-coder-designer/references/format-convention.md`
R029: 引用规范须标明出处：项目规则写 `kaka-project-rules R{NNN}`，技能写 `{skill-name}` 与 R 编号，域 spec 写域前缀与编号（如 naming N008）
