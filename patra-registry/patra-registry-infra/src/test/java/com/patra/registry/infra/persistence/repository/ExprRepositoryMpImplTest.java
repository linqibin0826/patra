package com.patra.registry.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import com.patra.registry.domain.model.vo.expr.*;
import com.patra.registry.infra.persistence.converter.ExprEntityConverter;
import com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ExprRepositoryMpImpl 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 Mockito Mock 所有依赖 (Mapper, Converter)
 *   <li>不启动 Spring 容器，纯单元测试
 *   <li>验证方法调用、参数传递和返回值转换
 * </ul>
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>正常加载表达式快照 (loadSnapshot)
 *   <li>处理 null 参数 (endpointName, at)
 *   <li>数据源代码未找到异常
 *   <li>操作类型键规范化 (operationType → operationKey)
 *   <li>端点名称规范化 (endpointName → normalizedEndpoint)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExprRepositoryMpImpl 单元测试")
class ExprRepositoryMpImplTest {

  @Mock private RegExprFieldDictMapper fieldDictMapper;
  @Mock private RegProvApiParamMapMapper apiParamMapMapper;
  @Mock private RegProvExprCapabilityMapper capabilityMapper;
  @Mock private RegProvExprRenderRuleMapper renderRuleMapper;
  @Mock private RegProvenanceMapper provenanceMapper;
  @Mock private ExprEntityConverter converter;

  @InjectMocks private ExprRepositoryMpImpl repository;

  private static final ProvenanceCode TEST_PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final Long TEST_PROVENANCE_ID = 1L;
  private static final String TEST_OPERATION_TYPE = "search";
  private static final String TEST_OPERATION_KEY = "SEARCH";
  private static final String TEST_ENDPOINT_NAME = "esearch";
  private static final Instant TEST_TIMESTAMP = Instant.parse("2025-01-01T00:00:00Z");

  @Nested
  @DisplayName("loadSnapshot 正常场景")
  class LoadSnapshotNormalTests {

    @Test
    @DisplayName("应成功加载完整的表达式元数据快照")
    void shouldLoadCompleteSnapshotSuccessfully() {
      // Given: Mock 数据源查询
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));

      // Mock 字段字典
      List<RegExprFieldDictDO> fieldDOs =
          List.of(createFieldDictDO("title"), createFieldDictDO("author"));
      when(fieldDictMapper.selectAllActive()).thenReturn(fieldDOs);
      ExprField field1 = org.mockito.Mockito.mock(ExprField.class);
      ExprField field2 = org.mockito.Mockito.mock(ExprField.class);
      when(converter.toDomain(fieldDOs.get(0))).thenReturn(field1);
      when(converter.toDomain(fieldDOs.get(1))).thenReturn(field2);

      // Mock 能力查询
      List<RegProvExprCapabilityDO> capabilityDOs = List.of(createCapabilityDO("title", true));
      when(capabilityMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(capabilityDOs);
      ExprCapability capability = org.mockito.Mockito.mock(ExprCapability.class);
      when(converter.toDomain(capabilityDOs.get(0))).thenReturn(capability);

      // Mock 渲染规则
      List<RegProvExprRenderRuleDO> renderRuleDOs =
          List.of(createRenderRuleDO("title", "{{value}}"));
      when(renderRuleMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(renderRuleDOs);
      ExprRenderRule renderRule = org.mockito.Mockito.mock(ExprRenderRule.class);
      when(converter.toDomain(renderRuleDOs.get(0))).thenReturn(renderRule);

      // Mock API 参数映射
      List<RegProvApiParamMapDO> apiParamDOs =
          List.of(createApiParamMapDO("title", "title[Title]"));
      when(apiParamMapMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, "ESEARCH", TEST_TIMESTAMP))
          .thenReturn(apiParamDOs);
      ApiParamMapping apiParam = org.mockito.Mockito.mock(ApiParamMapping.class);
      when(converter.toDomain(apiParamDOs.get(0))).thenReturn(apiParam);

      // When: 加载快照
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 验证结果
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).hasSize(2).containsExactly(field1, field2);
      assertThat(snapshot.capabilities()).hasSize(1).containsExactly(capability);
      assertThat(snapshot.renderRules()).hasSize(1).containsExactly(renderRule);
      assertThat(snapshot.apiParamMappings()).hasSize(1).containsExactly(apiParam);

