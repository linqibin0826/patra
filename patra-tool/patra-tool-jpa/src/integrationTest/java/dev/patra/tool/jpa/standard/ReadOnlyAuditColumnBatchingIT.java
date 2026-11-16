package dev.patra.tool.jpa.standard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import dev.patra.tool.jpa.support.JpaITGeneratedAuditEntity;
import dev.patra.tool.jpa.support.JpaITReadOnlyAuditEntity;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// 验证库表规范依赖的一个前提（设计文档推断 2）：审计时间做只读映射时，Hibernate 的批量写入仍然生效。
///
/// 判断依据是 Hibernate 统计里「准备过几条语句」：同一种语句走批量时只准备一次再反复执行，
/// 逐条执行时每行准备一次。对照组用 `@Generated`，读源码看到它会让批量退化成逐条执行。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("库表规范推断 2：审计时间只读映射与批量写入")
class ReadOnlyAuditColumnBatchingIT {

  private static final int ROWS = 10;

  @PersistenceContext private EntityManager em;
  @Autowired private EntityManagerFactory emf;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("只读映射：插入 10 行只准备 1 条语句，两个时间由数据库填上")
  void should_batch_inserts_when_audit_times_are_read_only() {
    List<Long> ids = newIds();
    statistics().clear();

    tx.executeWithoutResult(status -> ids.forEach(id -> em.persist(readOnly(id, "a"))));

    assertThat(statistics().getPrepareStatementCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM jpa_it_readonly_audit"
                    + " WHERE id = ANY(?) AND created_at IS NOT NULL AND updated_at IS NOT NULL",
                Long.class,
                (Object) ids.toArray(Long[]::new)))
        .isEqualTo(ROWS);
  }

  @Test
  @DisplayName("只读映射：更新 10 行时，查询 1 条加批量更新 1 条")
  void should_batch_updates_when_audit_times_are_read_only() {
    List<Long> ids = newIds();
    tx.executeWithoutResult(status -> ids.forEach(id -> em.persist(readOnly(id, "a"))));
    statistics().clear();

    tx.executeWithoutResult(
        status ->
            em.createQuery(
                    "SELECT e FROM JpaITReadOnlyAuditEntity e WHERE e.id IN :ids",
                    JpaITReadOnlyAuditEntity.class)
                .setParameter("ids", ids)
                .getResultList()
                .forEach(entity -> entity.setContent("b")));

    assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("对照 @Generated：插入 10 行至少准备 10 条语句，批量失效")
  void should_insert_row_by_row_when_audit_times_use_generated() {
    List<Long> ids = newIds();
    statistics().clear();

    tx.executeWithoutResult(status -> ids.forEach(id -> em.persist(generated(id))));

    assertThat(statistics().getPrepareStatementCount()).isGreaterThanOrEqualTo(ROWS);
  }

  private Statistics statistics() {
    return emf.unwrap(SessionFactory.class).getStatistics();
  }

  private static List<Long> newIds() {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < ROWS; i++) {
      ids.add(SnowflakeIdGenerator.getId());
    }
    return ids;
  }

  private static JpaITReadOnlyAuditEntity readOnly(Long id, String content) {
    JpaITReadOnlyAuditEntity entity = new JpaITReadOnlyAuditEntity();
    entity.setId(id);
    entity.setContent(content);
    return entity;
  }

  private static JpaITGeneratedAuditEntity generated(Long id) {
    JpaITGeneratedAuditEntity entity = new JpaITGeneratedAuditEntity();
    entity.setId(id);
    entity.setContent("a");
    return entity;
  }
}
