# 图片客户端变更记录

## 本轮：硅基流动通义模型接入

- change-id：`image-siliconflow-protocol`；日期：2026-11-24，Asia/Shanghai；状态：`active`，代码与配置已完成，供应商 HTTP 正常路径及模块编译、25 项回归已通过；1.0.18 待发布。
- 目标：按用户提供的 SiliconFlow 文档及密钥接入平台上的通义视觉与生图模型；不将其称为阿里云百炼直连接入。
- 基线 Git ref：`06c9977a567566d00db242c6731a8cca246173dc`；结果 Git ref：N/A，未提交。
- 上线状态与证据：不涉及旧契约替换；历史记录确认 1.0.17 构件已发布，未取得业务部署或生产调用证据。本轮仅增加可选的生成协议，原 OpenAI 默认行为和公开请求/结果签名保留，无调用方迁移要求。
- 当前入口：`ImageAutoConfiguration#imageGenerator` 固定创建 OpenAI 客户端；现有 `ImageGenerator` 为可复用扩展点。
- 当前新增入口：`SiliconFlowImageGenerator`，复用 `ImageClientSupport`，通过 `ImageProperties.Generation.provider` 显式选择；默认仍为 `OPENAI`。`ImageAutoConfiguration#imageGenerator` 已按枚举路由。
- 范围：生成客户端、配置与装配、独立本地供应商配置、模块 README、字段索引及 project-spec 定位索引；不新增 SDK 或模块依赖。
- 回滚来源：上述基线 Git ref 可恢复本轮涉及的已跟踪源码；新增适配器及配置按本轮文件索引定向移除，保留此前智谱配置及无关本地密钥。
- 兼容选择：兼容增量，仅新增可选配置和独立实现；历史公开签名及默认客户端保留。未切换既有默认配置，无旧入口移除；正式应用线上版本及旧版对比待确认，本轮不宣称正式线上新旧差异。
- 验证：供应商 HTTP 最小正常路径及图片模块 verify 已通过；真实宿主端到端与发布状态分开记录。

| 文件与符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- |
| `kset-starter-image/src/main/java/com/kset/image/client/SiliconFlowImageGenerator.java` | SiliconFlow 图片生成与参考图变体协议 | add | active |
| `kset-starter-image/src/main/java/com/kset/image/config/ImageProperties.java#Generation` | 可选生成供应商，默认 OPENAI | modify | active |
| `kset-starter-image/src/main/java/com/kset/image/autoconfigure/ImageAutoConfiguration.java#imageGenerator` | 按配置选择客户端 | modify | active |
| `kset-starter-image/src/main/java/com/kset/image/client/OpenAiCompatibleImageGenerator.java` | 保留原 OpenAI 配置路径 | retain | active-compatible |
| `env/image-siliconflow.properties` | 硅基流动通义模型本地测试配置 | add | active |
| `kset-starter-image/src/test/java/com/kset/image/client/SiliconFlowImageGeneratorTest.java` | 进程内 HTTP 原生协议与拒绝/失败路径 | add | active |
| `kset-starter-image/src/test/java/com/kset/image/autoconfigure/ImageAutoConfigurationTest.java` | 配置绑定、默认装配与宿主覆盖回归 | add | active |

### 本轮验收与验证边界

测试时间为 2026-11-24（Asia/Shanghai），级别为供应商 HTTP L1/L2。目标 `https://api.siliconflow.cn/v1`，使用正常 Bearer 鉴权与 JSON POST；测试前 `/models` 返回 HTTP 200、92 个模型，所选模型均在列表中。输入为内存构造的 256x256 PNG：白底、左侧红色色块、右侧蓝色色块；生图提示为橘猫坐在蓝色窗台、晴朗天空、简洁插画。没有 SQL 或本地业务数据变化。

