# Agent Harness 变更记录

- feature-key：`agent-harness`
- 当前 change-id：`agent-harness-positive-retry-limit`
- 当前状态：`active`
- 当前入口：`kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessFactory.java#builder`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| agent-harness-positive-retry-limit | active | AgentHarnessFactory.builder | 非法零值构造参数，原方法内已替换 | e601cfdf0d7e8e18bbc8dcd8f5a5af8e73fe1535 | 源码可恢复；恢复会重新引入初始化故障 | 无并行旧实现，无待清理代码 |

## agent-harness-positive-retry-limit

- 日期：2026-11-23
- 目标：修复 AgentScope 2.0.5 在模型调用前拒绝 `maxRetries=0` 导致的初始化失败。
- 判定时间：2026-11-23，Asia/Shanghai。
- 上线状态：修复未发布；旧参数已在调用方测试环境触发故障，生产发布状态待确认。
- 判定方式：基于本轮跨项目排查证据；不推定生产上线。
- 判定证据：调用方 kset-rag 问答记录 #104 的异常定位到 `AgentHarnessFactory.builder`；本仓当前源码包含对应零值参数。
- 证据来源：本次问答排查报告及当前工厂源码；不在框架文档中复制业务记录、日志或构建产物。
- 当时代码状态：公开方法及 AgentHarnessSpec 契约保持不变，已知调用方为 kset-rag。
- 兼容选择：直接修正非法默认值为最小合法正整数；保留自定义扩展、工具目录、安全默认项与状态存储边界。
- 回滚目标：N/A。
- 旧版本正式线上主分支及对比基线 Git SHA：待确认；不把当前 main HEAD 当作已发布版本。
- 旧版正式发布版本：生产版本待确认；调用方测试环境登记为 1.0.17-SNAPSHOT，不据版本号推定发布提交。
- 基线 Git ref：`e601cfdf0d7e8e18bbc8dcd8f5a5af8e73fe1535`。
- 结果 Git ref：N/A，未提交。
- 回滚来源：上述基线中工厂源码。
- 回滚可用性：源码可恢复，不建议恢复非法参数。
- 验证：2026-11-23 获用户提交、推送及测试服务器部署授权后，执行本模块及所需父 POM 的定向 Maven 构建；质量检查与 1 项构造回归通过，修正后的 1.0.17-SNAPSHOT 已安装到本机 Maven 仓库，供调用方打包。

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessFactory.java | builder | 构造满足原生参数校验的 Harness builder | modify | active |
| kset-starter-agent-harness/src/test/java/com/kset/agent/harness/AgentHarnessFactoryTest.java | nativeBuilderAcceptsDefaultsAndReachesHostCustomization | 验证默认构造可到达宿主自定义扩展 | add | active |
| kset-starter-agent-harness/README.md | 重试边界说明 | 说明合法默认参数及宿主职责 | modify | active |

### 回滚边界

用户选择对应 change-id 后，才按指定 ref 恢复工厂默认值与文档。已安装或发布的依赖不随源码回滚自动替换；调用方测试服务器的应用版本切换与回滚由调用方部署流程负责，本框架未单独部署服务。
