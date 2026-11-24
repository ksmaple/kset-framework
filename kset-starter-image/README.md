# kset-starter-image

图片 AI 能力 Starter：提供**图片理解（转文字 / 参数提取）**与**图片生成（文生图 / 参考图编辑）**两个入口。默认接入 OpenAI；图片生成另提供 SiliconFlow 原生 JSON 适配，按配置选择。其他服务须实现所选客户端的 HTTP 协议，模型须支持相应操作。

## 引入

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-image</artifactId>
</dependency>
```

## 配置

```yaml
kset:
  image:
    enabled: true                 # 总开关，默认 true
    base-url: https://api.openai.com/v1
    api-key: ${OPENAI_API_KEY:}    # 缺失、空串或全空白时不装配客户端，启动告警
    vision:
      enabled: true               # 图片理解开关
      model: gpt-4.1-mini
      timeout: 60s
      max-tokens: 2048
    generation:
      enabled: true               # 图片生成开关
      provider: openai             # openai（默认）或 siliconflow
      model: gpt-image-2.5-flare
      default-size: 1024x1024
      timeout: 120s
```

模型名称由目标服务决定，接入方须具有对应模型的访问权限。`timeout` 分别约束连接与读取，不是整个调用的总耗时上限；配置须在 `1ms` 至 `2147483647ms` 之间。启用客户端时会预检服务地址、模型、超时、默认输出 token 数和默认图片尺寸是否有效；尺寸支持范围及张数上限由目标服务校验。

例如仅使用 DashScope 图片理解时，覆盖以下配置，并关闭不兼容的生成能力：

```yaml
kset:
  image:
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY:}
    vision:
      model: qwen-vl-plus
    generation:
      enabled: false
```

两项能力都关闭时不输出缺少密钥的告警。宿主声明自己的 `VisionClient` 或 `ImageGenerator` Bean 时，对应默认客户端退让。

### 智谱测试配置

仓库提供独立配置 [env/image-zhipu.properties](../env/image-zhipu.properties)。宿主应用从仓库根目录运行时，添加启动参数 `--spring.config.additional-location=optional:file:./env/image-zhipu.properties`；此文件不会由 Starter 自动加载。其他工作目录需调整配置文件及 `spring.config.import` 的路径。

密钥通过 `ZHIPU_API_KEY` 环境变量或本地 `env/.env` 中的同名属性提供，`env/.env` 已被 Git 忽略。配置文件仅保存占位符，不包含密钥。已有系统环境变量优先于文件属性。

| 能力 | 模型 | 配置说明 |
| --- | --- | --- |
| 图片理解 / 参数提取 | `glm-5.3-flash` | 官方当前已开放 API 的原生多模态模型；默认开启思考，配置 8192 输出 tokens、180s 超时，为思考及正文留出空间 |
| 文生图 | `glm-image` | 智谱当前旗舰图像生成模型；1280x1280，单张生成 |

上述模型依据 [智谱模型索引](https://docs.bigmodel.cn/llms.txt)、[GLM-5.3-Flash](https://docs.bigmodel.cn/cn/guide/models/vlm/glm-5.3-flash) 与 [GLM-Image](https://docs.bigmodel.cn/cn/guide/models/image-generation/glm-image) 文档选择。更新的 [GLM-5.4](https://docs.bigmodel.cn/cn/guide/models/glm-5.4) 文档注明普通模型 API 尚未开放，因此未用于此地址的测试配置。

智谱 GLM-Image 文档仅声明文生图能力，不能据此使用本模块 `/images/edits` 参考图编辑。`n` 对应的多张生成未验证，使用时指定 `withCount(1)`。

2026-11-24 使用模块同形 JSON 请求直连智谱：`glm-5.3-flash` 图片理解与 `glm-image` 文生图均返回 HTTP 429、业务码 `1113`（余额不足或无可用资源包）。免费 `glm-4.6v-flash` 补充诊断返回 `1305`（服务繁忙）。本轮未取得成功图片理解或生成结果，结构化提取及 Java 客户端端到端调用未验证；充值或启用相应资源包后需复测。

### 硅基流动通义模型配置

使用 [env/image-siliconflow.properties](../env/image-siliconflow.properties)，宿主从仓库根目录运行时添加：

```text
--spring.config.additional-location=optional:file:./env/image-siliconflow.properties
```

密钥读取 `SILICONFLOW_API_KEY` 环境变量或 Git 忽略的 `env/.env` 同名属性。该配置适用于 1.0.18 起的版本；1.0.17 不包含 `generation.provider=siliconflow` 适配。宿主升级 parent / Starter 后，把此文件的 `kset.image.*` 属性放入宿主配置或显式加载该文件，再注入 `VisionClient` / `ImageGenerator`。配置不会由 Starter 自动加载；其他工作目录需调整文件及导入路径。

| 能力 | 模型 / 设置 | 协议 |
| --- | --- | --- |
| 图片理解与参数提取 | `Qwen/Qwen3-VL-32B-Instruct`，2048 tokens | `/chat/completions`，继续复用 `VisionClient` |
| 文生图 | `Qwen/Qwen-Image`，1328x1328 | `generation.provider=siliconflow`，使用 `image_size`，解析 `images[].url` |
| 参考图编辑（可选，未实测） | 将生成模型改为 `Qwen/Qwen-Image-Edit-2509` | 同一 `/images/generations` 地址，使用 `image` 字段；请求不指定 size |

平台地址为 `https://api.siliconflow.cn/v1`，此配置接入的是硅基流动托管的通义模型。模型来自该账户实际 `/models` 列表，不据列表顺序宣称为最新版本。硅基流动每次图片请求仅生成一张；`withCount(2)` 等多张请求在调用前拒绝，不自动循环多次计费。参考图与模型的支持关系由供应商校验，本适配器的参考图请求不接受自定义 size。生成 URL 按官方文档约一小时失效，需要留存时及时转存。