| 用例与接口 | 预期 | 实际与定位依据 | 结果 |
| --- | --- | --- | --- |
| L1 `GET /models` | 鉴权成功，存在所选模型 | HTTP 200；92 个模型，包含 `Qwen/Qwen3-VL-32B-Instruct` 与 `Qwen/Qwen-Image` | PASSED |
| L2 `POST /chat/completions` 图片理解 | 文本说明左红、右蓝 | HTTP 200；返回两个并列色块、左红右蓝、中间白色间隔；约 878ms；trace `ti_edtdvjny4ivr8d96rc` | PASSED |
| L2 `POST /chat/completions` 结构化提取 | 合法 JSON，红蓝各 1 个、白色背景 | HTTP 200；断言 `red_blocks=1`、`blue_blocks=1`、`background=white` 全部通过；trace `ti_8ucblominoprpzzfw1` | PASSED |
| L2 `POST /images/generations` 文生图 | `images[]` 返回 1 个可下载 URL | HTTP 200；约 24989ms，trace `ti_i0jcid2m35dcol4uyh`；下载 200，PNG 文件签名及尺寸 1328x1328 通过，约 1.2MB；人工查看符合橘猫、蓝色窗台与简洁插画描述 | PASSED |
| Java 编译与进程内 HTTP / 自动装配 | 新配置绑定、客户端选择、Java 请求与解析正确 | `mvn -B -pl kset-starter-image -am verify`：父 POM / 图片模块 SUCCESS；25 项测试通过，无失败、错误、跳过；Checkstyle / PMD 通过 | PASSED |
| 真实宿主端到端 | 在宿主应用加载配置并调用真实供应商 | 未启动宿主应用，进程内测试不代表真实宿主业务链路 | NOT_RUN |
| 参考图编辑 | 编辑模型与 image 协议成功 | 已实现协议映射，未额外调用计费编辑接口 | NOT_RUN |

图片下载响应的 Content-Type 为 `application/octet-stream`，按 PNG 字节签名及 IHDR 尺寸确认真实文件类型，不因 MIME 声明判定失败；未重复生成图片。生成样图保存于会话可视化产物区，未将带签名的临时图片 URL 写入项目文档。供应商服务端日志不可访问，真实调用仅核对响应、trace 与结果图片；后续进程内 HTTP 测试已观察客户端成功日志，无宿主业务日志证据。无本地临时脚本或测试数据文件需要清理；保留本次生成样图供用户查看及 Git 忽略的本地密钥配置。

静态复核：新增实现复用现有 RestClient、超时和异常工具，无 SDK/版本/模块依赖变更或循环依赖；按供应商协议区分 `image_size` 与 `image`，解析 `images[].url`，空结果明确失败，拒绝多张请求，不自动重试。成功日志仅记录供应商、模型与数量，异常保留 cause，不输出密钥、prompt、图片或响应正文。Spring 属性绑定沿用配置框架，无重复查询，缓存不适用；无数据库、事务、Redis、MQ、任务抢占、跨线程上下文或共享业务状态变更。网络结果未知时由调用方按原调用结果核验，不自动重发计费请求。`git diff --check` 通过；编译和进程内测试另有本轮 verify 证据。

## 历史：OpenAI 协议与发布

- feature-key：`image-client`
- change-id：`image-openai-protocol-validation`
- 状态：`active`，代码已提交，全仓 verify 通过；1.0.17 已发布至 Nexus，未调用真实供应商。
- 日期与判定时间：2026-11-24，Asia/Shanghai。
- 基线 Git ref：`a592b394544267b2d14e79b794f01e61cfb94c31`；此提交不包含图片模块，修复前实现来自本次未跟踪工作区文件。
- 结果 Git ref：`adb78de0966e451a73e808649298c1c94c7cfe1d`。
- 发布 Git ref：`v1.0.17`，提交 `5673f3b457771ec0328f3a2b7fe4003777cc02cf`；版本提交与标签均已推送。
- 上线状态：1.0.17 Maven 构件已发布；未取得业务应用部署或生产调用证据，不推定应用已上线。

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

按标准流程执行 `release:prepare`（1.0.17 → 1.0.18-SNAPSHOT）及 `release:perform` 均 BUILD SUCCESS。2026-11-24 发布后核对 Nexus：18 个可发布模块 POM 均为 1.0.17，17 个 JAR 均可读取响应头，父 POM 的内部依赖版本属性为 1.0.17。发布构建按配置跳过重复测试，依据为此前同源码的全仓 verify 成功结果。主工作树进入 1.0.18-SNAPSHOT，未执行应用部署。

## 回滚与清理

- 回滚目标：N/A，无已登记的旧 change-id。
- 回滚来源与可用性：原图片模块属于未提交、未跟踪文件，没有可恢复原缺陷实现的 Git ref；上述基线只用于定位仓库时点，不能用于恢复原模块。
- 恢复旧默认配置会重新引入协议不匹配，不建议单独回退配置。
- 本次发布源码可由 v1.0.17 标签恢复；恢复源码不撤销 Nexus 构件，使用方须按自己的版本切换流程回滚。
- 无并行旧客户端或临时脚本；release:perform 已完成自身发布状态清理。`target/checkout` 为本次标签检出与发布构建目录，递归删除操作被自动审批以 blocked by policy 拒绝，本次保留一份，不继续生成副本；无发布恢复需要后应按项目清理规则处理。常规 target 构建输出由 Maven 管理。本次新建 helper 均被生产入口或图片编码路径引用。
