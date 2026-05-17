package dev.linqibin.patra.registry.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import dev.linqibin.patra.registry.domain.model.vo.provenance.BatchingConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.HttpConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.PaginationConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.Provenance;
import dev.linqibin.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.RetryConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvBatchingCfgDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvHttpCfgDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvPaginationCfgDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvRateLimitCfgDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvRetryCfgDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvWindowOffsetCfgDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvenanceDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvBatchingCfgEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvHttpCfgEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvPaginationCfgEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvRateLimitCfgEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvRetryCfgEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvWindowOffsetCfgEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvenanceEntity;
import dev.linqibin.patra.registry.infra.config.RegistryPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// ProvenanceConfigRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + PostgreSQL 17 测试数据源配置查询。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 PostgreSQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：数据源查询、配置查询、完整配置聚合
///
/// **重点测试场景**：
///
/// - 数据源查询（按代码、所有激活）
///   - 各种配置查询（窗口偏移、分页、HTTP、批处理、重试、速率限制）
///   - 完整配置聚合（loadConfiguration）
///   - 时态切片（基于有效时间范围筛选配置）
///   - 操作类型键规范化
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = RegistryPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  ProvenanceConfigRepositoryAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.registry.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("ProvenanceConfigRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ProvenanceConfigRepositoryAdapterIT {

  @Autowired private ProvenanceConfigRepositoryAdapter repository;

  @Autowired private ProvenanceDao provenanceDao;
  @Autowired private ProvWindowOffsetCfgDao windowOffsetCfgDao;
  @Autowired private ProvPaginationCfgDao paginationCfgDao;
  @Autowired private ProvHttpCfgDao httpCfgDao;
  @Autowired private ProvBatchingCfgDao batchingCfgDao;
  @Autowired private ProvRetryCfgDao retryCfgDao;
  @Autowired private ProvRateLimitCfgDao rateLimitCfgDao;

  private static final String TEST_OPERATION_TYPE = "SEARCH";
  private static final Instant TEST_TIMESTAMP = Instant.parse("2025-01-15T00:00:00Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");

  private Long testProvenanceId;

  @BeforeEach
  void setUp() {
    // 清理现有数据（按外键依赖顺序）
    rateLimitCfgDao.deleteAllInBatch();
    retryCfgDao.deleteAllInBatch();
    batchingCfgDao.deleteAllInBatch();
    httpCfgDao.deleteAllInBatch();
    paginationCfgDao.deleteAllInBatch();
    windowOffsetCfgDao.deleteAllInBatch();
    provenanceDao.deleteAllInBatch();
  }

  @Nested
  @DisplayName("findProvenanceByCode 场景")
  class FindProvenanceByCodeTests {

    @Test
    @DisplayName("应成功查找数据源")
    void shouldFindProvenanceSuccessfully() {
      // Given
      insertProvenance("PUBMED", "PubMed", true);

      // When
      Optional<Provenance> result = repository.findProvenanceByCode(ProvenanceCode.PUBMED);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().code()).isEqualTo("PUBMED");
      assertThat(result.get().name()).isEqualTo("PubMed");
    }

    @Test
    @DisplayName("应在数据源不存在时返回空 Optional")
    void shouldReturnEmptyWhenProvenanceNotFound() {
      // Given: 数据库为空

      // When
      Optional<Provenance> result = repository.findProvenanceByCode(ProvenanceCode.PUBMED);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findAllProvenances 场景")
  class FindAllProvenancesTests {

    @Test
    @DisplayName("应成功查询所有激活的数据源")
    void shouldFindAllActiveProvenances() {
      // Given
      insertProvenance("PUBMED", "PubMed", true);
      insertProvenance("EPMC", "Europe PMC", true);
      insertProvenance("CROSSREF", "Crossref", false); // 非激活

      // When
      List<Provenance> result = repository.findAllProvenances();

      // Then
      assertThat(result).hasSize(2);
      assertThat(result).extracting(Provenance::code).containsExactlyInAnyOrder("PUBMED", "EPMC");
    }

    @Test
    @DisplayName("应在没有激活数据源时返回空列表")
    void shouldReturnEmptyListWhenNoActiveProvenances() {
      // Given: 插入非激活数据源
      insertProvenance("PUBMED", "PubMed", false);

      // When
      List<Provenance> result = repository.findAllProvenances();

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveWindowOffset 场景")
  class FindActiveWindowOffsetTests {

    @Test
    @DisplayName("应成功查询窗口偏移配置")
    void shouldFindWindowOffsetSuccessfully() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().windowSizeValue()).isEqualTo(30);
    }

    @Test
    @DisplayName("应在时间戳早于生效时间时返回空 Optional")
    void shouldReturnEmptyWhenTimestampBeforeEffective() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When: 使用早于生效时间的时间戳
      Instant earlyTimestamp = EFFECTIVE_FROM.minus(1, ChronoUnit.DAYS);
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(testProvenanceId, TEST_OPERATION_TYPE, earlyTimestamp);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应将 null 操作类型转换为 ALL")
    void shouldConvertNullOperationTypeToAll() {
      // Given: 插入 ALL 操作类型的配置
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, "ALL");

      // When: 传入 null 操作类型
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(testProvenanceId, null, TEST_TIMESTAMP);

      // Then: 应匹配 ALL 配置
      assertThat(result).isPresent();
    }

    @Test
    @DisplayName("应将小写操作类型转换为大写")
    void shouldConvertLowercaseOperationTypeToUppercase() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, "SEARCH");

      // When: 传入小写操作类型
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(testProvenanceId, "search", TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
    }
  }

  @Nested
  @DisplayName("loadConfiguration 完整聚合场景")
  class LoadConfigurationTests {

    @Test
    @DisplayName("应成功加载完整配置聚合")
    void shouldLoadCompleteConfigurationSuccessfully() {
      // Given: 插入所有配置
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, TEST_OPERATION_TYPE);
      insertPaginationConfig(testProvenanceId, TEST_OPERATION_TYPE);
      insertHttpConfig(testProvenanceId, TEST_OPERATION_TYPE);
      insertBatchingConfig(testProvenanceId, TEST_OPERATION_TYPE);
      insertRetryConfig(testProvenanceId, TEST_OPERATION_TYPE);
      insertRateLimitConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      ProvenanceConfiguration config = result.get();
      assertThat(config.provenance()).isNotNull();
      assertThat(config.provenance().code()).isEqualTo("PUBMED");
      assertThat(config.windowOffset()).isNotNull();
      assertThat(config.pagination()).isNotNull();
      assertThat(config.http()).isNotNull();
      assertThat(config.batching()).isNotNull();
      assertThat(config.retry()).isNotNull();
      assertThat(config.rateLimit()).isNotNull();
    }

    @Test
    @DisplayName("应在数据源不存在时返回空 Optional")
    void shouldReturnEmptyWhenProvenanceNotFound() {
      // Given: 不插入任何数据

      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(999L, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应在部分配置缺失时创建部分聚合")
    void shouldCreatePartialConfigurationWhenSomeConfigsMissing() {
      // Given: 只插入数据源和窗口偏移配置
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      ProvenanceConfiguration config = result.get();
      assertThat(config.provenance()).isNotNull();
      assertThat(config.windowOffset()).isNotNull();
      assertThat(config.pagination()).isNull();
      assertThat(config.http()).isNull();
      assertThat(config.batching()).isNull();
      assertThat(config.retry()).isNull();
      assertThat(config.rateLimit()).isNull();
    }

    @Test
    @DisplayName("应在 at 参数为 null 时使用当前时间")
    void shouldUseCurrentTimeWhenAtIsNull() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When: at 参数为 null
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(testProvenanceId, TEST_OPERATION_TYPE, null);

      // Then: 应使用当前时间成功加载
      assertThat(result).isPresent();
    }

    @Test
    @DisplayName("应在 provenanceId 为 null 时返回空 Optional")
    void shouldReturnEmptyWhenProvenanceIdIsNull() {
      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(null, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("时态切片场景")
  class TemporalSlicingTests {

    @Test
    @DisplayName("应在配置过期后返回空 Optional")
    void shouldReturnEmptyWhenConfigExpired() {
      // Given: 插入已过期的配置
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfigWithExpiry(
          testProvenanceId,
          TEST_OPERATION_TYPE,
          EFFECTIVE_FROM,
          EFFECTIVE_FROM.plus(7, ChronoUnit.DAYS));

      // When: 使用过期后的时间戳查询
      Instant afterExpiry = EFFECTIVE_FROM.plus(10, ChronoUnit.DAYS);
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(testProvenanceId, TEST_OPERATION_TYPE, afterExpiry);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应在配置有效期内返回配置")
    void shouldReturnConfigWhenWithinValidPeriod() {
      // Given: 插入有限期的配置
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertWindowOffsetConfigWithExpiry(
          testProvenanceId,
          TEST_OPERATION_TYPE,
          EFFECTIVE_FROM,
          EFFECTIVE_FROM.plus(30, ChronoUnit.DAYS));

      // When: 使用有效期内的时间戳查询
      Instant withinPeriod = EFFECTIVE_FROM.plus(15, ChronoUnit.DAYS);
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(testProvenanceId, TEST_OPERATION_TYPE, withinPeriod);

      // Then
      assertThat(result).isPresent();
    }
  }

  @Nested
  @DisplayName("各配置类型查询场景")
  class IndividualConfigQueryTests {

    @Test
    @DisplayName("应成功查询分页配置")
    void shouldFindPaginationSuccessfully() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertPaginationConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<PaginationConfig> result =
          repository.findActivePagination(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().pageSizeValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("应成功查询 HTTP 配置")
    void shouldFindHttpConfigSuccessfully() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertHttpConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<HttpConfig> result =
          repository.findActiveHttpConfig(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().timeoutConnectMillis()).isEqualTo(30000);
    }

    @Test
    @DisplayName("应成功查询批处理配置")
    void shouldFindBatchingSuccessfully() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertBatchingConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<BatchingConfig> result =
          repository.findActiveBatching(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().detailFetchBatchSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("应成功查询重试配置")
    void shouldFindRetrySuccessfully() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertRetryConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<RetryConfig> result =
          repository.findActiveRetry(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().maxRetryTimes()).isEqualTo(3);
    }

    @Test
    @DisplayName("应成功查询速率限制配置")
    void shouldFindRateLimitSuccessfully() {
      // Given
      testProvenanceId = insertProvenance("PUBMED", "PubMed", true);
      insertRateLimitConfig(testProvenanceId, TEST_OPERATION_TYPE);

      // When
      Optional<RateLimitConfig> result =
          repository.findActiveRateLimit(testProvenanceId, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().maxConcurrentRequests()).isEqualTo(10);
    }
  }

  // ==================== 辅助方法 ====================

  private Long insertProvenance(String code, String name, boolean isActive) {
    ProvenanceEntity provenance = new ProvenanceEntity();
    provenance.setId(SnowflakeIdGenerator.getId());
    provenance.setProvenanceCode(code);
    provenance.setProvenanceName(name);
    provenance.setTimezoneDefault("UTC");
    provenance.setIsActive(isActive);
    provenance.setLifecycleStatusCode("ACTIVE");
    provenanceDao.saveAndFlush(provenance);
    return provenance.getId();
  }

  private void insertWindowOffsetConfig(Long provenanceId, String operationType) {
    insertWindowOffsetConfigWithExpiry(provenanceId, operationType, EFFECTIVE_FROM, null);
  }

  private void insertWindowOffsetConfigWithExpiry(
      Long provenanceId, String operationType, Instant effectiveFrom, Instant effectiveTo) {
    ProvWindowOffsetCfgEntity cfg = new ProvWindowOffsetCfgEntity();
    cfg.setId(SnowflakeIdGenerator.getId());
    cfg.setProvenanceId(provenanceId);
    cfg.setOperationType(operationType);
    cfg.setEffectiveFrom(effectiveFrom);
    cfg.setEffectiveTo(effectiveTo);
    cfg.setWindowModeCode("SLIDING");
    cfg.setWindowSizeValue(30);
    cfg.setWindowSizeUnitCode("DAY");
    cfg.setLookbackValue(7);
    cfg.setLookbackUnitCode("DAY");
    cfg.setOffsetTypeCode("DATE");
    cfg.setOffsetFieldKey("PDAT"); // DATE 类型必须提供 offsetFieldKey 或 windowDateFieldKey
    cfg.setLifecycleStatusCode("ACTIVE");
    windowOffsetCfgDao.saveAndFlush(cfg);
  }

  private void insertPaginationConfig(Long provenanceId, String operationType) {
    ProvPaginationCfgEntity cfg = new ProvPaginationCfgEntity();
    cfg.setId(SnowflakeIdGenerator.getId());
    cfg.setProvenanceId(provenanceId);
    cfg.setOperationType(operationType);
    cfg.setEffectiveFrom(EFFECTIVE_FROM);
    cfg.setEffectiveTo(null);
    cfg.setPaginationModeCode("PAGE_NUMBER");
    cfg.setPageSizeValue(100);
    cfg.setMaxPagesPerExecution(1000);
    cfg.setSortingDirection(true);
    cfg.setLifecycleStatusCode("ACTIVE");
    paginationCfgDao.saveAndFlush(cfg);
  }

  private void insertHttpConfig(Long provenanceId, String operationType) {
    ProvHttpCfgEntity cfg = new ProvHttpCfgEntity();
    cfg.setId(SnowflakeIdGenerator.getId());
    cfg.setProvenanceId(provenanceId);
    cfg.setOperationType(operationType);
    cfg.setEffectiveFrom(EFFECTIVE_FROM);
    cfg.setEffectiveTo(null);
    cfg.setTimeoutConnectMillis(30000);
    cfg.setTimeoutReadMillis(60000);
    cfg.setTimeoutTotalMillis(300000);
    cfg.setTlsVerifyEnabled(true);
    cfg.setRetryAfterPolicyCode("RESPECT");
    cfg.setLifecycleStatusCode("ACTIVE");
    httpCfgDao.saveAndFlush(cfg);
  }

  private void insertBatchingConfig(Long provenanceId, String operationType) {
    ProvBatchingCfgEntity cfg = new ProvBatchingCfgEntity();
    cfg.setId(SnowflakeIdGenerator.getId());
    cfg.setProvenanceId(provenanceId);
    cfg.setOperationType(operationType);
    cfg.setEffectiveFrom(EFFECTIVE_FROM);
    cfg.setEffectiveTo(null);
    cfg.setDetailFetchBatchSize(50);
    cfg.setIdsParamName("id");
    cfg.setIdsJoinDelimiter(",");
    cfg.setMaxIdsPerRequest(200);
    cfg.setLifecycleStatusCode("ACTIVE");
    batchingCfgDao.saveAndFlush(cfg);
  }

  private void insertRetryConfig(Long provenanceId, String operationType) {
    ProvRetryCfgEntity cfg = new ProvRetryCfgEntity();
    cfg.setId(SnowflakeIdGenerator.getId());
    cfg.setProvenanceId(provenanceId);
    cfg.setOperationType(operationType);
    cfg.setEffectiveFrom(EFFECTIVE_FROM);
    cfg.setEffectiveTo(null);
    cfg.setMaxRetryTimes(3);
    cfg.setBackoffPolicyTypeCode("EXPONENTIAL");
    cfg.setInitialDelayMillis(1000);
    cfg.setMaxDelayMillis(30000);
    cfg.setExpMultiplierValue(2.0);
    cfg.setRetryOnNetworkError(true);
    cfg.setLifecycleStatusCode("ACTIVE");
    retryCfgDao.saveAndFlush(cfg);
  }

  private void insertRateLimitConfig(Long provenanceId, String operationType) {
    ProvRateLimitCfgEntity cfg = new ProvRateLimitCfgEntity();
    cfg.setId(SnowflakeIdGenerator.getId());
    cfg.setProvenanceId(provenanceId);
    cfg.setOperationType(operationType);
    cfg.setEffectiveFrom(EFFECTIVE_FROM);
    cfg.setEffectiveTo(null);
    cfg.setMaxConcurrentRequests(10);
    cfg.setPerCredentialQpsLimit(3);
    cfg.setLifecycleStatusCode("ACTIVE");
    rateLimitCfgDao.saveAndFlush(cfg);
  }
}
