# 本地 / Dubbo 服务引用变更记录

- feature-key：`local-dubbo-service-reference`
- 当前 change-id：`local-dubbo-service-reference-20261105-05`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/java/com/kset/common/service/KsetServiceRefBeanPostProcessor.java#resolveService`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| local-dubbo-service-reference-20261105-01 | removed | `KsetServiceRefBeanPostProcessor#resolveService` | 无注册中心配置时仍触发远端冲突探测 | `a07d3f6` 及本轮修改前工作区状态 | 部分可用；新增功能尚未提交 | 已由 02 替代 |
| local-dubbo-service-reference-20261105-02 | removed | `KsetServiceRefBeanPostProcessor#resolveService` | 注解和本地注入依赖 `kset-starter-dubbo` | 本轮修改前工作区状态 | 部分可用；原功能未提交 | 已由 03 替代 |
| local-dubbo-service-reference-20261105-03 | removed | `KsetServiceRefBeanPostProcessor#resolveService` | 公开注解名 `KsetServiceRef` 使用缩写 | 本轮修改前工作区状态 | 部分可用；原功能未提交 | 已由 04 替代 |
| local-dubbo-service-reference-20261105-04 | removed | `KsetServiceRefBeanPostProcessor#resolveService` | 公开注解名含多余的 `Service` | 本轮修改前工作区状态 | 部分可用；原功能未提交 | 已由 05 补充接入规范 |
| local-dubbo-service-reference-20261105-05 | active | `KsetServiceRefBeanPostProcessor#resolveService` | 缺少外部业务默认接入规范 | 本轮修改前工作区状态 | 部分可用；原功能未提交 | N/A |

## local-dubbo-service-reference-20261105-05

