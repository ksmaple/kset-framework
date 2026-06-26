# 测试执行规范（Controller 优先单点调用验证）

> kaka-coder-designer · 编译通过后由 `{proj}-coder` / `{proj}-fixer` 按需执行；任务编排见 [codegen-task-spec.md](codegen-task-spec.md)。

---

## 1. 测试执行时机

R001: 代码开发完成后默认只做编译校验，未收到用户、任务或 CI 显式测试要求时禁止生成、补充或执行测试用例。
R002: 显式测试要求仅包括用户明确要求测试、编排参数 `runTests=true`、独立 `TASK-TEST-EXECUTE` 或 CI 明确要求测试。
R003: 显式测试任务须在编译校验通过后执行；编译失败时测试任务置为 `BLOCKED`，禁止执行测试用例。
R004: 未显式声明测试任务时，测试执行阶段状态为 `SKIPPED`，报告须说明“未显式声明测试任务”。

---

## 2. 测试范围

R005: 显式测试默认只针对单个 Controller 接口做单点调用验证。
R006: 仅当无可调用 Controller 或用户明确点名 Service 时，才允许单个 Service 方法测试。
R007: 每个测试方法只调用一个目标方法，禁止在同一个 `@Test` 中串联多个业务动作。
R008: 禁止编排多接口、多服务、多步骤串联的整体流程测试。
R009: 禁止为测试额外串接口、查库准备数据、构造多阶段流程或清理流程数据。
R010: 测试目标是确认真实代码可调用、入参可构造、日志结果可见，禁止扩展为业务正确性验收。

---

## 3. 真实环境约束

R011: Java Spring 测试须启动真实 Spring Boot 上下文，使用项目实际启动类配置 `@SpringBootTest(classes = {...})`。
R012: JUnit 4 项目优先使用 `@RunWith(SpringRunner.class)`；项目已统一 JUnit 5 时使用等价的 Spring Extension。
R013: 测试须注入真实 Controller 或兜底 Service Bean 后直接调用方法，禁止使用 Mockito、`@MockBean`、stub、fake 或内存替身。
R014: 禁止用 `TestRestTemplate`、MockMvc 或随机端口 HTTP 调用替代同进程真实 Bean 方法调用，除非项目现有测试规范明确只允许 HTTP 入口。
R015: 测试依赖数据库、Redis、MQ 等中间件时须使用项目真实测试环境配置，禁止为了跑通测试临时 mock 外部依赖。

---

## 4. 入参与输出

R016: 测试方法须在调用前使用日志框架输出目标类、目标方法和完整输入入参。
R017: 测试方法须在调用后使用日志框架输出完整返回结果；返回对象优先使用项目已有 JSON 工具序列化。
R018: 测试方法设置登录态、租户、SessionUID 或 ThreadLocal 上下文时须使用日志框架输出上下文入参。
R019: 测试入参只填本次目标方法调用所需的最小必要字段，禁止为覆盖边界值或异常分支扩大数据准备范围。
R020: 测试禁止使用 JUnit Assert、Hamcrest、AssertJ 或 Spring ResultMatcher 等断言类 API。
R021: 测试结果只能通过日志框架输出，禁止使用断言、返回值判定或测试框架 matcher 表达业务结果。

---

## 5. 测试代码模板（Java + Spring Boot）

### 5.1 Controller 单点调用模板

```java
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PlatformWealthServer.class})
@Slf4j
public class CashControllerTest {

    @Resource
    private CashController cashController;

    @Test
    public void getWithdrawTiers() {
        Long uid = 2000029L;
        SessionUID.setUid(uid);

        Object request = null;
        log.info("target=CashController#getWithdrawTiers");
        log.info("context.uid={}", uid);
        log.info("request={}", JSON.toJSONString(request));
        log.info("response={}", JSON.toJSONString(cashController.getWithdrawTiers(request)));
    }
}
```

### 5.2 Service 兜底单点调用模板

> 仅当无可调用 Controller 或用户明确点名 Service 时使用。

```java
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PlatformWealthServer.class})
@Slf4j
public class CashServiceTest {

    @Resource
    private CashService cashService;

    @Test
    public void findWithdrawTiers() {
        Long uid = 2000029L;
        WithdrawTierQuery query = new WithdrawTierQuery();
        query.setUid(uid);

        log.info("target=CashService#findWithdrawTiers");
        log.info("request={}", JSON.toJSONString(query));
        log.info("response={}", JSON.toJSONString(cashService.findWithdrawTiers(query)));
    }
}
```

### 5.3 模板变量说明

| 变量 | 说明 | 示例 |
|---|---|---|
| `{SpringBootApplication}` | 项目真实启动类 | `PlatformWealthServer` |
| `{TargetClass}` | 被测 Controller 或兜底 Service | `CashController` |
| `{targetMethod}` | 被测单个接口或方法 | `getWithdrawTiers` |
| `{context}` | 登录态、租户、SessionUID 等上下文 | `SessionUID.setUid(2000029L)` |
| `{request}` | 单点调用入参 | `null`、`WithdrawTierQuery` |

---

## 6. 测试执行摘要

显式测试执行完成后，仅输出轻量摘要：

```json
{
  "testExecutionId": "test-20240523-001",
  "status": "DONE|SKIPPED|BLOCKED",
  "target": "CashController#getWithdrawTiers",
  "context": "{\"uid\":2000029}",
  "input": "{\"request\":null}",
  "output": "{\"errCode\":0,...}",
  "error": null
}
```

### 6.1 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `testExecutionId` | String | 测试执行唯一标识 |
| `status` | String | 状态：`DONE`、`SKIPPED` 或 `BLOCKED` |
| `target` | String | 本次单点调用目标 |
| `context` | String | 上下文摘要 |
| `input` | String | 入参摘要 |
| `output` | String | 返回结果摘要 |
| `error` | String | 异常摘要，无异常时为 `null` |

---

## 7. 测试失败处理

R022: 单点调用失败时须记录目标类、目标方法、上下文、入参、异常信息和已打印返回内容。
R023: 单点调用失败不得自动扩大为整体流程测试，须先确认真实测试环境、环境数据和目标方法依赖。
R024: 多个单点测试独立执行；一个单点失败不得影响其他已声明单点测试继续执行。
R025: 因真实环境不可用导致测试无法执行时，状态置为 `BLOCKED` 并输出缺失的环境项。

---

## 8. 与代码生成的关系

R026: 测试代码仅在显式测试要求下生成或补充，禁止作为 `TASK-CONTROLLER` 默认附属输出。
R027: 测试类放在项目现有 `src/test/java` 包结构下，并按目标类命名为 `{TargetClass}Test.java`。
R028: 生成测试时须优先读取目标 Controller 真实方法签名，按方法签名构造最小必要入参。
R029: 仅在无可调用 Controller 或用户明确点名 Service 时，才读取目标 Service 真实方法签名生成测试。
R030: 测试执行摘要须保留目标、上下文、入参、返回结果或异常摘要，便于用户直接查看本次真实调用情况。

### 8.1 生成时序

```
[CODE] 完成业务代码
        ↓
[COMPILE] 编译校验
        ↓
[TEST?] 未显式声明 → SKIPPED
        ↓
[TEST] 显式声明 → 生成或补充 Controller 优先单点测试 → 真实 Spring 环境执行
        ↓
[REPORT] 输出目标、上下文、入参、日志结果或异常摘要
```
