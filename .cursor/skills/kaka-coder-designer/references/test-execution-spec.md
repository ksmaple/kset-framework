# 测试执行规范（可调用性验证）

> kaka-coder-designer · 编译通过后由 `{proj}-coder` / `{proj}-fixer` 按需执行；任务编排见 [codegen-task-spec.md](codegen-task-spec.md)。

---

## 1. 测试执行时机

- **测试执行不是默认流程，必须在编排中显式声明（`runTests=true` 或独立 `TASK-TEST-EXECUTE`）才会触发。**
- 显式声明后，测试在编译校验通过后执行，作为代码落地的最后一步（POST-COMPILE）；只有编译通过且测试通过的代码才视为可交付。
- 若编译失败，已声明的测试任务置为 `BLOCKED`，直接返回编译错误信息。
- 未声明测试任务时，测试执行阶段状态为 `SKIPPED`。

---

## 2. 测试类型

### 2.1 API 可调用性测试

- 每个 Controller 接口生成一个简单调用测试。
- 只验证：**可调用、不报错、返回格式正确**。
- 不验证业务逻辑正确性、不验证数据准确性。

### 2.2 流程可调用性测试

- 针对核心业务场景，编排多步骤调用。
- 验证流程可以完整走完，各步骤之间衔接正常。
- 同样只验证可调用性，不验证业务结果正确性。

---

## 3. 核心规范（R001-R005）

### R001: 基于生成的 Controller 和 API 设计文档生成测试

- 测试代码必须依据代码生成阶段产出的 Controller 类和 API 设计文档自动生成。
- 测试类作为 `TASK-CONTROLLER` 的附属输出，与 Controller 代码同步生成。

### R002: 禁止使用 Mock，必须真实可调用

- 测试必须启动真实的 Spring Boot 应用上下文。
- 使用 `TestRestTemplate` 或真实 HTTP 客户端发送请求。
- 禁止 Mockito、@MockBean 等任何形式的 Mock。

### R003: 禁止使用断言（assert），使用日志输出代替

- 禁止使用 JUnit 的 `assertEquals`、`assertTrue` 等断言方法。
- 使用日志输出记录请求、响应、状态码和判定结果。
- 测试方法本身不抛异常即视为可调用性通过。

### R004: 只验证接口可调用性，不验证业务逻辑正确性

- 不校验返回数据的业务含义是否正确。
- 不校验数据库数据是否按预期变更。
- 仅验证：HTTP 请求能发出去、接口能响应、返回格式符合 `ApiResult` 约定。

### R005: 使用最小必要参数调用

- 构造请求参数时，仅填充接口要求的必填字段。
- 选填字段、复杂业务字段尽量简化，降低测试准备成本。
- 若必填字段依赖前置数据（如 ID），通过流程测试前置步骤提供。

### R006: 默认跳过与显式触发

- 未显式声明测试任务时，不生成也不执行测试用例，测试报告状态为 `SKIPPED`。
- 显式声明的测试任务执行完成后，输出 `PASSED` / `FAILED` / `PARTIAL`。
- `SKIPPED` 状态的代码不可视为最终可交付，进入 CI / 合并前须补跑测试。

---

## 4. 测试类模板（Java + Spring Boot）

