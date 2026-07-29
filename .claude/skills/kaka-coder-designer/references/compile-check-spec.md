# 按需编译校验规范

> kaka-coder-designer · `{proj}-coder` / `{proj}-fixer` 仅在用户、任务或 CI 显式要求时执行

---

## R001 编译校验目的

- 代码生成后的默认验收是文件逻辑完成与静态自查，不自动触发编译。
- 仅当显式声明 `runCompile=true` 或 `runTests=true` 时，编译校验才进入执行流程。
- 编译不通过时，返回编译错误报告；未显式声明的编译或测试任务不会触发。

---

## R002 编译校验范围

### 2.1 后端编译
- **构建工具**：Maven 或 Gradle
- **校验命令**：
  - Maven：优先使用项目探测出的 compile-only 命令；未登记时使用 `mvn compile -DskipTests`
  - Gradle：优先使用项目探测出的 compile-only 命令；未登记时使用 `./gradlew compileJava -x test`
- **禁止范围**：默认不执行 `testCompile`、`compileTestJava`、`test`、`verify`、`package`、`install`、`check` 或全量聚合任务。

### 2.2 前端编译
- **语言**：TypeScript
- **校验命令**：
  - Vue 项目：`vue-tsc --noEmit`
  - 普通 TS 项目：`tsc --noEmit`
- **禁止范围**：默认不执行测试、打包、端到端校验、覆盖率或生产构建。

### 2.3 耗时控制
- 编译校验只覆盖本次变更影响的最小模块；多模块项目优先使用 `-pl`、`:module:compileJava` 等单模块命令。
- 发现默认命令会触发测试编译、打包或长时间全量构建时，须改用更小的 compile-only 命令或跳过并说明原因。

---

## R003 编译校验步骤

1. **显式触发**：仅在用户、任务或 CI 明确要求时，触发最小 compile-only 编译校验流程。
2. **输出收集**：收集编译输出（stdout / stderr）。
3. **错误解析**：解析编译错误，定位到具体文件和行号。
4. **报告生成**：根据解析结果生成结构化编译报告。

---

## R004 编译报告格式

编译报告必须采用以下 JSON 结构：

```json
{
  "compileCheckId": "compile-20240523-001",
  "status": "PASSED|FAILED",
  "mode": "COMPILE_ONLY|COMPILE_AND_TEST",
  "module": "kset-rag-server",
  "command": "mvn compile -DskipTests",
  "durationMs": 15000,
  "errors": [
    {
      "file": "domain/entity/Order.java",
      "line": 25,
      "column": 10,
      "message": "cannot find symbol: class OrderStatus",
      "severity": "ERROR"
    }
  ],
  "warnings": [
    {
      "file": "application/service/OrderService.java",
      "line": 40,
      "message": "unchecked conversion",
      "severity": "WARNING"
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `compileCheckId` | string | 编译校验唯一标识，格式：`compile-{YYYYMMDD}-{序号}` |
| `status` | string | 校验结果：`PASSED` 或 `FAILED` |
| `mode` | string | 执行模式：`COMPILE_ONLY`（默认）或 `COMPILE_AND_TEST` |
| `module` | string | 所属模块名称 |
| `command` | string | 实际执行的编译命令 |
| `durationMs` | number | 编译耗时（毫秒） |
| `errors` | array | 错误列表，每项包含文件、行号、列号、消息、严重级别 |
| `warnings` | array | 警告列表，结构同 `errors` |

---

## R005 编译失败处理策略

| 错误类型 | 处理策略 |
|---------|---------|
| 单文件错误 | 自动修复（如缺少 import、类型不匹配） |
| 依赖缺失 | 检查是否漏生成依赖文件，触发补全生成 |
| 跨层引用错误 | 检查包依赖方向是否违反规范，阻断并报告 |
| 无法自动修复 | 标记为 `BLOCKED`，等待人工确认 |

---

## R006 与测试的关系

- 默认模式下，编译校验不执行，代码落地以文件逻辑完成与静态自查为最终检查点。
- 显式声明测试任务（`runTests=true`）且编译通过 → 进入测试执行阶段。
- 显式声明测试任务但编译失败 → 返回编译错误报告，测试任务置为 `BLOCKED`，不执行任何测试用例。

## R007 运行模式与报告

- 编译校验输入支持 `runCompile` 与 `runTests` 开关，未显式指定时均默认为 `false`。
- 编译报告须包含 `mode` 字段，默认值为 `COMPILE_ONLY`，显式声明测试时为 `COMPILE_AND_TEST`。
- `COMPILE_ONLY` 模式下，测试执行阶段输出 `SKIPPED`，不生成测试用例也不占用测试时间。
- `COMPILE_ONLY` 模式下，命令须避免触发测试编译、打包、覆盖率、集成检查或生产构建。

---

## 附录：流程图

```
代码生成完成
    │
    ▼
┌─────────────┐
│ 文件逻辑完成  │
│  静态自查    │
└──────┬──────┘
       │
       ├─ 默认 ──→ 结束，不编译
       │
       └─ 显式 runCompile=true/runTests=true
              │
              └─ 最小 compile-only 编译校验
```
