package com.kset.schedule.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * SQL 锁表实现的唯一运行锁（{@code t_kset_schedule_lock}）。
 *
 * <p>抢锁语义：先按"锁已过期"条件 UPDATE（fencing token 自增），未命中再尝试 INSERT，
 * 主键冲突说明其他实例已持有。每次获取使用独立持有标识，释放仅匹配本次持有。
 * 锁时间以数据库当前时间为准，规避多实例时钟漂移。</p>
 */
public class JdbcTaskLockProvider implements TaskLockProvider {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskLockProvider.class);

    public static final String DEFAULT_TABLE = "t_kset_schedule_lock";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final String instanceId;
    private final ThreadLocal<Map<String, Deque<String>>> heldLocks = ThreadLocal.withInitial(HashMap::new);

    public JdbcTaskLockProvider(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_TABLE);
    }

    public JdbcTaskLockProvider(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tableName = (tableName == null || tableName.isBlank()) ? DEFAULT_TABLE : tableName;
        this.instanceId = resolveInstanceId();
    }

    /**
     * 启动时幂等建表：自动识别数据库方言（PostgreSQL / MySQL / MariaDB，其他按通用 DDL）。
     */
    public void createTableIfNotExists() {
        String dialect = detectDialect();
        jdbcTemplate.execute(createTableSql(dialect));
        log.info("[kset-schedule] 锁表就绪: {} (dialect={})", tableName, dialect);
    }

    /**
     * 通过连接元数据识别数据库方言；识别失败回退通用 DDL（TIMESTAMP/BIGINT 主流库均可识别）。
     */
    private String detectDialect() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return "generic";
        }
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null) {
                return "generic";
            }
            String normalized = product.toLowerCase(Locale.ROOT);
            if (normalized.contains("postgres")) {
                return "postgresql";
            }
            if (normalized.contains("mysql") || normalized.contains("mariadb")) {
                return "mysql";
            }
            return "generic(" + product + ")";
        } catch (Exception e) {
            log.warn("[kset-schedule] 数据库方言识别失败，按通用 DDL 建表: {}", e.getMessage());
            return "generic";
        }
    }

    private String createTableSql(String dialect) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" ("
                + "name VARCHAR(128) PRIMARY KEY,"
                + " lock_until TIMESTAMP NOT NULL,"
                + " locked_at TIMESTAMP NOT NULL,"
                + " locked_by VARCHAR(255) NOT NULL,"
                + " fencing_token BIGINT NOT NULL DEFAULT 0"
                + ")");
        if ("mysql".equals(dialect)) {
            sql.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        return sql.toString();
    }

    @Override
    public boolean tryLock(String name, Duration atMostFor) {
        validateLockRequest(name, atMostFor);
        Instant now = dbNow();
        Instant lockUntil = now.plus(atMostFor);
        String owner = instanceId.substring(0, Math.min(instanceId.length(), 180)) + ":" + UUID.randomUUID();
        int updated = jdbcTemplate.update(
                "UPDATE " + tableName + " SET lock_until = ?, locked_at = ?, locked_by = ?,"
                        + " fencing_token = fencing_token + 1 WHERE name = ? AND lock_until <= ?",
                Timestamp.from(lockUntil), Timestamp.from(now), owner, name, Timestamp.from(now));
        if (updated > 0) {
            rememberLock(name, owner);
            log.debug("[kset-schedule] 抢锁成功（续期）: name={}, owner={}", name, owner);
            return true;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + tableName + " (name, lock_until, locked_at, locked_by, fencing_token)"
                            + " VALUES (?, ?, ?, ?, 1)",
                    name, Timestamp.from(lockUntil), Timestamp.from(now), owner);
            rememberLock(name, owner);
            log.debug("[kset-schedule] 抢锁成功（新建）: name={}, owner={}", name, owner);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("[kset-schedule] 锁被其他实例持有，跳过: name={}", name);
            return false;
        }
    }

    @Override
    public void unlock(String name, Duration atLeastFor) {
        Map<String, Deque<String>> locks = heldLocks.get();
        Deque<String> owners = locks.get(name);
        if (owners == null || owners.isEmpty()) {
            throw new IllegalStateException("lock must be released by its acquiring thread: " + name);
        }
        String owner = owners.peek();
        try {
            Instant keepUntil = dbNow().plus(atLeastFor);
            jdbcTemplate.update(
                    "UPDATE " + tableName + " SET lock_until = ? WHERE name = ? AND locked_by = ?",
                    Timestamp.from(keepUntil), name, owner);
        } finally {
            owners.pop();
            if (owners.isEmpty()) {
                locks.remove(name);
            }
            if (locks.isEmpty()) {
                heldLocks.remove();
            }
        }
    }

    private void rememberLock(String name, String owner) {
        heldLocks.get().computeIfAbsent(name, ignored -> new ArrayDeque<>()).push(owner);
    }

    /**
     * 入参防御：空锁名会直接打到 DB；atMostFor 非正数会让锁立即过期，唯一运行语义静默失效。
     */
    private static void validateLockRequest(String name, Duration atMostFor) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("lock name must not be blank");
        }
        if (atMostFor == null || atMostFor.isZero() || atMostFor.isNegative()) {
            throw new IllegalArgumentException(
                    "atMostFor must be positive, otherwise the lock expires immediately and uniqueness is broken: " + atMostFor);
        }
    }

    /**
     * 以数据库当前时间为准，规避多实例时钟漂移。
     */
    private Instant dbNow() {
        Timestamp now = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        if (now == null) {
            throw new IllegalStateException("database clock returned no value");
        }
        return now.toInstant();
    }

    private static String resolveInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        return host + "-" + pid;
    }
}
