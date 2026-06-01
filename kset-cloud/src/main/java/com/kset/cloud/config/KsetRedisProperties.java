package com.kset.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 约定配置（由 starter-redis 消费）。
 */
@ConfigurationProperties(prefix = "kset.redis")
public class KsetRedisProperties {

    public static final String PRIMARY_SOURCE_NAME = "primary";

    private boolean enabled = true;
    private String keyPrefix = "";
    /** 全局默认过期时间，禁止永久 key；写操作未传 ttl 时使用 */
    private Duration defaultTtl = Duration.ofMinutes(30);
    /** 可选：单次写入允许的最大 TTL */
    private Duration maxTtl;
    private final Cache cache = new Cache();
    private final Stream stream = new Stream();
    private final Redisson redisson = new Redisson();
    private final Lock lock = new Lock();
    private final Map<String, RedisSourceProperties> sources = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Cache getCache() {
        return cache;
    }

    public Map<String, RedisSourceProperties> getSources() {
        return sources;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getMaxTtl() {
        return maxTtl;
    }

    public void setMaxTtl(Duration maxTtl) {
        this.maxTtl = maxTtl;
    }

    public Stream getStream() {
        return stream;
    }

    public Redisson getRedisson() {
        return redisson;
    }

    public Lock getLock() {
        return lock;
    }

    /**
     * 分布式锁（注解锁等）。
     */
    public static class Lock {

        /** 是否注册 {@code @KsetLocked} AOP 切面 */
        private boolean annotationEnabled = true;

        /** 启动时校验注解锁目标并输出失效场景提示 */
        private boolean validateTargets = true;

        public boolean isAnnotationEnabled() {
            return annotationEnabled;
        }

        public void setAnnotationEnabled(boolean annotationEnabled) {
            this.annotationEnabled = annotationEnabled;
        }

        public boolean isValidateTargets() {
            return validateTargets;
        }

        public void setValidateTargets(boolean validateTargets) {
            this.validateTargets = validateTargets;
        }
    }

    /**
     * 高危操作流式/分批参数。
     */
    public static class Stream {

        private int scanBatchSize = 500;
        private int deleteBatchSize = 500;
        private int mgetChunkSize = 100;
        private int hashScanCount = 100;
        private boolean useUnlink = true;

        public int getScanBatchSize() {
            return scanBatchSize;
        }

        public void setScanBatchSize(int scanBatchSize) {
            this.scanBatchSize = scanBatchSize;
        }

        public int getDeleteBatchSize() {
            return deleteBatchSize;
        }

        public void setDeleteBatchSize(int deleteBatchSize) {
            this.deleteBatchSize = deleteBatchSize;
        }

        public int getMgetChunkSize() {
            return mgetChunkSize;
        }

        public void setMgetChunkSize(int mgetChunkSize) {
            this.mgetChunkSize = mgetChunkSize;
        }

        public int getHashScanCount() {
            return hashScanCount;
        }

        public void setHashScanCount(int hashScanCount) {
            this.hashScanCount = hashScanCount;
        }

        public boolean isUseUnlink() {
            return useUnlink;
        }

        public void setUseUnlink(boolean useUnlink) {
            this.useUnlink = useUnlink;
        }
    }

    /**
     * Redisson 分布式锁与通用编解码（Jackson）。
     */
    public static class Redisson {

        private boolean enabled = true;
        private Duration lockWaitTime = Duration.ofSeconds(3);
        private Duration lockLeaseTime = Duration.ofSeconds(30);
        private int threads = 16;
        private int nettyThreads = 32;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getLockWaitTime() {
            return lockWaitTime;
        }

        public void setLockWaitTime(Duration lockWaitTime) {
            this.lockWaitTime = lockWaitTime;
        }

        public Duration getLockLeaseTime() {
            return lockLeaseTime;
        }

        public void setLockLeaseTime(Duration lockLeaseTime) {
            this.lockLeaseTime = lockLeaseTime;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getNettyThreads() {
            return nettyThreads;
        }

        public void setNettyThreads(int nettyThreads) {
            this.nettyThreads = nettyThreads;
        }
    }

    public static class Cache {
        private boolean enabled = false;
        private Duration defaultTtl = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }
    }

    /**
     * 命名 Redis 数据源（{@code kset.redis.sources.{name}}）。
     */
    public static class RedisSourceProperties {

        private boolean enabled = true;
        private String keyPrefix = "";
        private String host = "localhost";
        private int port = 6379;
        private int database = 0;
        private String password;
        private boolean ssl = false;
        private Duration timeout = Duration.ofSeconds(2);
        private final RedisSourcePoolProperties pool = new RedisSourcePoolProperties();
        private final Cluster cluster = new Cluster();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public RedisSourcePoolProperties getPool() {
            return pool;
        }

        public Cluster getCluster() {
            return cluster;
        }

        public boolean isClusterMode() {
            return cluster.isEnabled() && cluster.getNodes() != null && !cluster.getNodes().isEmpty();
        }
    }

    public static class RedisSourcePoolProperties {

        private boolean enabled = false;
        private int maxActive = 8;
        private int maxIdle = 8;
        private int minIdle = 0;
        private Duration maxWait = Duration.ofMillis(-1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxActive() {
            return maxActive;
        }

        public void setMaxActive(int maxActive) {
            this.maxActive = maxActive;
        }

        public int getMaxIdle() {
            return maxIdle;
        }

        public void setMaxIdle(int maxIdle) {
            this.maxIdle = maxIdle;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }
    }

    public static class Cluster {

        private boolean enabled = false;
        private List<String> nodes = List.of();
        private int maxRedirects = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes != null ? nodes : List.of();
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }
    }
}
