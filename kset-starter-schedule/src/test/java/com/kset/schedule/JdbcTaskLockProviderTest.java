package com.kset.schedule;

import com.kset.schedule.lock.JdbcTaskLockProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTaskLockProviderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcTaskLockProvider provider = new JdbcTaskLockProvider(jdbcTemplate, "t_kset_schedule_lock");

    @Test
    void acquiresLockWhenExistingLockExpired() {
        mockDbNow();
        when(jdbcTemplate.update(contains("lock_until <= ?"), any(), any(), any(), eq("taskA"), any()))
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
        when(jdbcTemplate.update(contains("fencing_token = fencing_token + 1"),
                any(), any(), anyString(), eq("taskA"), any()))
                .thenReturn(1);

        assertThat(provider.tryLock("taskA", Duration.ofMinutes(10))).isTrue();
        provider.unlock("taskA", Duration.ofSeconds(30));

        ArgumentCaptor<String> acquiredOwner = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("fencing_token = fencing_token + 1"),
                any(), any(), acquiredOwner.capture(), eq("taskA"), any());
        verify(jdbcTemplate).update(contains("WHERE name = ? AND locked_by = ?"),
                any(), eq("taskA"), eq(acquiredOwner.getValue()));
    }

    @Test
    void staleExecutionCannotReleaseNewLockOnSameInstance() throws Exception {
        mockDbNow();
        when(jdbcTemplate.update(contains("fencing_token = fencing_token + 1"),
                any(), any(), anyString(), eq("taskA"), any()))
                .thenReturn(1);

        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch secondAcquired = new CountDownLatch(1);
        CountDownLatch oldReleased = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var oldExecution = pool.submit(() -> {
                boolean acquired = provider.tryLock("taskA", Duration.ofSeconds(1));
                firstAcquired.countDown();
                secondAcquired.await();
                provider.unlock("taskA", Duration.ZERO);
                oldReleased.countDown();
                return acquired;
            });
            var newExecution = pool.submit(() -> {
                firstAcquired.await();
                boolean acquired = provider.tryLock("taskA", Duration.ofSeconds(1));
                secondAcquired.countDown();
                oldReleased.await();
                provider.unlock("taskA", Duration.ZERO);
                return acquired;
            });
            assertThat(oldExecution.get(2, TimeUnit.SECONDS)).isTrue();
            assertThat(newExecution.get(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        ArgumentCaptor<String> acquiredOwners = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> releasedOwners = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(contains("fencing_token = fencing_token + 1"),
                any(), any(), acquiredOwners.capture(), eq("taskA"), any());
        verify(jdbcTemplate, times(2)).update(contains("WHERE name = ? AND locked_by = ?"),
                any(), eq("taskA"), releasedOwners.capture());
        assertThat(acquiredOwners.getAllValues().get(0)).isNotEqualTo(acquiredOwners.getAllValues().get(1));
        assertThat(releasedOwners.getAllValues()).containsExactly(
                acquiredOwners.getAllValues().get(0), acquiredOwners.getAllValues().get(1));
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

    @Test
    void rejectsBlankNameAndNonPositiveAtMostFor() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.tryLock(" ", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.tryLock("taskA", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.tryLock("taskA", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void mockDbNow() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Timestamp.class)))
                .thenReturn(new Timestamp(System.currentTimeMillis()));
    }
}
