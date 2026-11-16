package dev.patra.tool.jpa.standard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// 库表规范推断 4：去掉 `@SoftDelete` 之后，JPQL 批量删除会从「标记删除」变成真删除。
///
/// catalog 原先有两条 JPQL 批量删除语句作用在 `@SoftDelete` 实体上。规范禁用这个注解后，
/// 如果这两条语句原样保留，就会把行物理删掉。这里确认映射注解时是「标记删除」、去掉注解后是真删除，
/// 所以那两条语句改写成了 `UPDATE … SET deleted_at`；保留这个测试，演示这个注解为什么被禁用。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("库表规范推断 4：JPQL 批量删除与 @SoftDelete")
class SoftDeleteBulkDeleteIT {

  @PersistenceContext private EntityManager em;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("映射了 @SoftDelete：JPQL 批量删除只写上 deleted_at，行还在")
  void should_mark_rows_deleted_when_soft_delete_is_mapped() {
    List<Long> ids = insertTwo("jpa_it_soft_deleted");

    int affected = bulkDelete("JpaITSoftDeletedEntity", ids);

    assertThat(affected).isEqualTo(2);
    assertThat(count("jpa_it_soft_deleted", ids, "TRUE")).isEqualTo(2);
    assertThat(count("jpa_it_soft_deleted", ids, "deleted_at IS NOT NULL")).isEqualTo(2);
  }

  @Test
  @DisplayName("没有 @SoftDelete：JPQL 批量删除把行物理删掉")
  void should_delete_rows_physically_when_soft_delete_is_not_mapped() {
    List<Long> ids = insertTwo("jpa_it_explicit_deleted");

    int affected = bulkDelete("JpaITExplicitDeletedEntity", ids);

    assertThat(affected).isEqualTo(2);
    assertThat(count("jpa_it_explicit_deleted", ids, "TRUE")).isZero();
  }

  private List<Long> insertTwo(String table) {
    List<Long> ids = List.of(SnowflakeIdGenerator.getId(), SnowflakeIdGenerator.getId());
    ids.forEach(id -> jdbc.update("INSERT INTO " + table + " (id, content) VALUES (?, 'a')", id));
    return ids;
  }

  private int bulkDelete(String entityName, List<Long> ids) {
    Integer affected =
        tx.execute(
            status ->
                em.createQuery("DELETE FROM " + entityName + " e WHERE e.id IN :ids")
                    .setParameter("ids", ids)
                    .executeUpdate());
    return affected == null ? -1 : affected;
  }

  private long count(String table, List<Long> ids, String condition) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE id = ANY(?) AND " + condition,
            Long.class,
            (Object) ids.toArray(Long[]::new));
    return count == null ? -1 : count;
  }
}
