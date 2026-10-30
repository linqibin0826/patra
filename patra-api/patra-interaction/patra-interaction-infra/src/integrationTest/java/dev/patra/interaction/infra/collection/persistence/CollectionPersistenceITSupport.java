package dev.patra.interaction.infra.collection.persistence;

import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// collection 持久化集成测试的公共底座：真实 PG，每例前清空三张表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = CollectionPersistenceITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class CollectionPersistenceITSupport {

  /// 用户 U。
  protected static final long U = 1001L;

  /// 用户 V。
  protected static final long V = 1002L;

  /// 对象。
  protected static final SubjectId S1 = SubjectId.of(11L);

  /// 对象。
  protected static final SubjectId S2 = SubjectId.of(12L);

  /// 对象。
  protected static final SubjectId S3 = SubjectId.of(13L);

  /// 时刻。
  protected static final Instant T0 = Instant.parse("2026-10-24T01:00:00Z");

  /// 时刻。
  protected static final Instant T1 = Instant.parse("2026-10-24T02:00:00Z");

  /// 时刻。
  protected static final Instant T2 = Instant.parse("2026-10-24T03:00:00Z");

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncateCollectionTables() {
    jdbcTemplate.update(
        "TRUNCATE collection.library_item, collection.collection, collection.collection_item,"
            + " collection.subject_stats");
  }

  /// 在事务里执行。
  ///
  /// @param work 写操作
  /// @param <T> 结果类型
  /// @return 结果
  protected <T> T tx(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }

  /// 在事务里执行，无返回值。
  ///
  /// @param work 写操作
  protected void txRun(Runnable work) {
    transactions.executeWithoutResult(status -> work.run());
  }

  /// 名称。
  ///
  /// @param name 原文
  /// @return 名称
  protected static CollectionName name(String name) {
    return CollectionName.parse(name);
  }

  /// 表行数。
  ///
  /// @param table 带 schema 的表名
  /// @return 行数
  protected long count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }
}
