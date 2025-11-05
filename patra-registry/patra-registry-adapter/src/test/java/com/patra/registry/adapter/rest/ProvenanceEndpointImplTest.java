package com.patra.registry.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.rest.converter.ProvenanceApiConverter;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.app.service.ProvenanceConfigOrchestrator;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import java.time.Instant;
import java.util.Collections;
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
 * ProvenanceEndpointImpl 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 Mockito Mock Orchestrator 和 Converter
 *   <li>验证 Controller 正确调用 Orchestrator
 *   <li>验证 Controller 正确使用 Converter 转换响应
 *   <li>验证参数正确传递
 *   <li>验证异常正确处理 (ProvenanceNotFoundException)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProvenanceEndpointImpl 单元测试")
class ProvenanceEndpointImplTest {

  @Mock private ProvenanceConfigOrchestrator orchestrator;

  @Mock private ProvenanceApiConverter converter;

  @InjectMocks private ProvenanceEndpointImpl endpoint;

  @Nested
  @DisplayName("listProvenances() 方法测试")
  class ListProvenancesTests {

    @Test
    @DisplayName("应该成功返回所有数据源列表")
    void shouldReturnProvenanceList() {
      // Given: 准备测试数据
      List<ProvenanceQuery> queryResults = List.of(createMockProvenanceQuery("PUBMED"), createMockProvenanceQuery("EPMC"));

      when(orchestrator.listProvenances()).thenReturn(queryResults);

      List<ProvenanceResp> expectedResps = List.of(createMockProvenanceResp("PUBMED"), createMockProvenanceResp("EPMC"));
      when(converter.toResp(queryResults)).thenReturn(expectedResps);

      // When: 执行方法
      List<ProvenanceResp> result = endpoint.listProvenances();

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).hasSize(2);
      assertThat(result).isEqualTo(expectedResps);

      // 验证调用
      verify(orchestrator).listProvenances();
      verify(converter).toResp(queryResults);
    }

    @Test
    @DisplayName("当没有数据源时应该返回空列表")
    void shouldReturnEmptyListWhenNoProvenances() {
      // Given: Orchestrator 返回空列表
      when(orchestrator.listProvenances()).thenReturn(Collections.emptyList());
      when(converter.toResp(Collections.emptyList())).thenReturn(Collections.emptyList());

      // When: 执行方法
      List<ProvenanceResp> result = endpoint.listProvenances();

      // Then: 验证空列表
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
      verify(orchestrator).listProvenances();
    }

    @Test
    @DisplayName("当 Orchestrator 抛出异常时应该传播异常")
    void shouldPropagateOrchestratorException() {
      // Given: Orchestrator 抛出异常
      when(orchestrator.listProvenances()).thenThrow(new RuntimeException("Database error"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.listProvenances())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database error");
    }
  }

  @Nested
  @DisplayName("getProvenance() 方法测试")
  class GetProvenanceTests {

    @Test
    @DisplayName("应该成功返回单个数据源")
    void shouldReturnSingleProvenance() {
      // Given: 准备测试数据
      ProvenanceCode code = ProvenanceCode.PUBMED;
      ProvenanceQuery queryResult = createMockProvenanceQuery("PUBMED");

      when(orchestrator.findProvenance(code)).thenReturn(Optional.of(queryResult));

      ProvenanceResp expectedResp = createMockProvenanceResp("PUBMED");
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ProvenanceResp result = endpoint.getProvenance(code);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(expectedResp);

      // 验证调用
      verify(orchestrator).findProvenance(code);
      verify(converter).toResp(queryResult);
    }

    @Test
    @DisplayName("当数据源不存在时应该抛出 ProvenanceNotFoundException")
    void shouldThrowExceptionWhenProvenanceNotFound() {
      // Given: Orchestrator 返回 Empty
      ProvenanceCode code = ProvenanceCode.EPMC;
      when(orchestrator.findProvenance(code)).thenReturn(Optional.empty());

      // When & Then: 验证异常
      assertThatThrownBy(() -> endpoint.getProvenance(code))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("Provenance not found for code [EPMC]");

      verify(orchestrator).findProvenance(code);
    }

    @Test
    @DisplayName("当 Orchestrator 抛出异常时应该传播异常")
    void shouldPropagateOrchestratorException() {
      // Given: Orchestrator 抛出异常
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(orchestrator.findProvenance(code)).thenThrow(new RuntimeException("Database error"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getProvenance(code))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database error");
    }
  }

