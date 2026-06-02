# Cache 域规范

K001: Key 格式 {system}:{module}:{business}:{identifier}
K002: system 取项目短名，init 写入 project-spec
K003: 默认 TTL 30min，按数据特性调整
K004: 写操作先更新 DB 再删缓存，Cache-Aside 默认
K005: 穿透：布隆过滤器或空值缓存
K006: 雪崩：随机过期与互斥锁
K007: Write-Through 用于强一致写，Read-Through 用于懒加载
K008: Key 业务段与 DDD 实体语义一致
K009: 禁止用缓存 Key 重新定义字段含义
K010: 不产出 design/cache 文档；落地以配置与 Cacheable 代码为准
K011: 缓存命中返回的 List/Set/Map 禁止原地变更，须见 coding 域 C001
