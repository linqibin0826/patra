package dev.linqibin.patra.registry.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import dev.linqibin.patra.registry.domain.model.vo.expr.ApiParamMapping;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprCapability;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprField;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprRenderRule;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprSnapshot;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.expr.ExprFieldDictDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.expr.ProvApiParamMapDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.expr.ProvExprCapabilityDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.expr.ProvExprRenderRuleDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.provenance.ProvenanceDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr.ExprFieldDictEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr.ProvApiParamMapEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr.ProvExprCapabilityEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr.ProvExprRenderRuleEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance.ProvenanceEntity;
import dev.linqibin.patra.registry.infra.config.RegistryITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// ExprRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + PostgreSQL 17 测试表达式快照加载。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 PostgreSQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：loadSnapshot() 的各种场景
///
/// **重点测试场景**：
///
/// - 正常加载表达式快照（完整数据）
///   - 数据源代码未找到异常
///   - 操作类型键规范化（小写转大写、null/空白转 ALL）
///   - 端点名称规范化（小写转大写、空白转 null）
///   - 空结果场景（无匹配数据时返回空快照）
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = RegistryITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({ExprRepositoryAdapter.class, JpaAuditingConfig.class, HibernatePropertiesCustomizer.class})
@ComponentScan(
    basePackages = "dev.linqibin.patra.registry.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("ExprRepositoryAdapter 集成测试")
class ExprRepositoryAdapterIT {

  @Autowired private ExprRepositoryAdapter repository;

  @Autowired private ProvenanceDao provenanceDao;
  @Autowired private ExprFieldDictDao fieldDictDao;
  @Autowired private ProvExprCapabilityDao capabilityDao;
  @Autowired private ProvExprRenderRuleDao renderRuleDao;
  @Autowired private ProvApiParamMapDao apiParamMapDao;

  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_TYPE = "SEARCH";
  private static final String TEST_ENDPOINT_NAME = "ESEARCH";
  private static final Instant TEST_TIMESTAMP = Instant.parse("2025-01-15T00:00:00Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

  private Long testProvenanceId;

  @BeforeEach
  void setUp() {
    // 清理现有数据（按外键依赖顺序）
    apiParamMapDao.deleteAllInBatch();
    renderRuleDao.deleteAllInBatch();
    capabilityDao.deleteAllInBatch();
    fieldDictDao.deleteAllInBatch();
    provenanceDao.deleteAllInBatch();
  }

  @Nested
  @DisplayName("loadSnapshot 正常场景")
  class LoadSnapshotNormalTests {

    @Test
    @DisplayName("应成功加载完整的表达式元数据快照")
    void shouldLoadCompleteSnapshotSuccessfully() {
      // Given: 准备测试数据
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertFieldDict("author", "作者");
      insertCapability(testProvenanceId, TEST_OPERATION_TYPE, "title");
      insertRenderRule(testProvenanceId, TEST_OPERATION_TYPE, "title", "TERM", "{{value}}[Title]");
      insertApiParamMapping(testProvenanceId, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, "title");

      // When: 加载快照
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 验证结果
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).hasSize(2);
      assertThat(snapshot.fields())
          .extracting(ExprField::fieldKey)
          .containsExactlyInAnyOrder("title", "author");
      assertThat(snapshot.capabilities()).hasSize(1);
      assertThat(snapshot.capabilities())
          .extracting(ExprCapability::fieldKey)
          .containsExactly("title");
      assertThat(snapshot.renderRules()).hasSize(1);
      assertThat(snapshot.renderRules())
          .extracting(ExprRenderRule::fieldKey)
          .containsExactly("title");
      assertThat(snapshot.apiParamMappings()).hasSize(1);
      assertThat(snapshot.apiParamMappings())
          .extracting(ApiParamMapping::stdKey)
          .containsExactly("title");
    }

    @Test
    @DisplayName("应在 at 参数为 null 时使用当前时间")
    void shouldUseCurrentTimeWhenAtIsNull() {
      // Given: 准备测试数据（使用过去的生效时间）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertCapability(testProvenanceId, TEST_OPERATION_TYPE, "title");

      // When: at 参数为 null
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, null);

      // Then: 应成功加载（使用当前时间查询）
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).hasSize(1);
      assertThat(snapshot.capabilities()).hasSize(1);
    }

    @Test
    @DisplayName("应在 endpointName 为 null 时查询所有端点的映射")
    void shouldQueryAllEndpointsWhenEndpointNameIsNull() {
      // Given: 准备测试数据（API 参数映射不指定端点）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertApiParamMappingWithNullEndpoint(testProvenanceId, TEST_OPERATION_TYPE, "title");

      // When: endpointName 为 null
      ExprSnapshot snapshot =
          repository.loadSnapshot(ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, null, TEST_TIMESTAMP);

      // Then: 应返回无端点限制的映射
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.apiParamMappings()).hasSize(1);
    }

    @Test
    @DisplayName("应在 endpointName 为空白字符串时查询所有端点")
    void shouldQueryAllEndpointsWhenEndpointNameIsBlank() {
      // Given: 准备测试数据
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertApiParamMappingWithNullEndpoint(testProvenanceId, TEST_OPERATION_TYPE, "title");

      // When: endpointName 为空白字符串
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, "   ", TEST_TIMESTAMP);

      // Then: 应返回无端点限制的映射
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.apiParamMappings()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("操作类型键规范化场景")
  class OperationKeyNormalizationTests {

    @Test
    @DisplayName("应将小写操作类型转换为大写操作键")
    void shouldConvertLowercaseOperationTypeToUppercase() {
      // Given: 准备测试数据（操作类型为大写 SEARCH）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertCapability(testProvenanceId, "SEARCH", "title");

      // When: 传入小写操作类型 "search"
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, "search", TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应正确匹配大写操作类型
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.capabilities()).hasSize(1);
    }

    @Test
    @DisplayName("应将 null 操作类型转换为 ALL")
    void shouldConvertNullOperationTypeToAll() {
      // Given: 准备测试数据（操作类型为 ALL）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertCapability(testProvenanceId, "ALL", "title");

      // When: 传入 null 操作类型
      ExprSnapshot snapshot =
          repository.loadSnapshot(ProvenanceCode.PUBMED, null, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应正确匹配 ALL 操作类型
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.capabilities()).hasSize(1);
    }

    @Test
    @DisplayName("应将空白操作类型转换为 ALL")
    void shouldConvertBlankOperationTypeToAll() {
      // Given: 准备测试数据（操作类型为 ALL）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertCapability(testProvenanceId, "ALL", "title");

      // When: 传入空白操作类型
      ExprSnapshot snapshot =
          repository.loadSnapshot(ProvenanceCode.PUBMED, "   ", TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应正确匹配 ALL 操作类型
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.capabilities()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("API 参数映射查询")
  class ApiParamMapQueryTests {

    /// 仅返回生命周期为 ACTIVE 的映射。
    @Test
    @DisplayName("仅返回生命周期为 ACTIVE 的映射")
    void shouldReturnOnlyActiveMappings() {
      // Given
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertApiParamMappingWithStatus(
          testProvenanceId, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, "title", "ACTIVE");
      insertApiParamMappingWithStatus(
          testProvenanceId, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, "author", "INACTIVE");

      // When
      var result =
          apiParamMapDao.findActiveByTask(
              testProvenanceId, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result).extracting(ProvApiParamMapEntity::getStdKey).containsExactly("title");
    }
  }

  @Nested
  @DisplayName("端点名称规范化场景")
  class EndpointNameNormalizationTests {

    @Test
    @DisplayName("应将小写端点名称转换为大写")
    void shouldConvertLowercaseEndpointNameToUppercase() {
      // Given: 准备测试数据（端点名称为大写 ESEARCH）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertApiParamMapping(testProvenanceId, TEST_OPERATION_TYPE, "ESEARCH", "title");

      // When: 传入小写端点名称 "esearch"
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, "esearch", TEST_TIMESTAMP);

      // Then: 应正确匹配大写端点名称
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.apiParamMappings()).hasSize(1);
    }

    @Test
    @DisplayName("应将混合大小写端点名称转换为大写")
    void shouldConvertMixedCaseEndpointNameToUppercase() {
      // Given: 准备测试数据
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertApiParamMapping(testProvenanceId, TEST_OPERATION_TYPE, "ESEARCH", "title");

      // When: 传入混合大小写端点名称 "ESearch"
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, "ESearch", TEST_TIMESTAMP);

      // Then: 应正确匹配大写端点名称
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.apiParamMappings()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("异常场景")
  class ExceptionTests {

    @Test
    @DisplayName("应在数据源代码未找到时抛出 ProvenanceNotFoundException")
    void shouldThrowProvenanceNotFoundExceptionWhenCodeNotFound() {
      // Given: 数据库中没有对应的数据源记录

      // When & Then: 应抛出 ProvenanceNotFoundException
      assertThatThrownBy(
              () ->
                  repository.loadSnapshot(
                      ProvenanceCode.PUBMED,
                      TEST_OPERATION_TYPE,
                      TEST_ENDPOINT_NAME,
                      TEST_TIMESTAMP))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("Provenance code not found: PUBMED");
    }
  }

  @Nested
  @DisplayName("空结果场景")
  class EmptyResultTests {

    @Test
    @DisplayName("应在所有查询返回空列表时创建空快照")
    void shouldCreateEmptySnapshotWhenAllQueriesReturnEmpty() {
      // Given: 只插入数据源，不插入其他数据
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);

      // When
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应创建空快照
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).isEmpty();
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("应在只有字段字典有数据时创建部分快照")
    void shouldCreatePartialSnapshotWhenOnlyFieldsExist() {
      // Given: 只插入数据源和字段字典
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");

      // When
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应创建部分快照
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).hasSize(1);
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("应在时间戳早于生效时间时返回空快照")
    void shouldReturnEmptySnapshotWhenTimestampBeforeEffective() {
      // Given: 准备测试数据（生效时间为 2025-01-01）
      testProvenanceId = insertProvenance(TEST_PROVENANCE_CODE);
      insertFieldDict("title", "标题");
      insertCapability(testProvenanceId, TEST_OPERATION_TYPE, "title");

      // When: 使用早于生效时间的时间戳查询
      Instant earlyTimestamp = EFFECTIVE_FROM.minus(1, ChronoUnit.DAYS);
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              ProvenanceCode.PUBMED, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, earlyTimestamp);

      // Then: 能力等时态配置应为空（字段字典无时态限制，仍有数据）
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).hasSize(1); // 字段字典无时态限制
      assertThat(snapshot.capabilities()).isEmpty(); // 能力有时态限制
    }
  }

  // ==================== 辅助方法 ====================

  private Long insertProvenance(String code) {
    ProvenanceEntity provenance = new ProvenanceEntity();
    provenance.setId(SnowflakeIdGenerator.getId());
    provenance.setProvenanceCode(code);
    provenance.setProvenanceName("Test Provenance - " + code);
    provenance.setTimezoneDefault("UTC");
    provenance.setIsActive(true);
    provenance.setLifecycleStatusCode("ACTIVE");
    provenanceDao.saveAndFlush(provenance);
    return provenance.getId();
  }

  private void insertFieldDict(String fieldKey, String displayName) {
    ExprFieldDictEntity field = new ExprFieldDictEntity();
    field.setId(SnowflakeIdGenerator.getId());
    field.setFieldKey(fieldKey);
    field.setDisplayName(displayName);
    field.setDescription("Test field: " + fieldKey);
    field.setDataTypeCode("TEXT");
    field.setCardinalityCode("SINGLE");
    field.setExposable(true);
    field.setDateField(false);
    fieldDictDao.saveAndFlush(field);
  }

  private void insertCapability(Long provenanceId, String operationType, String fieldKey) {
    ProvExprCapabilityEntity capability = new ProvExprCapabilityEntity();
    capability.setId(SnowflakeIdGenerator.getId());
    capability.setProvenanceId(provenanceId);
    capability.setOperationType(operationType);
    capability.setFieldKey(fieldKey);
    capability.setLifecycleStatusCode("ACTIVE");
    capability.setEffectiveFrom(EFFECTIVE_FROM);
    capability.setEffectiveTo(null);
    capability.setOps(parseJson("[\"TERM\", \"IN\"]"));
    capability.setSupportsNot(true);
    capability.setTermCaseSensitiveAllowed(false);
    capability.setTermAllowBlank(false);
    capability.setTermMinLen(0);
    capability.setTermMaxLen(0);
    capability.setInMaxSize(0);
    capability.setInCaseSensitiveAllowed(false);
    capability.setRangeKindCode("NONE");
    capability.setRangeAllowOpenStart(true);
    capability.setRangeAllowOpenEnd(true);
    capability.setRangeAllowClosedAtInfty(false);
    capability.setExistsSupported(false);
    capabilityDao.saveAndFlush(capability);
  }

  /// 解析 JSON 字符串为 JsonNode。
  private JsonNode parseJson(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JSON: " + json, e);
    }
  }

  private void insertRenderRule(
      Long provenanceId, String operationType, String fieldKey, String opCode, String template) {
    ProvExprRenderRuleEntity renderRule = new ProvExprRenderRuleEntity();
    renderRule.setId(SnowflakeIdGenerator.getId());
    renderRule.setProvenanceId(provenanceId);
    renderRule.setOperationType(operationType);
    renderRule.setFieldKey(fieldKey);
    renderRule.setOpCode(opCode);
    renderRule.setLifecycleStatusCode("ACTIVE");
    renderRule.setEffectiveFrom(EFFECTIVE_FROM);
    renderRule.setEffectiveTo(null);
    renderRule.setTemplate(template);
    renderRule.setEmitTypeCode("QUERY");
    renderRule.setWrapGroup(false);
    renderRuleDao.saveAndFlush(renderRule);
  }

  private void insertApiParamMapping(
      Long provenanceId, String operationType, String endpointName, String stdKey) {
    ProvApiParamMapEntity apiParam = new ProvApiParamMapEntity();
    apiParam.setId(SnowflakeIdGenerator.getId());
    apiParam.setProvenanceId(provenanceId);
    apiParam.setOperationType(operationType);
    apiParam.setEndpointName(endpointName);
    apiParam.setStdKey(stdKey);
    apiParam.setProviderParamName(stdKey + "[Title]");
    apiParam.setLifecycleStatusCode("ACTIVE");
    apiParam.setEffectiveFrom(EFFECTIVE_FROM);
    apiParam.setEffectiveTo(null);
    apiParamMapDao.saveAndFlush(apiParam);
  }

  private void insertApiParamMappingWithNullEndpoint(
      Long provenanceId, String operationType, String stdKey) {
    ProvApiParamMapEntity apiParam = new ProvApiParamMapEntity();
    apiParam.setId(SnowflakeIdGenerator.getId());
    apiParam.setProvenanceId(provenanceId);
    apiParam.setOperationType(operationType);
    apiParam.setEndpointName(null);
    apiParam.setStdKey(stdKey);
    apiParam.setProviderParamName(stdKey + "[Title]");
    apiParam.setLifecycleStatusCode("ACTIVE");
    apiParam.setEffectiveFrom(EFFECTIVE_FROM);
    apiParam.setEffectiveTo(null);
    apiParamMapDao.saveAndFlush(apiParam);
  }

  /// 插入指定生命周期状态的 API 参数映射。
  private void insertApiParamMappingWithStatus(
      Long provenanceId,
      String operationType,
      String endpointName,
      String stdKey,
      String lifecycleStatusCode) {
    ProvApiParamMapEntity apiParam = new ProvApiParamMapEntity();
    apiParam.setId(SnowflakeIdGenerator.getId());
    apiParam.setProvenanceId(provenanceId);
    apiParam.setOperationType(operationType);
    apiParam.setEndpointName(endpointName);
    apiParam.setStdKey(stdKey);
    apiParam.setProviderParamName(stdKey + "[Title]");
    apiParam.setLifecycleStatusCode(lifecycleStatusCode);
    apiParam.setEffectiveFrom(EFFECTIVE_FROM);
    apiParam.setEffectiveTo(null);
    apiParamMapDao.saveAndFlush(apiParam);
  }
}
