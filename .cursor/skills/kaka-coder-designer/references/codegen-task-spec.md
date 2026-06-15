# 代码落地任务编排

> 技能：kaka-coder-designer · `{proj}-coder` 写码阶段按需查阅
>
> 本文档定义代码落地阶段任务的拆分粒度、任务类型、依赖关系（DAG）以及服务牌匾（H5 调用）规范。
>
> **使用方式**：按当前需要生成的代码层级查阅对应任务类型，不需要全量阅读本文档。
> **API 约定**：接口层须符合 `kaka-coder-designer/references/api-spec.md`（Full POST + `ApiResult`）；Swagger/OpenAPI 为可选，非默认必做。

---

## 1. 任务拆分规则

### 1.1 按层级拆分独立任务

代码落地按经典分层架构划分为若干层级，每一层内部的任务在满足依赖的前提下可并行执行：

| 层级 | 说明 | 并行度 |
|---|---|---|
| L1 — 领域层（Domain） | 实体、值对象、领域事件、领域服务 | 同层内多个聚合之间可并行 |
| L2 — 基础设施层（Infra） | 仓库实现、PO、Mapper、持久化细节 | 依赖 L1，同层内多个仓库可并行 |
| L3 — 应用层（Application） | 应用服务、Command、DTO、Validator | 依赖 L1 + L2，同层内多个用例可并行 |
| L4 — 接口层（Interface） | Controller、适配器、过滤器 | 依赖 L3，同层内多个接口可并行 |
| L5 — 前端层（Frontend） | 类型、服务、Store、视图 | 依赖 L4 的 DTO 契约，同层内可并行 |
| L6 — 编译校验层（Compile） | 对所有生成的代码执行编译/类型检查 | 所有代码生成完成后串行执行 |
| L7 — 测试执行层（Test） | 运行 API 可调用性测试和流程测试 | **默认不执行**；显式声明 `runTests=true` 后串行执行；细则见 [test-execution-spec.md](test-execution-spec.md) |

### 1.2 按领域拆分独立任务

不同限界上下文（Bounded Context）之间的任务天然解耦，可并行执行：

- **原则**：一个限界上下文内部的代码落地任务，不应阻塞另一个上下文的任务。
- **例外**：若存在跨上下文的领域事件或共享内核（Shared Kernel），需在整合任务中显式声明依赖。

### 1.3 明确任务间的依赖关系（DAG）

所有任务必须构成**有向无环图（DAG）**，禁止循环依赖：

- 每个任务在创建时必须声明 `dependencies` 列表（前置任务 ID 集合）。
- 调度器仅在前置任务全部达到 `COMPLETED` 状态后，才将当前任务置为 `READY`。
- 若前置任务任一失败，当前任务自动置为 `BLOCKED`，由整合任务统一处理。

---

## 2. 任务类型定义

以下模板为代码落地阶段的标准任务类型，每种类型对应一个明确的代码生成目标。

### 2.1 后端任务类型

#### `TASK-DDD-ENTITY`

| 属性 | 说明 |
|---|---|
| **输入** | DDD 设计文档（聚合根/实体/值对象定义） |
| **输出** | 领域实体 Java/Kotlin 源码文件 |
| **依赖** | `TASK-DDD-DESIGN`（DDD 设计任务） |
| **校验点** | 字段与 DDD 文档一致；值对象不可变；聚合根有唯一标识 |

#### `TASK-REPOSITORY`

| 属性 | 说明 |
|---|---|
| **输入** | 领域实体定义 |
| **输出** | 仓库接口（Domain）、仓库实现（Infra）、持久化对象（PO）、MyBatis/JPA Mapper |
| **依赖** | `TASK-DDD-ENTITY` |
| **校验点** | 接口与实现类名一致；PO 字段与实体映射完整；Mapper XML/Annotation 无语法错误 |

#### `TASK-CONVERTER`

