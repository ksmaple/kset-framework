# 图片客户端变更记录

- feature-key：`image-client`
- change-id：`image-openai-protocol-validation`
- 状态：`active`，代码已提交，全仓 verify 通过；准备发布 1.0.17，未调用真实供应商。
- 日期与判定时间：2026-11-24，Asia/Shanghai。
- 基线 Git ref：`a592b394544267b2d14e79b794f01e61cfb94c31`；此提交不包含图片模块，修复前实现来自本次未跟踪工作区文件。
- 结果 Git ref：`adb78de0966e451a73e808649298c1c94c7cfe1d`。
- 上线状态：源码已推送 main；1.0.17 构件待发布，未取得业务应用部署或生产调用证据。

## 当前入口与替代关系

| 文件与符号 | 职责 | 替代关系 | 状态 |
| --- | --- | --- | --- |
| `kset-starter-image/src/main/java/com/kset/image/client/OpenAiCompatibleImageGenerator.java#generate` | 文生图与 JSON 参考图编辑 | 原生成请求夹带 image 字段已移除 | active |
| `kset-starter-image/src/main/java/com/kset/image/client/OpenAiCompatibleVisionClient.java#understand` | 图片理解与结构化解析 | 原扩展名 MIME 推断、固定 JPEG base64 已移除 | active |
| `kset-starter-image/src/main/java/com/kset/image/client/ImageClientSupport.java` | 地址、超时和响应边界 | 复用两客户端相同的连接配置与响应校验 | active |
| `kset-starter-image/src/main/java/com/kset/image/client/ImageDataUrls.java` | 图片签名识别与 data URL | 按 PNG、JPEG、GIF、WebP 内容识别 MIME | active |
| `kset-starter-image/src/main/java/com/kset/image/autoconfigure/ImageAutoConfiguration.java` | 开关、密钥条件与宿主覆盖 | 原属性存在判断改为非空白密钥判断 | active |
| `kset-starter-image/src/main/java/com/kset/image/vision/VisionResult.java` | 不可修改的结果 Map | 替换不支持 JSON null 字段的 Map.copyOf | active |
| `kset-starter-image/pom.xml`、`kset-architecture-tests/pom.xml` | 编译日志 API 与架构测试覆盖 | 显式声明 SLF4J API，架构测试引入 image 模块 | active |

公开方法签名保持不变。默认服务调整为 OpenAI，默认理解模型为 `gpt-4.1-mini`、生成模型为 `gpt-image-2.5-flare`、尺寸为 `1024x1024`；修复前默认的 DashScope/万相组合不再保留。原生万相异步任务及 multipart 编辑不属于该 JSON 客户端能力；宿主可通过原有接口提供自己的实现，不新增供应商 SDK 或内部模块依赖。

## 静态验收与回归点

| 场景 | 预期 | 状态 |
| --- | --- | --- |
| 文生图 / 参考图编辑 | 分别调用 generations / edits，编辑 JSON 使用 images[].image_url | 静态核对；HTTP 回归 NOT_RUN |
| 模型返回 null 字段 | 保留原始文本及该字段，不抛 Map.copyOf 的空值异常 | 静态核对；回归 NOT_RUN |
| 密钥缺失、空串、全空白 | 不创建默认客户端；两能力均关闭时不误报 | 静态核对；容器回归 NOT_RUN |
| 非正、亚毫秒或超范围 timeout | 创建客户端时明确拒绝，不转换为无限等待或溢出 | 静态核对；回归 NOT_RUN |
| PNG、JPEG、GIF、WebP 的 base64 或本地文件 | data URL MIME 根据图片字节签名确定 | 静态核对；回归 NOT_RUN |
| 空响应、非法 JSON、非对象响应、全空图片项 | 抛 ImageException，不伪造成功图片 | 静态核对；回归 NOT_RUN |
| 编译依赖、架构测试加载 | SLF4J 为主依赖，image 加入架构测试类路径 | 全仓 verify PASSED；图片模块 16 项测试及架构 2 项测试通过 |

2026-11-24 收到提交、推送与发布要求后，按发布前检查执行 `mvn -B -pl kset-starter-image -am -Dmaven.test.skip=true compile`，父 POM 与图片模块均 BUILD SUCCESS，Checkstyle、PMD 无违规。保留现有测试代码，未新增或运行测试；真实供应商调用及计费结果未验证。

随后用户明确授权验证，执行 `mvn -B verify`，全部 20 个模块 SUCCESS，退出码 0。图片模块现有 16 项测试及框架 2 项架构测试均无失败、错误或跳过。上述专项回归点尚未逐项增加独立测试，不将现有用例结果扩大为未覆盖场景或真实供应商调用通过。

## 回滚与清理

- 回滚目标：N/A，无已登记的旧 change-id。
- 回滚来源与可用性：原图片模块属于未提交、未跟踪文件，没有可恢复原缺陷实现的 Git ref；上述基线只用于定位仓库时点，不能用于恢复原模块。
- 恢复旧默认配置会重新引入协议不匹配，不建议单独回退配置。
- 无并行旧客户端、临时脚本、构建输出或待清理副本；本次新建 helper 均被两个生产入口或图片编码路径引用。
