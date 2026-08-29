package com.kset.schedule;

import com.kset.schedule.lock.JdbcTaskLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTaskLockProviderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcTaskLockProvider provider = new JdbcTaskLockProvider(jdbcTemplate, "t_kset_schedule_lock");

    @Test
    void acquiresLockWhenExistingLockExpired() {
        mockDbNow();
        when(jdbcTemplate.update(contains("UPDATE"), any(), any(), any(), eq("taskA"), any()))
                .thenReturn(1);

        assertThat(provider.tryLock("taskA", Duration.ofMinutes(10))).isTrue();
    }

    @Test
    void acquiresLockByInsertWhenNeverLocked() {
        mockDbNow();
        when(jdbcTemplate.update(contains("UPDATE"), any(), any(), any(), eq("taskB"), any()))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT"), eq("taskB"), any(), any(), any()))
                .thenReturn(1);

        assertThat(provider.tryLock("taskB", Duration.ofMinutes(10))).isTrue();
    }

    @Test
    void failsWhenOtherInstanceHoldsLock() {
        mockDbNow();
        when(jdbcTemplate.update(contains("UPDATE"), any(), any(), any(), eq("taskC"), any()))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT"), eq("taskC"), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate name"));

        assertThat(provider.tryLock("taskC", Duration.ofMinutes(10))).isFalse();
    }

    @Test
    void unlockShortensLockUntilWithAtLeastFor() {
        mockDbNow();
        when(jdbcTemplate.update(contains("SET lock_until"), any(), eq("taskA"), anyString()))
                .thenReturn(1);

        provider.unlock("taskA", Duration.ofSeconds(30));

        org.mockito.Mockito.verify(jdbcTemplate)
                .update(contains("SET lock_until"), any(), eq("taskA"), anyString());
    }

    @Test
    void createTableAutoDetectsPostgresDialect() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);

        JdbcTaskLockProvider pgProvider = new JdbcTaskLockProvider(new JdbcTemplate(dataSource));
        pgProvider.createTableIfNotExists();

        org.mockito.Mockito.verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS t_kset_schedule_lock"));
    }

    @Test
    void createTableAppendsMysqlEngineClause() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.createStatement()).thenReturn(statement);

        JdbcTaskLockProvider mysqlProvider = new JdbcTaskLockProvider(new JdbcTemplate(dataSource));
        mysqlProvider.createTableIfNotExists();

        org.mockito.Mockito.verify(statement).execute(contains("ENGINE=InnoDB"));
    }

    private void mockDbNow() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Timestamp.class)))
                .thenReturn(new Timestamp(System.currentTimeMillis()));
    }
}
