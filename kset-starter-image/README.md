# kset-starter-image

图片 AI 能力 Starter：提供**图片理解（转文字 / 参数提取）**与**图片生成（文生图 / 参考图编辑）**两个入口。默认接入 OpenAI；其他服务只有在实现对应 HTTP 协议且模型支持该操作时才能替换接入。

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

文生图向 `/images/generations` 提交 JSON；参考图编辑向 `/images/edits` 提交 `images: [{image_url: ...}]`。本实现不下载参考图、不提交 multipart 文件，也不适配万相原生异步任务接口（包括 `wanx2.1-t2i-turbo`）；此类服务可由宿主自行实现 `ImageGenerator`。

结果项 `GeneratedImage` 的 `url` 与 `base64` 至少一个为非空白文本，取决于供应商返回形式。无可用图片或响应格式错误时抛出 `ImageException`。

## 说明

- 业务方只依赖 `VisionClient` / `ImageGenerator` 接口；仅在目标服务协议兼容时，通过 `base-url`、`vision.model`、`generation.model`、`generation.default-size` 与 `api-key` 等配置切换。
- 请求失败、供应商拒绝或响应无法解析时抛出 `ImageException`；日志关键词 `[kset-image]`。
- 非法请求与客户端配置抛出 `IllegalArgumentException`；失败不会自动重试，避免重复提交计费生成请求。
- 本模块不内嵌本地 OCR 库，图片识别由多模态模型完成。
- 协议来源：[OpenAI Images](https://platform.openai.com/docs/api-reference/images/create)、[官方 OpenAPI](https://github.com/openai/openai-openapi/blob/master/openapi.yaml)。变更与验证状态见[修复记录](../docs/kset-starter-image/changes/image-client.md)。