- 日期：2026-11-05
- 目标：明确外部业务引用服务接口时默认使用 `@KsetReference`，补充本地和 Dubbo 接入边界。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：当前功能仅存在于未提交工作区
- 判定证据：`git status --short --branch`
- 证据来源：`kset-common/README.md`、`kset-starter-dubbo/README.md`
- 当时代码状态：公开注解已更名为 `KsetReference`；已有 README 说明行为，但尚无外部业务默认用法规范，未发现已知外部调用方。
- 兼容选择：N/A；仅补文档与项目约定
- 回滚目标：`local-dubbo-service-reference-20261105-04`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f6`；本功能当前状态位于未提交工作区
- 结果 Git ref：N/A
- 回滚来源：本轮修改前工作区状态；已提交基线 `a07d3f6` 不包含本功能
- 回滚可用性：部分可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `docs/usage/service-reference.md` | 外部业务服务引用规范 | 默认注解、接入方式、例外边界 | add | active |
| `docs/README.md` | 场景使用文档 | 增加索引 | modify | active |
| `kset-common/README.md`、`kset-starter-dubbo/README.md` | 服务引用章节 | 链接接入规范 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | 本地 / Dubbo 服务引用 | 记录默认使用约定 | modify | active |

### 回滚步骤

1. 移除本轮新增的接入规范和索引。
2. 恢复两个模块 README 与 project-spec 的本轮说明。
3. 更新本记录中的实现状态。

## local-dubbo-service-reference-20261105-04

- 日期：2026-11-05
- 目标：公开注解简化为 `KsetReference`，保持注入行为不变。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：当前功能仅存在于未提交工作区
- 判定证据：`git status --short --branch`
- 证据来源：`kset-common/src/main/java/com/kset/common/service/`
- 当时代码状态：公开注解为 `com.kset.common.service.KsetServiceReference`，仓内测试和文档使用该名称，未发现已知外部调用方。
- 兼容选择：N/A；未提交的新功能直接更名
- 回滚目标：`local-dubbo-service-reference-20261105-03`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f6`；本功能当前状态位于未提交工作区
- 结果 Git ref：N/A
- 回滚来源：本轮修改前工作区状态；已提交基线 `a07d3f6` 不包含本功能
- 回滚可用性：部分可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-common/src/main/java/com/kset/common/service/KsetReference.java` | `KsetReference` | 字段注入公开注解 | add | active |
| `kset-common/src/main/java/com/kset/common/service/KsetServiceReference.java` | `KsetServiceReference` | 原公开注解名 | remove | removed |
| `kset-common/src/main/java/com/kset/common/service/KsetServiceRefBeanPostProcessor.java` | 注解识别与错误提示 | 注解名称适配 | modify | active |
| `kset-common/src/test/java/com/kset/common/service/` | 注解使用处 | 覆盖新名称 | modify | active |
| `kset-common/README.md`、`kset-starter-dubbo/README.md` | 接入示例 | 更新导入与示例 | modify | active |

### 回滚步骤

1. 恢复旧注解类名与处理器识别。
2. 恢复测试和文档中的旧名称。
3. 更新本记录中的实现状态。

## local-dubbo-service-reference-20261105-03

- 日期：2026-11-05
- 目标：公开注解更名为 `KsetServiceReference`，保持服务选择及 Dubbo 适配行为不变。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：当前功能仅存在于未提交工作区
- 判定证据：`git status --short --branch`
- 证据来源：`kset-common/src/main/java/com/kset/common/service/`
- 当时代码状态：公开注解 `com.kset.common.service.KsetServiceRef` 用于字段注入；仓内测试和文档使用旧名称，未发现已知外部调用方。
- 兼容选择：N/A；未提交的新功能直接更名
- 回滚目标：`local-dubbo-service-reference-20261105-02`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f6`；本功能当前状态位于未提交工作区
- 结果 Git ref：N/A
- 回滚来源：本轮修改前工作区状态；已提交基线 `a07d3f6` 不包含本功能
- 回滚可用性：部分可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-common/src/main/java/com/kset/common/service/KsetServiceReference.java` | `KsetServiceReference` | 字段注入公开注解 | add | active |
| `kset-common/src/main/java/com/kset/common/service/KsetServiceRef.java` | `KsetServiceRef` | 原公开注解名 | remove | removed |
| `kset-common/src/main/java/com/kset/common/service/KsetServiceRefBeanPostProcessor.java` | 注解识别与错误提示 | 注解名称适配 | modify | active |
| `kset-common/src/test/java/com/kset/common/service/` | 注解使用处 | 覆盖新名称 | modify | active |
| `kset-common/README.md`、`kset-starter-dubbo/README.md` | 接入示例 | 更新导入与示例 | modify | active |

### 回滚步骤

1. 恢复旧注解类名与处理器识别。
2. 恢复测试和文档中的旧名称。
3. 更新本记录中的实现状态。

## local-dubbo-service-reference-20261105-02

- 日期：2026-11-05
- 目标：注解和本地注入由 `kset-common` 独立提供；仅宿主机引入 Dubbo starter 且配置注册中心时接入远端引用与冲突校验。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：当前功能仅存在于未提交工作区
- 判定证据：`git status --short --branch`
- 证据来源：`kset-starter-dubbo/src/main/java/com/kset/dubbo/`
- 当时代码状态：`com.kset.dubbo.annotation.KsetServiceRef`、注入器、冲突校验均位于 Dubbo starter；尚无已知外部调用方及迁移状态。
- 兼容选择：N/A；未提交的新功能改用 `com.kset.common.service.KsetServiceRef`
- 回滚目标：`local-dubbo-service-reference-20261105-01`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f6`；本功能当前状态位于未提交工作区
- 结果 Git ref：N/A
- 回滚来源：本轮修改前工作区状态；已提交基线 `a07d3f6` 不包含本功能
- 回滚可用性：部分可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-common/src/main/java/com/kset/common/service/` | `KsetServiceRef`, `KsetServiceRefBeanPostProcessor`, `RemoteServiceReferenceProvider` | 独立注解、选择与扩展契约 | add | active |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/reference/` | `DubboServiceReferenceProvider`, `DubboRemoteServiceInspector` | 可选 Dubbo 引用和校验 | modify | active |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/annotation/` | `KsetServiceRef` | 原注解位置 | remove | removed |
| `kset-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `KsetServiceReferenceAutoConfiguration` | 基础装配入口 | modify | active |
| `kset-common/src/test/java/com/kset/common/service/` | `KsetServiceRefBeanPostProcessorTest` | 本地和可选远端路径 | add | active |
| `kset-common/src/test/java/com/kset/common/service/` | `KsetServiceReferenceAutoConfigurationTest` | common 独立装配路径 | add | active |
| `kset-starter-dubbo/src/test/java/com/kset/dubbo/reference/` | `DubboServiceReferenceProviderTest` | Dubbo 配置门槛 | add | active |
| `kset-common/README.md`、`kset-starter-dubbo/README.md` | 本地 / 远端服务引用 | 使用说明 | modify | active |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/autoconfigure/` | 服务引用自动装配 | Dubbo 适配器装配 | modify | active |

### 回滚步骤

1. 恢复本轮修改前的注解、注入器与自动装配位置。
2. 恢复原接入文档并核对模块依赖方向。
3. 更新本记录中的实现状态。

## local-dubbo-service-reference-20261105-01

- 日期：2026-11-05
- 目标：本地服务优先；未配置 Dubbo 注册中心时本地服务正常启动；配置远端后在启动期重新选择并校验冲突。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：当前功能仅存在于未提交工作区
- 判定证据：`git status --short --branch`
- 证据来源：`kset-starter-dubbo/src/main/java/com/kset/dubbo/reference/`
- 当时代码状态：`@KsetServiceRef` 字段启动期优先注入本地；缺少本地时创建远端引用；本地引用在 Runner 阶段无条件探测注册中心；未发现已知外部调用方。
- 兼容选择：N/A
- 回滚目标：N/A
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f6`；新增功能位于未提交工作区
- 结果 Git ref：N/A
- 回滚来源：本轮修改前工作区状态；已提交基线 `a07d3f6` 不包含本功能
- 回滚可用性：部分可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/reference/KsetServiceRefBeanPostProcessor.java` | `resolveService`, `validateLocalReferences` | 服务选择及冲突校验 | modify | active |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/autoconfigure/KsetServiceReferenceAutoConfiguration.java` | `ksetServiceRefBeanPostProcessor` | 注入环境配置 | modify | active |
| `kset-starter-dubbo/src/test/java/com/kset/dubbo/reference/KsetServiceRefBeanPostProcessorTest.java` | 本地与远端选择用例 | 检查无 Dubbo 配置路径 | modify | active |
| `kset-starter-dubbo/README.md` | 本地 / 远程服务引用 | 使用说明 | modify | active |

### 回滚步骤

1. 按本轮修改前工作区状态恢复上述入口与文档。
2. 核对本地注入与远端冲突校验行为。
3. 更新本记录中的实现状态。