协议依据：[快速上手](https://api-docs.siliconflow.cn/docs/userguide/quickstart)、[多模态输入](https://api-docs.siliconflow.cn/docs/userguide/capabilities/multimodal-vision)、[图片生成 API](https://api-docs.siliconflow.cn/docs/api/images-generations-post)。实际验证结果见 [图片客户端变更记录](../docs/kset-starter-image/changes/image-client.md)。

## 图片理解 / 参数提取

```java
@Autowired
private VisionClient visionClient;

// 自由理解
VisionResult result = visionClient.understand(
        VisionRequest.ofUrl("https://example.com/nameplate.jpg", "描述这张图片的内容"));
String text = result.rawText();

// 结构化参数提取：responseSchema 作为提示词中的结构说明，结果解析进 structuredParams
VisionResult params = visionClient.understand(
        VisionRequest.ofPath(Path.of("nameplate.jpg"), "提取设备铭牌参数")
                .withResponseSchema("{\"型号\":\"string\",\"电压\":\"string\",\"功率\":\"string\"}"));
Map<String, Object> map = params.structuredParams();
```

图片来源三选一：`ofUrl`（供应商可访问的 URL）、`ofBase64`（不含 data URL 前缀）、`ofPath`（本地文件）。后两者按图片内容识别 PNG、JPEG、GIF 或 WebP 并转为 data URL；接入方仍须核对目标模型支持的格式和大小。

`responseSchema` 是结构说明提示词，不执行 JSON Schema 校验。模型输出不是合法 JSON 对象时保留 `rawText` 并返回空 Map；对象字段值为 JSON `null` 时原样保留。`structuredParams` 顶层 Map 不可修改。

## 图片生成

```java
@Autowired
private ImageGenerator imageGenerator;

// 文生图
ImageGenerationResult result = imageGenerator.generate(
        ImageGenerationRequest.of("一只站在屋檐上的橘猫，水彩风格")
                .withSize("1024x1024").withCount(1));

// 参考图编辑：要求服务与模型支持 /images/edits 的 JSON 请求
ImageGenerationResult edited = imageGenerator.generate(
        ImageGenerationRequest.ofReference("https://example.com/cat.png", "换成夜景"));
```

默认 OpenAI 文生图向 `/images/generations` 提交 JSON；参考图编辑向 `/images/edits` 提交 `images: [{image_url: ...}]`。SiliconFlow 协议见上方专节。本模块不下载参考图、不提交 multipart 文件，也不适配万相原生异步任务接口（包括 `wanx2.1-t2i-turbo`）；此类服务可由宿主自行实现 `ImageGenerator`。

结果项 `GeneratedImage` 的 `url` 与 `base64` 至少一个为非空白文本，取决于供应商返回形式。无可用图片或响应格式错误时抛出 `ImageException`。

## 说明

- 业务方只依赖 `VisionClient` / `ImageGenerator` 接口；按目标服务协议选择 `generation.provider`，再通过 `base-url`、`vision.model`、`generation.model`、`generation.default-size` 与 `api-key` 等配置切换。
- 请求失败、供应商拒绝或响应无法解析时抛出 `ImageException`；日志关键词 `[kset-image]`。
- 非法请求与客户端配置抛出 `IllegalArgumentException`；失败不会自动重试，避免重复提交计费生成请求。
- 本模块不内嵌本地 OCR 库，图片识别由多模态模型完成。
- 协议来源：[OpenAI Images](https://platform.openai.com/docs/api-reference/images/create)、[官方 OpenAPI](https://github.com/openai/openai-openapi/blob/master/openapi.yaml)。变更与验证状态见[修复记录](../docs/kset-starter-image/changes/image-client.md)。