| 属性 | 说明 |
|---|---|
| **输入** | 领域实体、PO、DTO 定义 |
| **输出** | `EntityConverter`（Entity ↔ PO）、`DtoConverter`（Entity ↔ DTO） |
| **依赖** | `TASK-DDD-ENTITY` + `TASK-REPOSITORY`（需 PO 定义） |
| **校验点** | 转换方法覆盖所有字段；无空指针风险；双向转换可逆 |

#### `TASK-APPLICATION`

| 属性 | 说明 |
|---|---|
| **输入** | 用例描述、领域实体、仓库接口、DTO 契约 |
| **输出** | `ApplicationService`、`Command` 类、`DTO`、`Validator` |
| **依赖** | `TASK-DDD-ENTITY` + `TASK-REPOSITORY` + `TASK-CONVERTER` |
| **校验点** | 命令与用例一一对应；Validator 覆盖必填/格式/业务规则；事务边界正确 |

#### `TASK-CONTROLLER`

| 属性 | 说明 |
|---|---|
| **输入** | ApplicationService、DTO、URL 路由规范 |
| **输出** | `Controller` 类、统一 `ApiResult` 响应包装（Swagger 可选） |
| **依赖** | `TASK-APPLICATION` |
| **校验点** | URL 与 API 设计文档一致（Full POST）；入参 body 绑定；HTTP 200 + errCode |

### 2.2 前端任务类型

#### `TASK-FRONTEND-TYPES`

| 属性 | 说明 |
|---|---|
| **输入** | 后端 DTO 定义、API 设计文档 |
| **输出** | TypeScript `interface` / `type` 文件 |
| **依赖** | `TASK-APPLICATION`（需 DTO 契约稳定） |
| **校验点** | 字段名/类型与后端 DTO 一致；可选字段标记正确 |

#### `TASK-FRONTEND-SERVICES`

| 属性 | 说明 |
|---|---|
| **输入** | 前端类型、API 路由规范 |
| **输出** | API 封装模块（如 `api/xxxService.ts`，含 axios/fetch 调用） |
| **依赖** | `TASK-FRONTEND-TYPES` |
| **校验点** | URL/Method 与 Controller 一致；入参/返回值类型引用正确；错误处理完备 |

#### `TASK-FRONTEND-STORE`

| 属性 | 说明 |
|---|---|
| **输入** | 前端服务、页面状态需求 |
| **输出** | Pinia Store 模块（State + Getters + Actions） |
| **依赖** | `TASK-FRONTEND-SERVICES` |
| **校验点** | State 与页面需求覆盖；Actions 调用 Service；响应式绑定正确 |

#### `TASK-FRONTEND-VIEWS`

| 属性 | 说明 |
|---|---|
| **输入** | 前端 Store、页面原型/需求描述 |
| **输出** | Vue/React 页面组件（`.vue` / `.tsx`） |
| **依赖** | `TASK-FRONTEND-STORE` + `TASK-FRONTEND-TYPES` |
| **校验点** | 组件引用 Store；表单绑定类型正确；路由配置完整 |

### 2.3 编译与测试任务类型

#### `TASK-COMPILE-CHECK`

| 属性 | 说明 |
|---|---|
| **输入** | 所有已生成的代码文件 |
| **输出** | 编译报告（compile-check-report.json） |
| **依赖** | 所有代码生成任务（TASK-DDD-ENTITY, TASK-REPOSITORY, TASK-CONVERTER, TASK-APPLICATION, TASK-CONTROLLER, TASK-FRONTEND-*） |
| **校验点** | 无语法错误、依赖完整、类型匹配、包依赖方向正确 |

#### `TASK-TEST-EXECUTE`