### 4.1 API 测试模板

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class {Domain}ApiTest {
    @Resource
    private TestRestTemplate restTemplate;

    @Test
    public void test{Action}{Entity}() {
        // 准备最小请求参数
        {Command} command = new {Command}();
        // 设置必要字段...

        // 发送请求
        ResponseEntity<ApiResult> response = restTemplate.postForEntity(
            "/{module}/{action}", command, ApiResult.class);

        // 日志输出
        log.info("[Test] URL: /{module}/{action}");
        log.info("[Test] Request: {}", JSON.toJSONString(command));
        log.info("[Test] Response: {}", JSON.toJSONString(response.getBody()));
        log.info("[Test] Status: {}", response.getStatusCodeValue());
        log.info("[Test] Result: {}", response.getStatusCode().is2xxSuccessful() ? "PASS" : "FAIL");
    }
}
```

### 4.2 模板变量说明

| 变量 | 说明 | 示例 |
|---|---|---|
| `{Domain}` | 领域名称 | `Order`、`User` |
| `{Action}` | 操作动作 | `Create`、`Query`、`Cancel` |
| `{Entity}` | 实体名称 | `Order`、`UserProfile` |
| `{Command}` | 请求参数类型 | `CreateOrderCommand`、`QueryUserCommand` |
| `{module}` | 模块路径 | `order`、`user` |
| `{action}` | 接口动作路径 | `create`、`query` |

---

## 5. 测试执行报告格式

测试执行完成后，输出如下 JSON 格式的测试报告：

```json
{
  "testExecutionId": "test-20240523-001",
  "status": "PASSED|FAILED|PARTIAL",
  "compileCheckId": "compile-20240523-001",
  "durationMs": 30000,
  "apiTests": {
    "total": 10,
    "passed": 10,
    "failed": 0,
    "details": [
      {
        "testName": "testCreateOrder",
        "url": "/order/create",
        "status": "PASS",
        "httpStatus": 200,
        "responsePreview": "{\"errCode\":0,...}"
      }
    ]
  },
  "flowTests": {
    "total": 2,
    "passed": 2,
    "failed": 0
  }
}
```

### 5.1 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `testExecutionId` | String | 测试执行唯一标识 |
| `status` | String | 整体状态：`PASSED`（全部通过）、`FAILED`（全部失败）、`PARTIAL`（部分通过）、`SKIPPED`（未显式声明测试任务） |
| `compileCheckId` | String | 关联的编译检查 ID |
| `durationMs` | Long | 测试执行耗时（毫秒） |
| `apiTests.total` | Integer | API 测试总数 |
| `apiTests.passed` | Integer | API 测试通过数 |
| `apiTests.failed` | Integer | API 测试失败数 |
| `apiTests.details` | Array | 单个 API 测试详情 |
| `flowTests.total` | Integer | 流程测试总数 |
| `flowTests.passed` | Integer | 流程测试通过数 |
| `flowTests.failed` | Integer | 流程测试失败数 |

---

## 6. 测试失败处理

### 6.1 单接口调用失败

- 记录失败日志，包含：接口 URL、请求参数、响应状态码、异常信息。
- **不影响其他接口测试的继续执行。**
- 在测试报告中标记为 `FAIL`，并附上 `responsePreview` 或错误摘要。

### 6.2 全部接口失败

- 触发全局故障排查：
  1. 检查服务是否正常启动（端口监听、Spring Boot 上下文加载）。
  2. 检查数据库连接是否正常（连接池、网络、认证）。
  3. 检查必要的中间件（Redis、MQ 等）是否可达。
  4. 检查生成的代码是否存在基础配置错误（如 `application.yml` 缺失关键配置）。

### 6.3 流程测试失败

- 检查前置步骤是否成功执行（流程测试的每一步都依赖前一步的结果）。
- 检查测试数据是否清理（避免脏数据影响后续流程）。
- 输出流程执行步骤日志，标注失败节点。

---

## 7. 与代码生成的关系

### 7.1 测试代码自动生成

- 测试代码由代码生成阶段自动产出，作为 `TASK-CONTROLLER` 的附属输出。
- 生成测试时，读取同阶段产出的 Controller 类和 API 设计文档，自动填充模板变量。

### 7.2 测试代码存放位置

- 测试类放在 `src/test/java/{basePackage}/interfaces/` 目录下。
- 保持与主代码的包结构对应，便于维护。

### 7.3 测试类命名规范

- 测试类命名格式：`{Domain}ApiTest.java`
- 示例：`OrderApiTest.java`、`UserApiTest.java`、`PaymentApiTest.java`

### 7.4 生成时序

```
[TASK-CONTROLLER] 生成 Controller 代码
        ↓
[TASK-CONTROLLER] 同步生成 {Domain}ApiTest.java
        ↓
[COMPILE] 编译校验
        ↓
[TEST] 执行测试（仅在显式声明时触发）
        ↓
[REPORT] 输出测试执行报告（或 SKIPPED 报告）
```

---

## 8. 附录：与测试设计规范的关系

| 维度 | kaka-coder-designer / 项目测试约定 | 本文档（可调用性验证） |
|---|---|---|
| 阶段 | 设计阶段 | 代码生成后（POST-COMPILE） |
| 目标 | 验证业务逻辑正确性 | 验证接口真实可调用 |
| 是否用 Mock | 视场景允许 | **禁止** |
| 是否用断言 | 允许 | **禁止** |
| 参数策略 | 覆盖边界值、异常值 | **最小必要参数** |
| 输出物 | 测试用例文档 | 自动执行的测试类 + 执行报告 |

> 本文档聚焦「代码生成后可调用性验证」，与平台测试设计技能解耦，确保生成的代码真实可运行。
