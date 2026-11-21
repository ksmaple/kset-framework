# Java/Maven 代码质量门禁

本规范适用于 `kset-framework` 根目录的 Maven reactor。根目录 `.mvn/maven.config` 默认激活 `code-quality` profile；继承 `kset-boot-parent` 的其他仓库不会自动激活该 profile。所有启用的检查均在发现违反项时使对应 Maven 命令失败。

## 执行命令

| 命令 | 检查范围 |
| --- | --- |
| `mvn validate` | 全仓模块的 Enforcer、Checkstyle、PMD；不编译、不运行测试 |
| `mvn verify` | 在 `validate` 的检查之外，编译生产和测试源码，运行模块测试与 ArchUnit |
| `mvn -pl kset-architecture-tests -am test` | 选择架构测试模块；`-am` 同时构建并测试其依赖模块 |

命令须从仓库根目录执行，且不附加跳过质量检查或测试的参数。只选择部分模块运行 `validate` 不代表全仓通过。

## 强制规则

| 阶段 | 工具 | 规则 |
| --- | --- | --- |
| `validate` | Maven Enforcer | JDK 21、Maven 3.9+、禁止重复声明 POM 依赖版本；适用于继承父 POM 的模块 |
| `validate` | Checkstyle | 主源码和测试源码均检查 Tab、文件末尾换行、通配符/冗余/未使用导入、包/类型/方法/字段/参数/局部变量命名、修饰符顺序、字符串字面量引用比较、控制流大括号、空语句、一行多条语句及常用空白；允许空匿名类型体 |
| `validate` | PMD | 主源码和测试源码均检查捕获 `NullPointerException`、空 `catch`（注释不豁免）、`finally` 中的 `return`、小数字面量构造 `BigDecimal`、`printStackTrace` 和 `System.out/err` 打印 |
| `compile` | Maven Compiler | 按父 POM 的 Java 21 `release` 编译生产源码 |
| `test` | Maven Surefire | 执行模块测试；`kset.skipModuleTests` 默认 `false` |
| `test` | ArchUnit | 生产类的 `com.kset` 顶层包无循环依赖，`common`、`cloud` 共享包不反向依赖指定 Starter 实现包 |

Checkstyle 的实际规则以 [`.mvn/checkstyle.xml`](../.mvn/checkstyle.xml) 为准，PMD 以 [`.mvn/pmd-ruleset.xml`](../.mvn/pmd-ruleset.xml) 为准；插件绑定和版本见 [`kset-boot-parent/pom.xml`](../kset-boot-parent/pom.xml)。ArchUnit 规则位于 [`FrameworkArchitectureTest.java`](../kset-architecture-tests/src/test/java/com/kset/architecture/FrameworkArchitectureTest.java)。

新增代码沿用所在文件的四空格缩进、同一行左大括号和显式导入。当前没有统一格式化插件，也未对行长、导入排序或 Javadoc 覆盖率设置阻断门禁；这些项目不应被误报为已自动校验。

## 与研发规范的对应

| 项目条文 | 自动检查 | 覆盖边界 |
| --- | --- | --- |
| `kset-framework-coder` R250 | Checkstyle `StringLiteralEquality` | 拦截字符串字面量的 `==`/`!=`；可空变量和包装类比较仍需按业务语义审查 |
| R253 | PMD `AvoidDecimalLiteralsInBigDecimalConstructor` | 拦截小数字面量经 `double` 构造 `BigDecimal`；变量类型、金额单位与溢出仍需审查 |
| R259、R282 | Checkstyle 导入、命名、修饰符及空白规则 | 保持常用 Java 写法；无法判断全限定名是否因冲突而必要，也不校验全部格式偏好 |
| R261 | PMD `EmptyCatchBlock`、`AvoidPrintStackTrace` | 注释不能替代异常处理；宽泛异常的日志字段与恢复结果仍需审查 |
| R262 | PMD `SystemPrintln` | 拦截直接控制台打印；日志内容、级别与脱敏仍需审查 |

资源关闭、状态与金额语义、SQL 参数化、业务线程调度和模块真实依赖边等约束依赖上下文，不以通用 Lint 的零告警宣称符合全部研发规范。

## 模块与发布边界

ArchUnit 在 `kset-architecture-tests` 模块的 `test` 阶段运行，仅扫描生产类。它按 Java 包名判断依赖；`kset-tuya` 和 `kset-starter-monitor` 也有生产类位于 `com.kset.common.*`，因此这两条规则不能替代 Maven 模块依赖关系检查。该测试模块不安装或发布。

根 POM 的发布插件传入 `-DskipTests -Dmaven.test.skip=true`，发布过程不会运行模块测试或 ArchUnit。发布前须单独执行未跳过测试的 `mvn verify`。仓库目前未配置自动运行这些命令的 CI 工作流，也未接入 SpotBugs 或覆盖率阈值门禁。