| 属性 | 说明 |
|---|---|
| **输入** | 编译通过的代码 + 生成的测试类；须同时收到 `runTests=true` 才会真正执行 |
| **输出** | 测试执行报告（test-execution-report.json）；未触发时状态为 `SKIPPED` |
| **依赖** | `TASK-COMPILE-CHECK`（状态必须为 PASSED）；仅在显式声明时调度 |
| **校验点** | API 可调用、返回格式正确、流程可走完 |

---

## 3. 依赖关系图

### 3.1 文本形式 DAG

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              后端任务链                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   DDD设计 ──→ 实体 ──→ 仓库 ──→ 转换器 ──→ 应用服务 ──→ 控制器            │
│  (DESIGN)   (ENTITY)  (REPO)   (CONVERTER)  (APPLICATION)  (CONTROLLER)     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ DTO 契约
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端任务链                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   前端类型 ──→ 前端服务 ──→ 前端Store ──→ 前端视图                         │
│  (TYPES)     (SERVICES)    (STORE)       (VIEWS)                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ 所有代码
                                      ▼
                              ┌───────────────┐
                              │   编译校验     │
                              │ (COMPILE-CHECK)│
                              └───────┬───────┘
                                      │
                       runTests=true  │
                       （显式声明）    ▼
                              ┌───────────────┐
                              │   测试执行     │
                              │ (TEST-EXECUTE) │
                              └───────────────┘
