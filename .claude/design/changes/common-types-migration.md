# 公共类型迁移变更记录

- feature-key：`common-types-migration`
- 当前 change-id：`common-types-framework-20261119`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/java/com/kset/common/page/PageResult.java#PageResult`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| common-types-framework-20261119 | active | `kset-common/src/main/java/com/kset/common/page/PageResult.java#PageResult` | 业务仓旧公共类型与涂鸦源码 | `48a168b11cedd1bed80f94e6c79660b40bf66f5c` | 可用 | N/A |

## common-types-framework-20261119

- 日期：2026-11-19
- 目标：框架接收公共分页结果、常量及可选涂鸦客户端。
- 判定时间：2026-11-19 Asia/Shanghai
- 上线状态：待确认
- 判定方式：用户确认迁移；未核实生产发布与仓外依赖。
- 判定证据：本轮用户指令及本仓源码、模块配置。
- 证据来源：本轮对话、`pom.xml`、`kset-boot-parent/pom.xml`
- 当时代码状态：框架新增 `com.kset.common.page.PageResult` 与 `com.kset.common.constant`，`kset-tuya` 为可选模块；业务仓调用方迁移状态须随其发布核验。
- 兼容选择：直接替换旧分页类型，不保留旧类型。
- 回滚目标：N/A
- 旧版本正式线上主分支及对比基线 Git SHA：`main`；已发布提交待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`48a168b11cedd1bed80f94e6c79660b40bf66f5c`
- 结果 Git ref：N/A
- 回滚来源：`48a168b11cedd1bed80f94e6c79660b40bf66f5c`
- 回滚可用性：可用，须同步恢复业务仓的旧模块与契约。

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-common/src/main/java/com/kset/common/page/PageResult.java` | `PageResult` | 公共分页结果 | add | active |
| `kset-common/src/main/java/com/kset/common/constant/` | `CacheConstants`、`GlobalConstants`、`MQConstants` | 公共常量 | add | active |
| `kset-tuya/` | `TuyaApiClient`、`TuyaDoorLockClient` 等 | 可选涂鸦适配 | add | active |
| `pom.xml` | `modules` | 登记涂鸦模块 | modify | active |
| `kset-boot-parent/pom.xml` | `dependencyManagement` | 管理涂鸦构件版本 | modify | active |

### 回滚步骤

1. 从本仓基线 Git ref 移除本轮新增的公共类型、常量及可选涂鸦模块登记。
2. 同步恢复业务仓旧模块、依赖和用户管理 RPC 契约，核对实际发布版本。
