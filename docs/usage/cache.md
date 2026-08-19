# 多级缓存

依赖 `kset-starter-cache`。只引入 cache 时用 L1 Caffeine；需要 Redis 二级缓存时再加 `kset-starter-redis`，会自动注册 L2。

不要和 Spring 的 `@Cacheable` 混用。KSet 不注册 `CacheManager`。

## 只使用 L1

```yaml
kset:
  cache:
    default-layers: L1
    cache-null: true
    null-ttl: 1m
    l1:
      default-ttl: 5m
      maximum-size: 10000
```

## L1 + Redis L2

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-redis</artifactId>
</dependency>
```

```java
@KsetCacheable(cacheName = "user", key = "'user:id:' + #id", layers = {L1, L2})
public UserDTO getById(Long id) {
    return queryDb(id);
}

@KsetCaching(evict = {
        @KsetCacheEvict(cacheName = "user", key = "'user:id:' + #id", layers = {L1, L2}),
        @KsetCacheEvict(cacheName = "userByPhone", key = "'user:phone:' + #phone", layers = {L1, L2})
})
public void deleteUser(Long id, String phone) {
    deleteDb(id);
}
```

读顺序固定 L1 → L2 → 方法加载。L2 命中会回填 L1。声明了 L2 但没有适配器时，会降级到可用的 L1；一层都没有才报错。

## 编程式

```java
KsetCacheSpec spec = KsetCacheSpec.builder("user", "user:id:" + id)
        .layers(L1, L2)
        .ttl(Duration.ofMinutes(10))
        .valueType(UserDTO.class)
        .build();

UserDTO user = KsetCache.getOrLoad(spec, UserDTO.class, () -> queryDb(id));
KsetCache.put(spec, user);
KsetCache.evict(spec);
```

同类内部 `this.getById()` 不会走注解 AOP。配置、指标见 [kset-starter-cache/README.md](../../kset-starter-cache/README.md)。
