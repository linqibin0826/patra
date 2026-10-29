package dev.patra.interaction.infra.subject.persistence;

import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SourceState;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// 登记表持久化集成测试的公共底座：真实 PG，每例前清空登记表；写操作包在事务里，模拟 app 层。
@SpringBootTest(classes = SubjectRegistryITBootstrap.class)
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
abstract class SubjectRegistryITSupport {

  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected TransactionTemplate transactions;

  /// 每例从空表开始。
  @BeforeEach
  void truncateRegistry() {
    jdbcTemplate.update("TRUNCATE subject.registry");
  }

  /// PMID 的身份锚。
  ///
  /// @param pmid PMID
  /// @return 身份锚
  protected static NaturalKey pmid(String pmid) {
    return NaturalKey.of(NaturalKeyScheme.PMID, pmid);
  }

  /// 正常的来源记录。
  ///
  /// @param sourceId 来源 ID
  /// @param pmid PMID
  /// @return 来源记录
  protected static SourceRecord active(long sourceId, String pmid) {
    return SourceRecord.of(
        sourceId,
        pmid(pmid),
        SourceState.ACTIVE,
        SubjectSnapshot.of("Title " + pmid, "Journal", "2019-03", null));
  }

  /// 登记表行数。
  ///
  /// @return 行数
  protected long rowCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM subject.registry", Long.class);
  }
}