```

### 3.2 并行执行说明

- **后端并行**：同一层级内，不同聚合/模块的任务可并行。例如：订单实体的 `TASK-DDD-ENTITY` 与 用户实体的 `TASK-DDD-ENTITY` 可同时执行。
- **前后端并行**：前端 `TASK-FRONTEND-TYPES` 需等待后端 `TASK-APPLICATION` 完成，但后端 `TASK-CONTROLLER` 与前端 `TASK-FRONTEND-TYPES` 可部分并行（若 DTO 契约已提前约定）。
- **前端并行**：`TASK-FRONTEND-TYPES`、`TASK-FRONTEND-SERVICES`、`TASK-FRONTEND-STORE`、`TASK-FRONTEND-VIEWS` 在严格依赖链下顺序执行，但不同页面模块间可并行。
- **编译校验串行**：`TASK-COMPILE-CHECK` 必须在所有代码生成任务（含前后端）完成后执行，作为统一前置步骤，不可与代码生成任务并行。
- **测试执行可选**：`TASK-TEST-EXECUTE` 默认不调度；仅当编排参数 `runTests=true` 时，才在 `TASK-COMPILE-CHECK` 状态为 `PASSED` 后串行执行。

---

## 4. 整合任务输出格式

整合任务完成后，输出标准 JSON 任务列表：

```json
{
  "orchestrationId": "orch-20240523-001",
  "status": "COMPLETED",
  "runTests": false,
  "createdAt": "2024-05-23T10:00:00Z",
  "completedAt": "2024-05-23T10:05:30Z",
  "tasks": [
    {
      "taskId": "task-entity-001",
      "type": "TASK-DDD-ENTITY",
      "input": {
        "designDocPath": "/docs/ddd/order-aggregate.md"
      },
      "output": {
        "files": [
          "domain/order/Order.java",
          "domain/order/OrderId.java",
          "domain/order/OrderItem.java"
        ]
      },
      "dependencies": ["task-design-001"],
      "status": "COMPLETED",
      "validationReport": {
        "passed": true,
        "checks": [
          { "rule": "字段一致性", "result": "PASS" },
          { "rule": "值对象不可变", "result": "PASS" }
        ]
      }
    },
    {
      "taskId": "task-repo-001",
      "type": "TASK-REPOSITORY",
      "input": {
        "entityFiles": ["domain/order/Order.java"]
      },
      "output": {
        "files": [
          "domain/order/OrderRepository.java",
          "infra/order/OrderRepositoryImpl.java",
          "infra/order/OrderPO.java",
          "infra/order/OrderMapper.java"
        ]
      },
      "dependencies": ["task-entity-001"],
      "status": "COMPLETED",
      "validationReport": {
        "passed": true,
        "checks": [
          { "rule": "接口与实现一致", "result": "PASS" },
          { "rule": "PO 映射完整", "result": "PASS" }
        ]
      }
    }
  ],
  "compileCheck": {
    "compileCheckId": "compile-20240523-001",
    "status": "PASSED",
    "mode": "COMPILE_ONLY",
    "errors": [],
    "warnings": []
  },
  "testExecution": {
    "testExecutionId": null,
    "status": "SKIPPED",
    "reason": "未显式声明测试任务"
  }
}
```

### 4.1 状态枚举

| 状态 | 说明 |
|---|---|
| `PENDING` | 任务已创建，等待调度 |
| `READY` | 前置依赖已满足，可执行 |
| `RUNNING` | 任务执行中 |
| `COMPLETED` | 任务成功完成，输出已生成 |
| `FAILED` | 任务执行失败，需人工介入或重试 |
| `BLOCKED` | 前置任务失败，当前任务被阻塞 |
| `ROLLED_BACK` | 因一致性校验失败，已回滚 |
| `PASSED` | 编译校验或测试执行通过 |
| `PARTIAL` | 测试执行部分通过（部分失败但不阻塞） |
| `SKIPPED` | 测试任务未显式声明，未执行 |

---

## 5. 校验与串联规则

### 5.1 子任务校验报告

每个子任务完成后**必须**输出校验报告（`validationReport`），包含：

| 检查项 | 说明 |
|---|---|
| **字段一致性** | 生成的代码字段与输入设计文档是否一致 |
| **命名规范** | 类名、方法名、变量名是否符合项目命名约定 |
| **依赖可达** | 引用的类/接口是否存在于已完成的依赖任务输出中 |
| **语法正确性** | 生成的源码是否能通过编译（或静态检查） |
| **契约对齐** | DTO / API 定义前后端是否一致 |

校验报告格式：

```json
{
  "passed": true,
  "score": 95,
  "checks": [
    { "rule": "字段一致性", "result": "PASS", "detail": "12/12 字段匹配" },
    { "rule": "命名规范", "result": "WARN", "detail": "方法名 getOrderDetail 建议改为 findOrderDetail" },
    { "rule": "依赖可达", "result": "PASS", "detail": "所有引用已解析" }
  ],
  "timestamp": "2024-05-23T10:02:15Z"
}
```

### 5.2 整合任务一致性校验

整合任务负责收集所有子任务的校验报告，执行**最终一致性校验**：

| 校验维度 | 说明 |
|---|---|
| **跨层引用一致性** | Controller 引用的 ApplicationService 是否存在；Store 引用的前端 Service 是否存在 |
| **前后端契约一致性** | 后端 DTO 字段名/类型与前端 TypeScript 类型是否一一对应 |
| **DateTime wire 一致性** | API JSON、DTO、TS types、project-spec「时间格式」是否一致（默认 `yyyy-MM-dd HH:mm:ss`，api A027）；禁止 string/epoch/ISO 混用 |
| **数据库映射一致性** | PO 字段与实体字段、Mapper SQL 列名是否一致 |
| **任务输出完整性** | 所有声明的 `output.files` 是否实际生成且无缺失 |

### 5.3 不一致处理策略

当整合任务发现不一致时，按以下策略处理：

| 场景 | 处理策略 |
|---|---|
| **单个子任务校验失败** | 标记该任务为 `FAILED`，触发重试（最多 3 次）。重试仍失败则标记 `BLOCKED`，等待人工确认。 |
| **跨任务一致性校验失败** | 回滚（`ROLLED_BACK`）相关下游任务，重新执行依赖链。例如：后端 DTO 修改后，前端 TYPES / SERVICES 需重新生成。 |
| **前后端契约不一致** | 优先以后端为准（Source of Truth），自动重新生成前端类型；若涉及破坏性变更，标记 `BLOCKED` 待人工确认。 |
| **DateTime 格式/时区不一致** | 以 project-spec「时间格式」为准；init 阶段须先补齐 spec，禁止 coder 擅自改 wire 格式 |
| **无法自动修复** | 生成详细差异报告，H5 端展示对比视图，由人工选择"强制通过"或"回滚重试"。 |

### 5.4 回滚机制

- **回滚粒度**：以任务为最小回滚单元，支持单任务回滚或整条依赖链回滚。
- **回滚动作**：删除已生成的文件、清理已注册的接口、重置任务状态为 `PENDING`。
- **回滚触发条件**：
  1. 整合任务一致性校验失败且自动修复超时。
  2. H5 端用户手动发起回滚请求。
  3. 上游任务重试后仍失败，下游任务必须回滚。

---

## 附录：快速参考

### 任务类型速查表

| 任务类型 | 层级 | 主要依赖 | 输出物 |
|---|---|---|---|
| `TASK-DDD-ENTITY` | L1 领域层 | DDD 设计文档 | 实体、值对象、领域事件 |
| `TASK-REPOSITORY` | L2 基础设施层 | `TASK-DDD-ENTITY` | 仓库接口、实现、PO、Mapper |
| `TASK-CONVERTER` | L2 基础设施层 | `TASK-DDD-ENTITY` + `TASK-REPOSITORY` | EntityConverter、DtoConverter |
| `TASK-APPLICATION` | L3 应用层 | `TASK-DDD-ENTITY` + `TASK-REPOSITORY` + `TASK-CONVERTER` | ApplicationService、Command、DTO、Validator |
| `TASK-CONTROLLER` | L4 接口层 | `TASK-APPLICATION` | Controller、Swagger 注解 |
| `TASK-FRONTEND-TYPES` | L5 前端层 | `TASK-APPLICATION` | TypeScript 类型定义 |
| `TASK-FRONTEND-SERVICES` | L5 前端层 | `TASK-FRONTEND-TYPES` | API 封装模块 |
| `TASK-FRONTEND-STORE` | L5 前端层 | `TASK-FRONTEND-SERVICES` | Pinia Store |
| `TASK-FRONTEND-VIEWS` | L5 前端层 | `TASK-FRONTEND-STORE` + `TASK-FRONTEND-TYPES` | 页面组件 |
| `TASK-COMPILE-CHECK` | L6 编译校验层 | 所有代码生成任务 | 编译报告 |
| `TASK-TEST-EXECUTE` | L7 测试执行层 | `TASK-COMPILE-CHECK`（显式声明时） | 测试执行报告，未触发时为 `SKIPPED` |

### 状态流转图

```
PENDING ──→ READY ──→ RUNNING ──→ COMPLETED
              │           │
              │           └──────→ FAILED ──→（重试）──→ READY
              │                          └──────→ BLOCKED（人工确认）
              │
              └──────────────────────────────→ BLOCKED（前置失败）
```

### 编译校验状态流转

```
PENDING ──→ READY ──→ RUNNING ──→ PASSED ──→ 默认结束
              │           │
              │           │                    （显式 runTests=true）
              │           │                           ↓
              │           │                    进入 TEST-EXECUTE
              │           │
              │           └──────→ FAILED ──→ 自动修复重试 ──→ 仍失败 ──→ BLOCKED
              │
              └──────────────────────────────→ BLOCKED（前置失败）
```

### 测试执行状态流转

```
                            （runTests=false / 未显式声明）
PENDING ──→ READY ───────────────────────────────→ SKIPPED
              │
              │           （runTests=true）
              ▼
          RUNNING ──→ PASSED ──→ COMPLETED
              │
              └──────→ FAILED ──→ 记录日志 ──→ 继续其他测试 ──→ PARTIAL
              │
              └──────────────────────────────→ BLOCKED（前置失败）
```
