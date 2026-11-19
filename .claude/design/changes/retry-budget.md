# 重试预算变更记录

- feature-key：`retry-budget`
- 当前 change-id：`retry-budget-20261103-01`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/java/com/kset/common/utils/retry/RetryBudget.java#recordPrimary/tryRetry`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| retry-budget-20261103-01 | active | `RetryBudget#recordPrimary/tryRetry` | 窗口起点切换与计数清零分步执行 | `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` | 可用 | 不适用 |

## retry-budget-20261103-01

- 日期：2026-11-03
- 目标：窗口切换时不丢失新窗口的首次调用或重试计数。
- 判定时间：2026-11-03 18:05:32 +08:00
- 上线状态：不涉及兼容（修正计数竞态，公开方法签名不变）
- 判定方式：自动判定
- 判定证据：仅替换预算内部计数方式。
- 证据来源：`kset-common/src/main/java/com/kset/common/utils/retry/RetryBudget.java`
- 当时代码状态：`Retryer` 调用 `recordPrimary/tryRetry`；原实现先 CAS 窗口起点再清零计数，其他线程可能在两步之间写入；现实现状态为 active。
- 兼容选择：N/A
- 回滚目标：N/A（本轮缺陷修复）
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 结果 Git ref：N/A（未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-common/src/main/java/com/kset/common/utils/retry/RetryBudget.java` | `recordPrimary/tryRetry/currentWindow` | 原子切换窗口对象并在本代计数 | modify | active |
| `kset-common/src/test/java/com/kset/common/utils/retry/RetryerTest.java` | 预算窗口回归用例 | 核对窗口重置与并发额度 | modify | active |

### 回滚步骤

1. 从基线 Git ref 恢复预算计数实现，并按实际结果更新实现状态。
