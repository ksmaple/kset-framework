# 编译校验规范

> kaka-coder-designer · `{proj}-coder` / `{proj}-fixer` 生成或修复代码后执行

---

## R001 编译校验目的

- 编译校验默认独立执行，确保生成的代码无语法错误、依赖完整、类型匹配。
- 仅当显式声明 `runTests=true` 时，编译校验才作为测试执行的前置条件。
- 编译不通过时，返回编译错误报告；未显式声明的测试任务不会触发。

---

## R002 编译校验范围

### 2.1 后端编译
- **构建工具**：Maven 或 Gradle
- **校验命令**：
  - Maven：`mvn compile`
  - Gradle：`./gradlew compileJava`

### 2.2 前端编译
- **语言**：TypeScript
- **校验命令**：
  - Vue 项目：`vue-tsc --noEmit`
  - 普通 TS 项目：`tsc --noEmit`

---

## R003 编译校验步骤

1. **自动触发**：代码生成完成后，自动触发编译校验流程。
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
  "command": "mvn compile",
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

- 默认模式下，编译校验是代码落地的最终检查点，完成后不进入测试执行阶段。
- 显式声明测试任务（`runTests=true`）且编译通过 → 进入测试执行阶段。
- 显式声明测试任务但编译失败 → 返回编译错误报告，测试任务置为 `BLOCKED`，不执行任何测试用例。

## R007 运行模式与报告

- 编译校验输入支持 `runTests` 开关，未显式指定时默认为 `false`。
- 编译报告须包含 `mode` 字段，默认值为 `COMPILE_ONLY`，显式声明测试时为 `COMPILE_AND_TEST`。
- `COMPILE_ONLY` 模式下，测试执行阶段输出 `SKIPPED`，不生成测试用例也不占用测试时间。

---

## 附录：流程图

```
代码生成完成
    │
    ▼
┌─────────────┐
│  编译校验    │
│ (默认独立)  │
└──────┬──────┘
       │
       ├─ 失败 ──→ 返回编译错误报告
       │
       └─ 通过 ──→ 默认结束
              │
              └─ 显式 runTests=true ──→ 进入测试执行阶段
```