      // 验证方法调用
      verify(provenanceMapper).selectByCode("PUBMED");
      verify(fieldDictMapper).selectAllActive();
      verify(capabilityMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(renderRuleMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, "ESEARCH", TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应在 at 参数为 null 时使用当前时间")
    void shouldUseCurrentTimeWhenAtIsNull() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
          .thenReturn(List.of());

      // When: at 参数为 null
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, null);

      // Then: 应使用当前时间 (验证调用了带 Instant 参数的方法)
      assertThat(snapshot).isNotNull();
      verify(capabilityMapper)
          .selectActiveByTask(eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class));
      verify(renderRuleMapper)
          .selectActiveByTask(eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class));
      verify(apiParamMapMapper)
          .selectActiveByTask(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), eq("ESEARCH"), any(Instant.class));
    }

    @Test
    @DisplayName("应在 endpointName 为 null 时传递 null 给 API 参数映射查询")
    void shouldPassNullToApiParamMapperWhenEndpointNameIsNull() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, null, TEST_TIMESTAMP))
          .thenReturn(List.of());

      // When: endpointName 为 null
      ExprSnapshot snapshot =
          repository.loadSnapshot(TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, null, TEST_TIMESTAMP);

      // Then: 应传递 null
      assertThat(snapshot).isNotNull();
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, null, TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应在 endpointName 为空白字符串时传递 null")
    void shouldPassNullWhenEndpointNameIsBlank() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, null, TEST_TIMESTAMP))
          .thenReturn(List.of());

      // When: endpointName 为空白字符串
      ExprSnapshot snapshot =
          repository.loadSnapshot(TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, "   ", TEST_TIMESTAMP);

      // Then: 应传递 null
      assertThat(snapshot).isNotNull();
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, null, TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("操作类型键规范化场景")
  class OperationKeyNormalizationTests {

    @Test
    @DisplayName("应将小写操作类型转换为大写操作键")
    void shouldConvertLowercaseOperationTypeToUppercase() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
          .thenReturn(List.of());

      // When: 传入小写操作类型 "search"
      repository.loadSnapshot(TEST_PROVENANCE_CODE, "search", TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应转换为大写 "SEARCH"
      verify(capabilityMapper).selectActiveByTask(TEST_PROVENANCE_ID, "SEARCH", TEST_TIMESTAMP);
      verify(renderRuleMapper).selectActiveByTask(TEST_PROVENANCE_ID, "SEARCH", TEST_TIMESTAMP);
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, "SEARCH", "ESEARCH", TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应将 null 操作类型转换为 ALL")
    void shouldConvertNullOperationTypeToAll() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
          .thenReturn(List.of());

      // When: 传入 null 操作类型
      repository.loadSnapshot(TEST_PROVENANCE_CODE, null, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应转换为 "ALL"
      verify(capabilityMapper).selectActiveByTask(TEST_PROVENANCE_ID, "ALL", TEST_TIMESTAMP);
      verify(renderRuleMapper).selectActiveByTask(TEST_PROVENANCE_ID, "ALL", TEST_TIMESTAMP);
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, "ALL", "ESEARCH", TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应将空白操作类型转换为 ALL")
    void shouldConvertBlankOperationTypeToAll() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
          .thenReturn(List.of());

      // When: 传入空白操作类型
      repository.loadSnapshot(TEST_PROVENANCE_CODE, "   ", TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应转换为 "ALL"
      verify(capabilityMapper).selectActiveByTask(TEST_PROVENANCE_ID, "ALL", TEST_TIMESTAMP);
      verify(renderRuleMapper).selectActiveByTask(TEST_PROVENANCE_ID, "ALL", TEST_TIMESTAMP);
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, "ALL", "ESEARCH", TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("端点名称规范化场景")
  class EndpointNameNormalizationTests {

    @Test
    @DisplayName("应将小写端点名称转换为大写")
    void shouldConvertLowercaseEndpointNameToUppercase() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
          .thenReturn(List.of());

      // When: 传入小写端点名称 "esearch"
      repository.loadSnapshot(TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, "esearch", TEST_TIMESTAMP);

      // Then: 应转换为大写 "ESEARCH"
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, "ESEARCH", TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应将混合大小写端点名称转换为大写")
    void shouldConvertMixedCaseEndpointNameToUppercase() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class)))
          .thenReturn(List.of());

      // When: 传入混合大小写端点名称 "ESearch"
      repository.loadSnapshot(TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, "ESearch", TEST_TIMESTAMP);

      // Then: 应转换为大写 "ESEARCH"
      verify(apiParamMapMapper)
          .selectActiveByTask(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, "ESEARCH", TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("异常场景")
  class ExceptionTests {

    @Test
    @DisplayName("应在数据源代码未找到时抛出 ProvenanceNotFoundException")
    void shouldThrowProvenanceNotFoundExceptionWhenCodeNotFound() {
      // Given: 数据源代码不存在
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.empty());

      // When & Then: 应抛出 ProvenanceNotFoundException
      assertThatThrownBy(
              () ->
                  repository.loadSnapshot(
                      TEST_PROVENANCE_CODE,
                      TEST_OPERATION_TYPE,
                      TEST_ENDPOINT_NAME,
                      TEST_TIMESTAMP))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("Provenance code not found: PUBMED");

      // 验证后续查询未执行
      verify(fieldDictMapper, never()).selectAllActive();
      verify(capabilityMapper, never())
          .selectActiveByTask(anyLong(), anyString(), any(Instant.class));
      verify(renderRuleMapper, never())
          .selectActiveByTask(anyLong(), anyString(), any(Instant.class));
      verify(apiParamMapMapper, never())
          .selectActiveByTask(anyLong(), anyString(), any(), any(Instant.class));
    }
  }

  @Nested
  @DisplayName("空结果场景")
  class EmptyResultTests {

    @Test
    @DisplayName("应在所有查询返回空列表时创建空快照")
    void shouldCreateEmptySnapshotWhenAllQueriesReturnEmpty() {
      // Given: 所有查询返回空列表
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));
      when(fieldDictMapper.selectAllActive()).thenReturn(List.of());
      when(capabilityMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, "ESEARCH", TEST_TIMESTAMP))
          .thenReturn(List.of());

      // When
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

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
      // Given: 只有字段字典有数据
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));

      List<RegExprFieldDictDO> fieldDOs = List.of(createFieldDictDO("title"));
      when(fieldDictMapper.selectAllActive()).thenReturn(fieldDOs);
      ExprField field = org.mockito.Mockito.mock(ExprField.class);
      when(converter.toDomain(fieldDOs.get(0))).thenReturn(field);

      when(capabilityMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(renderRuleMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(List.of());
      when(apiParamMapMapper.selectActiveByTask(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, "ESEARCH", TEST_TIMESTAMP))
          .thenReturn(List.of());

      // When
      ExprSnapshot snapshot =
          repository.loadSnapshot(
              TEST_PROVENANCE_CODE, TEST_OPERATION_TYPE, TEST_ENDPOINT_NAME, TEST_TIMESTAMP);

      // Then: 应创建部分快照
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).hasSize(1).containsExactly(field);
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }
  }

  // ==================== 辅助方法 ====================

  private RegProvenanceDO createProvenanceDO(Long id, String code) {
    RegProvenanceDO provenance = new RegProvenanceDO();
    provenance.setId(id);
    provenance.setProvenanceCode(code);
    provenance.setProvenanceName("Test Provenance");
    provenance.setIsActive(true);
    return provenance;
  }

  private RegExprFieldDictDO createFieldDictDO(String fieldKey) {
    RegExprFieldDictDO field = new RegExprFieldDictDO();
    field.setFieldKey(fieldKey);
    field.setDisplayName(fieldKey);
    field.setDescription("Test field");
    field.setDataTypeCode("STRING");
    return field;
  }

  private RegProvExprCapabilityDO createCapabilityDO(String fieldKey, boolean supportsOr) {
    RegProvExprCapabilityDO capability = new RegProvExprCapabilityDO();
    capability.setProvenanceId(TEST_PROVENANCE_ID);
    capability.setOperationType(TEST_OPERATION_KEY);
    capability.setFieldKey(fieldKey);
    capability.setLifecycleStatusCode("ACTIVE");
    return capability;
  }

  private RegProvExprRenderRuleDO createRenderRuleDO(String fieldKey, String template) {
    RegProvExprRenderRuleDO renderRule = new RegProvExprRenderRuleDO();
    renderRule.setProvenanceId(TEST_PROVENANCE_ID);
    renderRule.setOperationType(TEST_OPERATION_KEY);
    renderRule.setFieldKey(fieldKey);
    renderRule.setTemplate(template);
    renderRule.setLifecycleStatusCode("ACTIVE");
    return renderRule;
  }

  private RegProvApiParamMapDO createApiParamMapDO(String stdKey, String apiSyntax) {
    RegProvApiParamMapDO apiParam = new RegProvApiParamMapDO();
    apiParam.setProvenanceId(TEST_PROVENANCE_ID);
    apiParam.setOperationType(TEST_OPERATION_KEY);
    apiParam.setEndpointName("ESEARCH");
    apiParam.setStdKey(stdKey);
    apiParam.setProviderParamName(apiSyntax);
    apiParam.setLifecycleStatusCode("ACTIVE");
    return apiParam;
  }
}
