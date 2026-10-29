package dev.patra.catalog.infra.kernel.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// `PgAdvisoryRunLockAdapter` 集成测试。
///
/// 关闭测试事务：测试事务会把连接绑在线程上，会话级 advisory lock 在同一会话里可重入，
/// 不关事务的话"没释放"也测不出来。
@JdbcTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PgAdvisoryRunLockAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
@DisplayName("PgAdvisoryRunLockAdapter 集成测试")
class PgAdvisoryRunLockAdapterIT {

  private static final String LOCK = "catalog.test-lock";

  @Autowired private PgAdvisoryRunLockAdapter adapter;
  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("锁空闲时执行并返回结果")
  void should_run_when_lock_free() {
    assertThat(adapter.runExclusively(LOCK, () -> 42)).contains(42);
  }

  @Test
  @DisplayName("锁被另一个会话持有时不执行、返回空")
  void should_skip_when_lock_held_by_another_session() throws Exception {
    AtomicBoolean ran = new AtomicBoolean();
    try (Connection other = dataSource.getConnection()) {
      execute(other, "SELECT pg_advisory_lock(?)");

      assertThat(adapter.runExclusively(LOCK, () -> ran.getAndSet(true))).isEmpty();

      execute(other, "SELECT pg_advisory_unlock(?)");
    }
    assertThat(ran).isFalse();
  }

  @Test
  @DisplayName("工作抛异常时原样抛出，并且锁已释放")
  void should_release_lock_when_work_throws() throws Exception {
    assertThatThrownBy(
            () ->
                adapter.runExclusively(
                    LOCK,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    try (Connection other = dataSource.getConnection()) {
      assertThat(execute(other, "SELECT pg_try_advisory_lock(?)")).isTrue();
      execute(other, "SELECT pg_advisory_unlock(?)");
    }
  }

  private static boolean execute(Connection connection, String sql) throws Exception {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setLong(1, PgAdvisoryRunLockAdapter.lockKey(LOCK));
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        Object value = rs.getObject(1);
        return !(value instanceof Boolean b) || b;
      }
    }
  }
}