`kset-boot-parent/pom.xml` 在 `code-quality` 之后有一段注释掉的 `api-compat` profile，使用 japicmp `0.27.0` 对照已发布版本 `1.0.16`。该段不进入默认构建，也未写入 `.mvn/maven.config`。

## 门禁目录

质量插件定义在 `kset-boot-parent/pom.xml`。`.mvn/maven.config` 只在本仓库激活 `code-quality`。其他仓库继承该 parent 时不自动打开这套检查。

| 期次 | 工具 | 配置 | 命令 | 默认 |
| --- | --- | --- | --- | --- |
| 第一期 | Maven Enforcer | `kset-boot-parent/pom.xml`，JDK `[21,22)`、Maven 3.9+、禁止重复声明依赖版本 | `mvn validate` | 开，已生效 |
| 第一期 | Checkstyle 10.26.1 | `.mvn/checkstyle.xml`，由 `code-quality` 绑定到 `validate` | `mvn validate` | 开，已生效 |
| 第一期 | PMD，现行 6 条 | `.mvn/pmd-ruleset.xml`，由 `code-quality` 绑定到 `validate` | `mvn validate` | 开，已生效 |
| 第一期 | Surefire | 父 POM `pluginManagement`，`kset.skipModuleTests` 默认 `false` | `mvn verify` | 开，已生效 |
| 第一期 | ArchUnit 1.5.1 | `kset-architecture-tests` 的 `FrameworkArchitectureTest` | `mvn verify` | 开，已生效 |
| 第二期 | japicmp 0.27.0 | `kset-boot-parent/pom.xml` 中注释掉的 `api-compat`，对照 `1.0.16` | 启用前不执行 | 关 |
| 第三期 | 无 | 无 | 无 | — |

未进入上表的工具不配置。Spotless 会改写现有格式。JaCoCo 失败阈值的存量预估为高。SpotBugs 与已生效的 PMD 同组，默认规则预估为中，且尚未实际扫描。

## 存量修复优先级

1. 第一期没有待修的存量违反。根目录 `mvn validate` 已通过，Enforcer、现行 Checkstyle 和现行 PMD 保持现状。
2. 改动测试或包边界后，以及每次发布前，从仓库根目录执行不带跳过测试参数的 `mvn verify`，确认 Surefire 和 ArchUnit 的循环依赖规则。门禁定稿时没有重新运行测试，因此这里没有已知失败项。
3. 取消 `api-compat` 注释之前，先保持两个 `breakBuild` 为 `false`，对照 `1.0.16` 出一份差异报告。只有报告里确认的二进制不兼容才进入修复。`kset-tuya`、`kset-starter-sentinel`、`kset-starter-agent-harness` 没有测试源码，但不因此安排覆盖率修复。SpotBugs 不进入修复队列。

## 豁免原则

豁免写在对应规则配置或本文档里，并写明范围和原因。不用 `checkstyle.skip`、`pmd.skip`、`-DskipTests` 这类全局开关掩盖违反项。

- Checkstyle 继续允许空匿名类型体，不扩大到行长、导入排序或全仓格式化。
- PMD 的空 `catch` 不因注释豁免。
- ArchUnit 仍按包名判断，不能代替 Maven 模块依赖检查。
- 发布插件可以继续带 `-DskipTests -Dmaven.test.skip=true`。发布前单独执行 `mvn verify`。
- japicmp 排除 `kset-architecture-tests`，不解析 `1.0.16` 中不存在的构件，只比较 public 成员。两个 `breakBuild` 在差异确认为低之前保持 `false`。

存量预估为高，或还没有测量结果的规则，不设为默认阻断。

## 规则调整流程

1. 写明新增规则检查什么，以及它和现有 Enforcer、Checkstyle、PMD、Surefire、ArchUnit 是否重复。
2. 用当前全仓代码估计存量违反和最可能的误报。预估为高时保持关闭，不做全仓重格式化。
3. 同组已有工具时沿用现有工具，不并行加第二套。
4. 修改规则配置和本文档，再从仓库根目录执行对应命令。
5. `api-compat` 保持注释。只有差异报告确认影响面为低，并先改本文档，才取消注释。不把它写入 `.mvn/maven.config`。

## 提交前清单

- 改了 Java 源码、测试或质量插件时，在仓库根目录执行 `mvn validate`。
- 改了包之间的依赖、ArchUnit 规则或测试行为时，再执行不带跳过测试参数的 `mvn verify`。
- 日常提交不附带 `checkstyle.skip`、`pmd.skip`、`-DskipTests` 或 `-Dmaven.test.skip=true`。
- 不取消 `kset-boot-parent/pom.xml` 里 `api-compat` 的注释，除非本文档已经写成启用。
- 发布仍走现有发布参数；发布前另执行一次 `mvn verify`。