  @Nested
  @DisplayName("getConfiguration() 方法测试")
  class GetConfigurationTests {

    @Test
    @DisplayName("应该成功返回配置聚合")
    void shouldReturnConfiguration() {
      // Given: 准备测试数据
      ProvenanceCode code = ProvenanceCode.PUBMED;
      String operationType = "HARVEST";
      Instant at = Instant.parse("2024-01-01T00:00:00Z");

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(orchestrator.loadConfiguration(code, operationType, at)).thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ProvenanceConfigResp result = endpoint.getConfiguration(code, operationType, at);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(expectedResp);

      // 验证调用
      verify(orchestrator).loadConfiguration(code, operationType, at);
      verify(converter).toResp(queryResult);
    }

    @Test
    @DisplayName("应该支持可选参数为 null")
    void shouldHandleNullOptionalParameters() {
      // Given: operationType 和 at 为 null
      ProvenanceCode code = ProvenanceCode.EPMC;

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(orchestrator.loadConfiguration(code, null, null)).thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ProvenanceConfigResp result = endpoint.getConfiguration(code, null, null);

      // Then: 验证结果
      assertThat(result).isNotNull();
      verify(orchestrator).loadConfiguration(code, null, null);
    }

    @Test
    @DisplayName("当配置不存在时应该抛出 ProvenanceNotFoundException")
    void shouldThrowExceptionWhenConfigNotFound() {
      // Given: Orchestrator 返回 Empty
      ProvenanceCode code = ProvenanceCode.EPMC;
      String operationType = "HARVEST";

      when(orchestrator.loadConfiguration(code, operationType, null)).thenReturn(Optional.empty());

      // When & Then: 验证异常
      assertThatThrownBy(() -> endpoint.getConfiguration(code, operationType, null))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("Provenance configuration not found for code [EPMC] and operationType [HARVEST]");

      verify(orchestrator).loadConfiguration(code, operationType, null);
    }

    @Test
    @DisplayName("应该正确传递时态切片参数")
    void shouldPassTemporalParameter() {
      // Given: 提供历史时间点
      ProvenanceCode code = ProvenanceCode.PUBMED;
      Instant historicalTime = Instant.parse("2023-01-01T00:00:00Z");

      ProvenanceConfigQuery queryResult = createMockConfigQuery();
      when(orchestrator.loadConfiguration(code, null, historicalTime)).thenReturn(Optional.of(queryResult));

      ProvenanceConfigResp expectedResp = createMockConfigResp();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ProvenanceConfigResp result = endpoint.getConfiguration(code, null, historicalTime);

      // Then: 验证时态参数正确传递
      assertThat(result).isNotNull();
      verify(orchestrator).loadConfiguration(code, null, historicalTime);
    }

    @Test
    @DisplayName("当 Orchestrator 抛出异常时应该传播异常")
    void shouldPropagateOrchestratorException() {
      // Given: Orchestrator 抛出异常
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(orchestrator.loadConfiguration(any(), any(), any())).thenThrow(new RuntimeException("Database error"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getConfiguration(code, null, null))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database error");
    }
  }

  // ========== 测试数据构建助手 ==========

  private ProvenanceQuery createMockProvenanceQuery(String code) {
    return new ProvenanceQuery(
        1L, // id
        code, // code
        code + " Source", // name
        "https://example.com", // baseUrlDefault
        "UTC", // timezoneDefault
        "https://docs.example.com", // docsUrl
        true, // active
        "ACTIVE" // lifecycleStatusCode
    );
  }

  private ProvenanceResp createMockProvenanceResp(String code) {
    return new ProvenanceResp(
        1L, // id
        code, // code
        code + " Source", // name
        "https://example.com", // baseUrlDefault
        "UTC", // timezoneDefault
        "https://docs.example.com", // docsUrl
        true, // active
        "ACTIVE" // lifecycleStatusCode
    );
  }

  private ProvenanceConfigQuery createMockConfigQuery() {
    return new ProvenanceConfigQuery(
        createMockProvenanceQuery("PUBMED"),
        null, // windowOffset
        null, // pagination
        null, // httpConfig
        null, // batching
        null, // retry
        null  // rateLimit
    );
  }

  private ProvenanceConfigResp createMockConfigResp() {
    return new ProvenanceConfigResp(
        createMockProvenanceResp("PUBMED"),
        null, // windowOffset
        null, // pagination
        null, // httpConfig
        null, // batching
        null, // retry
        null  // rateLimit
    );
  }
}
