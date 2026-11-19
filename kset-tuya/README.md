# KSet Tuya

`kset-tuya` 是可选的涂鸦云客户端模块，不是 Spring Boot Starter。它提供通用 API 请求客户端和门锁临时密码操作，包名沿用 `com.kset.common.tuya`。

## 接入

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-tuya</artifactId>
</dependency>
```

业务侧创建 `TuyaConfig`，指定 `clientId`、`secret` 和 `region` 或 `baseUrl`，再自行构造客户端；设置 `region` 时优先使用该区域的地址。

```java
TuyaConfig config = TuyaConfig.builder()
        .clientId(clientId)
        .secret(secret)
        .region(TuyaRegion.CN)
        .build();
TuyaApiClient apiClient = new TuyaApiClient(config);
TuyaDoorLockClient doorLockClient = new TuyaDoorLockClient(apiClient);
```

## 主要入口

| 入口 | 用途 |
|------|------|
| `TuyaConfig`、`TuyaRegion` | 访问凭据与数据中心地址 |
| `TuyaApiClient` | 获取和刷新令牌，发送签名后的 GET、POST、PUT、DELETE 请求 |
| `TuyaDoorLockClient` | 获取密码 ticket，创建和删除门锁临时密码 |
| `TempPasswordParam` | 创建临时密码的参数 |

模块使用 JDK `HttpClient` 和 Fastjson2，不提供 Spring 自动装配。门锁操作需要业务侧提供有效的设备 ID、密码参数与涂鸦云凭据。
