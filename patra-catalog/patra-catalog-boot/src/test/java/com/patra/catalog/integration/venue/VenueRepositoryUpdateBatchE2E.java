package com.patra.catalog.integration.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.infra.persistence.mapper.VenueIdentifierMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMapper;
import com.patra.catalog.integration.config.CatalogMySQLContainerInitializer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// VenueRepository.updateBatch() E2E 测试。
///
/// 验证 `Db.saveBatch()` 和 `Db.updateBatchById()` 在完整 Spring Boot 环境下的事务行为。
///
/// ### 测试目的
///
/// 验证 MyBatis-Plus 的 `Db.saveBatch()` 和 `Db.updateBatchById()` 在 Spring 事务管理下的行为：
///
/// 1. **数据持久化**：批量操作能正确写入数据库
/// 2. **事务参与性**：在 `TransactionTemplate` 内执行时，批量操作参与 Spring 事务
/// 3. **回滚能力**：事务失败时，所有批量操作的数据都能正确回滚
///
/// ### 技术说明
///
/// **关于 `@MybatisPlusTest` 切片测试的限制**：
///
/// 在 `@MybatisPlusTest` 切片测试中，测试方法的 `@Transactional` 注解创建的事务
/// 与 `Db.saveBatch()` 内部的 BATCH SqlSession 之间存在隔离问题，导致：
/// - 批量插入的数据在事务提交前不可见
/// - 测试结束时的自动回滚无法清理批量操作的数据
///
/// **E2E 测试中的正确行为**：
///
/// 在完整 Spring Boot 环境下，当使用 `TransactionTemplate` 或 `@Transactional`
/// 包裹业务代码时，`Db.saveBatch()` 会参与该事务，因为：
/// - MyBatis-Spring 的 `SqlSessionTemplate` 会复用当前事务的 Connection
/// - 这确保了批量操作与其他数据库操作在同一个事务边界内
///
/// ### 测试场景
///
/// - 增量添加标识符（Db.saveBatch）
/// - 增量删除标识符（deleteByBusinessKeys）
/// - 更新主表字段（Db.updateBatchById）
/// - 混合变更（添加 + 删除 + 更新）
/// - 多聚合根批量操作
/// - **事务回滚**（验证异常时数据回滚）
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "spring.config.import=classpath:catalog-error-config.yaml"
    })
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@ActiveProfiles("e2e-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("VenueRepository.updateBatch() E2E 测试")
class VenueRepositoryUpdateBatchE2E {

  @Autowired private VenueRepository venueRepository;

  @Autowired private VenueMapper venueMapper;
  @Autowired private VenueIdentifierMapper venueIdentifierMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;

  /// 清空测试数据（使用 TRUNCATE 确保数据完全清除）。
  ///
  /// 使用 `SET FOREIGN_KEY_CHECKS=0` 临时禁用外键检查，然后 TRUNCATE 所有表。
  @BeforeEach
  void cleanupTestData() {
    // 禁用外键检查，使用 TRUNCATE 确保数据完全清除
    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
    jdbcTemplate.execute("TRUNCATE TABLE cat_venue_identifier");
    jdbcTemplate.execute("TRUNCATE TABLE cat_venue");
    jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
  }

  // ========== Db.saveBatch 测试 ==========

  @Nested
  @DisplayName("Db.saveBatch 事务测试")
  class DbSaveBatchTests {

    @Test
    @DisplayName("应该正确持久化新增的标识符")
    void shouldPersistNewIdentifiers() {
      // Given：插入一个载体（初始只有 OpenAlex ID 一个标识符）
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venueRepository.insertAll(List.of(venue));

      // When：重新加载并添加新标识符
      var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      int initialCount = loaded.getIdentifiers().size();
      loaded.addIdentifier(VenueIdentifier.forIssn("9999-9999"));
      venueRepository.updateBatch(List.of(loaded));

      // Then：重新加载聚合根，验证标识符已持久化
      var reloaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      assertThat(reloaded.getIdentifiers()).hasSize(initialCount + 1);
      assertThat(reloaded.getIdentifiers(VenueIdentifierType.ISSN)).contains("9999-9999");
    }

    @Test
    @DisplayName("应该正确持久化批量新增的标识符")
    void shouldPersistBatchNewIdentifiers() {
      // Given：插入一个载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venueRepository.insertAll(List.of(venue));

      // When：重新加载并添加多个标识符
      var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      int initialCount = loaded.getIdentifiers().size();
      loaded.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      loaded.addIdentifier(VenueIdentifier.forIssn("2222-2222"));
      loaded.addIdentifier(VenueIdentifier.forIssn("3333-3333"));
      venueRepository.updateBatch(List.of(loaded));

      // Then：验证所有标识符已持久化
      var reloaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      assertThat(reloaded.getIdentifiers()).hasSize(initialCount + 3);
      assertThat(reloaded.getIdentifiers(VenueIdentifierType.ISSN))
          .containsExactlyInAnyOrder("1111-1111", "2222-2222", "3333-3333");
    }
  }

  // ========== 增量删除测试 ==========

  @Nested
  @DisplayName("增量删除标识符测试")
  class IncrementalDeleteTests {

    @Test
    @DisplayName("应该正确删除标识符")
    void shouldDeleteIdentifiers() {
      // Given：插入一个带多个标识符的载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venue.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      venue.addIdentifier(VenueIdentifier.forIssn("2222-2222"));
      venueRepository.insertAll(List.of(venue));

      // When：重新加载并删除一个标识符
      var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      int initialCount = loaded.getIdentifiers().size();
      loaded.removeIdentifier(VenueIdentifierType.ISSN, "1111-1111");
      venueRepository.updateBatch(List.of(loaded));

      // Then：验证标识符已删除
      var reloaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      assertThat(reloaded.getIdentifiers()).hasSize(initialCount - 1);
      assertThat(reloaded.getIdentifiers(VenueIdentifierType.ISSN))
          .contains("2222-2222")
          .doesNotContain("1111-1111");
    }
  }

  // ========== 混合变更测试 ==========

  @Nested
  @DisplayName("混合变更测试")
  class MixedChangesTests {

    @Test
    @DisplayName("应该正确处理添加和删除标识符的混合操作")
    void shouldHandleMixedChanges() {
      // Given：插入一个带标识符的载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venue.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      venue.addIdentifier(VenueIdentifier.forIssn("2222-2222"));
      venueRepository.insertAll(List.of(venue));

      // When：执行混合操作（CQRS 最小聚合：只测试标识符变更）
      var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      // 删除一个标识符
      loaded.removeIdentifier(VenueIdentifierType.ISSN, "1111-1111");
      // 添加一个新标识符
      loaded.addIdentifier(VenueIdentifier.forIssn("3333-3333"));
      venueRepository.updateBatch(List.of(loaded));

      // Then：验证标识符变更
      var reloaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      List<String> issns = reloaded.getIdentifiers(VenueIdentifierType.ISSN);
      assertThat(issns).containsExactlyInAnyOrder("2222-2222", "3333-3333");
      assertThat(issns).doesNotContain("1111-1111");
    }
  }

  // ========== 多聚合根批量操作测试 ==========

  @Nested
  @DisplayName("多聚合根批量操作测试")
  class MultipleAggregatesTests {

    @Test
    @DisplayName("多个聚合根的批量标识符操作应该全部成功")
    void shouldHandleMultipleAggregates() {
      // Given：插入多个载体
      VenueAggregate venue1 = createVenueAggregate("S1", "Journal A");
      VenueAggregate venue2 = createVenueAggregate("S2", "Journal B");
      VenueAggregate venue3 = createVenueAggregate("S3", "Journal C");
      venueRepository.insertAll(List.of(venue1, venue2, venue3));

      // When：分别修改三个聚合根的标识符
      var loaded1 = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      var loaded2 = venueRepository.findByIssnLs(Set.of("1234-S2")).get("1234-S2");
      var loaded3 = venueRepository.findByIssnLs(Set.of("1234-S3")).get("1234-S3");

      loaded1.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      loaded2.addIdentifier(VenueIdentifier.forIssn("2222-2222"));
      loaded3.addIdentifier(VenueIdentifier.forIssn("3333-3333"));

      // 批量更新
      venueRepository.updateBatch(List.of(loaded1, loaded2, loaded3));

      // Then：验证标识符变更
      var results = venueRepository.findByIssnLs(Set.of("1234-S1", "1234-S2", "1234-S3"));

      assertThat(results.get("1234-S1").getIdentifiers(VenueIdentifierType.ISSN))
          .contains("1111-1111");
      assertThat(results.get("1234-S2").getIdentifiers(VenueIdentifierType.ISSN))
          .contains("2222-2222");
      assertThat(results.get("1234-S3").getIdentifiers(VenueIdentifierType.ISSN))
          .contains("3333-3333");
    }

    @Test
    @DisplayName("无变更时不应修改任何数据")
    void shouldNotModifyWhenNoChanges() {
      // Given：插入一个载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venueRepository.insertAll(List.of(venue));

      // When：重新加载但不做任何修改
      var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      int initialCount = loaded.getIdentifiers().size();
      String initialDisplayName = loaded.getDisplayName();
      // 不调用任何修改方法
      venueRepository.updateBatch(List.of(loaded));

      // Then：验证数据未变
      var reloaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
      assertThat(reloaded.getIdentifiers()).hasSize(initialCount);
      assertThat(reloaded.getDisplayName()).isEqualTo(initialDisplayName);
    }
  }

  // ========== 事务回滚测试 ==========

  /// 事务回滚测试。
  ///
  /// **核心问题验证**：`Db.saveBatch()` 使用独立的 BATCH SqlSession，
  /// 根据 MyBatis-Plus 官方文档，默认不参与 Spring 事务管理。
  ///
  /// 本测试验证：在 Spring 事务内执行 `updateBatch()` 后抛出异常，
  /// 数据是否正确回滚。
  @Nested
  @DisplayName("事务回滚测试")
  class TransactionRollbackTests {

    @Test
    @DisplayName("Spring 事务内抛出异常时，Db.saveBatch() 的数据应该回滚")
    void shouldRollbackSaveBatchWhenExceptionThrown() {
      // Given：插入一个载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venueRepository.insertAll(List.of(venue));

      // 记录初始标识符数量
      int initialIdentifierCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
              Integer.class,
              venue.getId());

      // When：在事务内添加标识符，然后抛出异常
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
                        loaded.addIdentifier(VenueIdentifier.forIssn("9999-9999"));
                        venueRepository.updateBatch(List.of(loaded));

                        // 验证数据已写入（在事务提交前）
                        int countAfterInsert =
                            jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
                                Integer.class,
                                loaded.getId());
                        assertThat(countAfterInsert)
                            .as("事务内应能看到新增的标识符")
                            .isEqualTo(initialIdentifierCount + 1);

                        // 故意抛出异常触发回滚
                        throw new RuntimeException("故意抛出异常以测试回滚");
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("故意抛出异常以测试回滚");

      // Then：验证数据已回滚
      int finalCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
              Integer.class,
              venue.getId());
      assertThat(finalCount)
          .as("事务回滚后，标识符数量应恢复到初始值（如果此断言失败，说明 Db.saveBatch() 未参与 Spring 事务）")
          .isEqualTo(initialIdentifierCount);
    }

    @Test
    @DisplayName("Spring 事务内抛出异常时，标识符删除操作应该回滚")
    void shouldRollbackIdentifierDeletionWhenExceptionThrown() {
      // Given：插入一个带多个标识符的载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venue.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      venue.addIdentifier(VenueIdentifier.forIssn("2222-2222"));
      venueRepository.insertAll(List.of(venue));

      int initialIdentifierCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
              Integer.class,
              venue.getId());

      // When：在事务内删除标识符，然后抛出异常
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
                        loaded.removeIdentifier(VenueIdentifierType.ISSN, "1111-1111");
                        venueRepository.updateBatch(List.of(loaded));

                        // 验证数据已删除（在事务提交前）
                        int countAfterDelete =
                            jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
                                Integer.class,
                                loaded.getId());
                        assertThat(countAfterDelete)
                            .as("事务内应能看到标识符已删除")
                            .isEqualTo(initialIdentifierCount - 1);

                        // 故意抛出异常触发回滚
                        throw new RuntimeException("故意抛出异常以测试回滚");
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("故意抛出异常以测试回滚");

      // Then：验证数据已回滚
      int finalCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
              Integer.class,
              venue.getId());
      assertThat(finalCount)
          .as("事务回滚后，标识符数量应恢复到初始值（如果此断言失败，说明 deleteByBusinessKeys 未参与 Spring 事务）")
          .isEqualTo(initialIdentifierCount);
    }

    @Test
    @DisplayName("标识符添加和删除混合操作在异常时应全部回滚")
    void shouldRollbackAllIdentifierOperationsWhenExceptionThrown() {
      // Given：插入一个带标识符的载体
      VenueAggregate venue = createVenueAggregate("S1", "Journal A");
      venue.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      venueRepository.insertAll(List.of(venue));

      int initialIdentifierCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
              Integer.class,
              venue.getId());

      // When：在事务内执行混合操作（标识符删除 + 标识符添加），然后抛出异常
      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        var loaded = venueRepository.findByIssnLs(Set.of("1234-S1")).get("1234-S1");
                        // 删除标识符
                        loaded.removeIdentifier(VenueIdentifierType.ISSN, "1111-1111");
                        // 添加新标识符
                        loaded.addIdentifier(VenueIdentifier.forIssn("2222-2222"));
                        venueRepository.updateBatch(List.of(loaded));

                        // 故意抛出异常触发回滚
                        throw new RuntimeException("故意抛出异常以测试回滚");
                      }))
          .isInstanceOf(RuntimeException.class);

      // Then：验证所有操作都已回滚
      int finalIdentifierCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ?",
              Integer.class,
              venue.getId());
      boolean has1111 =
          jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ? AND identifier_value = '1111-1111'",
                  Integer.class,
                  venue.getId())
              > 0;
      boolean has2222 =
          jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM cat_venue_identifier WHERE venue_id = ? AND identifier_value = '2222-2222'",
                  Integer.class,
                  venue.getId())
              > 0;

      assertThat(finalIdentifierCount).as("标识符数量应恢复到初始值").isEqualTo(initialIdentifierCount);
      assertThat(has1111).as("被删除的标识符应恢复").isTrue();
      assertThat(has2222).as("新增的标识符不应存在").isFalse();
    }
  }

  // ========== 辅助方法 ==========

  /// 创建测试用的 VenueAggregate。
  private VenueAggregate createVenueAggregate(String openalexId, String displayName) {
    VenueAggregate venue = VenueAggregate.fromOpenAlex(openalexId, VenueType.JOURNAL, displayName);
    // ISSN-L 通过标识符添加
    venue.addIdentifier(VenueIdentifier.forIssnL("1234-" + openalexId));
    return venue;
  }
}
