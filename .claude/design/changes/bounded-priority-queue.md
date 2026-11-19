# 有界优先队列变更记录

- feature-key：`bounded-priority-queue`
- 当前 change-id：`bounded-priority-queue-20261103-01`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/java/com/kset/common/utils/thread/BoundedPriorityBlockingQueue.java#put/offer`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| bounded-priority-queue-20261103-01 | active | `BoundedPriorityBlockingQueue#put/offer` | 批量移除后未通知等待线程 | `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` | 可用 | 不适用 |

## bounded-priority-queue-20261103-01

- 日期：2026-11-03
- 目标：所有释放容量的队列操作均唤醒等待入队的线程。
- 判定时间：2026-11-03 18:05:32 +08:00
- 上线状态：不涉及兼容（恢复阻塞队列容量通知行为，公开签名不变）
- 判定方式：自动判定
- 判定证据：仅调整已有队列的容量通知，队列对外方法不变。
- 证据来源：`kset-common/src/main/java/com/kset/common/utils/thread/BoundedPriorityBlockingQueue.java`
- 当时代码状态：`put/offer(timeout)` 等待 `notFull`，仅 `poll/take/remove` 发送通知；`drainTo/clear` 等继承操作遗漏；生产调用方为 `KsetThreadPoolExecutor`，外部调用方未知；现实现状态为 active。
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
| `kset-common/src/main/java/com/kset/common/utils/thread/BoundedPriorityBlockingQueue.java` | `clear/drainTo/removeIf/removeAll/retainAll/iterator` | 容量释放后通知等待线程 | modify | active |
| `kset-common/src/test/java/com/kset/common/utils/thread/BoundedPriorityBlockingQueueTest.java` | 批量释放回归用例 | 核对阻塞线程获唤醒 | modify | active |

### 回滚步骤

1. 从基线 Git ref 恢复队列实现，并按实际结果更新实现状态。
