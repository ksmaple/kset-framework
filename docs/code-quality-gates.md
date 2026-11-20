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

调整规则时，先核对全仓存量违反项与误报，再更新规则配置和本文档，并从根目录验证对应阶段；不要用全局跳过参数掩盖违反项。
