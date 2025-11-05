package com.patra.registry.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.registry.adapter.rest.converter.ExprApiConverter;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.app.service.ExprQueryOrchestrator;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ExprEndpointImpl 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 Mockito Mock Orchestrator 和 Converter
 *   <li>验证 Controller 正确调用 Orchestrator
 *   <li>验证 Controller 正确使用 Converter 转换响应
 *   <li>验证参数正确传递
 *   <li>验证异常正确传播
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExprEndpointImpl 单元测试")
class ExprEndpointImplTest {

  @Mock private ExprQueryOrchestrator orchestrator;

  @Mock private ExprApiConverter converter;

  @InjectMocks private ExprEndpointImpl endpoint;

  @Nested
  @DisplayName("getSnapshot() 方法测试")
  class GetSnapshotTests {

    @Test
    @DisplayName("应该成功返回表达式快照")
    void shouldReturnExprSnapshot() {
      // Given: 准备测试数据
      String provenanceCode = "PUBMED";
      String operationType = "HARVEST";
      String endpointName = "search";
      Instant at = Instant.parse("2024-01-01T00:00:00Z");

      ExprSnapshotQuery queryResult = createMockQuery();
      when(orchestrator.loadSnapshot(provenanceCode, operationType, endpointName, at))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ExprSnapshotResp result = endpoint.getSnapshot(provenanceCode, operationType, endpointName, at);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(expectedResp);

      // 验证调用
      verify(orchestrator).loadSnapshot(provenanceCode, operationType, endpointName, at);
      verify(converter).toResp(queryResult);
    }

    @Test
    @DisplayName("应该支持可选参数为 null")
    void shouldHandleNullOptionalParameters() {
      // Given: operationType, endpointName, at 都为 null
      String provenanceCode = "EPMC";

      ExprSnapshotQuery queryResult = createMockQuery();
      when(orchestrator.loadSnapshot(eq(provenanceCode), isNull(), isNull(), isNull()))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ExprSnapshotResp result = endpoint.getSnapshot(provenanceCode, null, null, null);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(expectedResp);

      verify(orchestrator).loadSnapshot(provenanceCode, null, null, null);
    }

    @Test
    @DisplayName("应该只提供必填参数 provenanceCode")
    void shouldWorkWithOnlyRequiredParameter() {
      // Given: 只提供 provenanceCode
      String provenanceCode = "ARXIV";

      ExprSnapshotQuery queryResult = createMockQuery();
      when(orchestrator.loadSnapshot(provenanceCode, null, null, null)).thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ExprSnapshotResp result = endpoint.getSnapshot(provenanceCode, null, null, null);

      // Then: 验证结果
      assertThat(result).isNotNull();
      verify(orchestrator).loadSnapshot(provenanceCode, null, null, null);
      verify(converter).toResp(queryResult);
    }

    @Test
    @DisplayName("当 Orchestrator 抛出异常时应该传播异常")
    void shouldPropagateOrchestratorException() {
      // Given: Orchestrator 抛出异常
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("Provenance not found"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot("UNKNOWN", null, null, null))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Provenance not found");
    }

    @Test
    @DisplayName("当 Converter 抛出异常时应该传播异常")
    void shouldPropagateConverterException() {
      // Given: Orchestrator 成功,但 Converter 抛出异常
      ExprSnapshotQuery queryResult = createMockQuery();
      when(orchestrator.loadSnapshot(any(), any(), any(), any())).thenReturn(queryResult);
      when(converter.toResp(queryResult)).thenThrow(new RuntimeException("Conversion error"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot("PUBMED", null, null, null))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Conversion error");
    }

    @Test
    @DisplayName("应该正确传递时态切片参数")
    void shouldPassTemporalParameter() {
      // Given: 提供历史时间点
      String provenanceCode = "PUBMED";
      Instant historicalTime = Instant.parse("2023-01-01T00:00:00Z");

      ExprSnapshotQuery queryResult = createMockQuery();
      when(orchestrator.loadSnapshot(provenanceCode, null, null, historicalTime))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ExprSnapshotResp result = endpoint.getSnapshot(provenanceCode, null, null, historicalTime);

      // Then: 验证时态参数正确传递
      assertThat(result).isNotNull();
      verify(orchestrator).loadSnapshot(provenanceCode, null, null, historicalTime);
    }

    @Test
    @DisplayName("应该正确传递过滤参数")
    void shouldPassFilterParameters() {
      // Given: 提供所有过滤参数
      String provenanceCode = "PUBMED";
      String operationType = "HARVEST";
      String endpointName = "search";

      ExprSnapshotQuery queryResult = createMockQuery();
      when(orchestrator.loadSnapshot(provenanceCode, operationType, endpointName, null))
          .thenReturn(queryResult);

      ExprSnapshotResp expectedResp = createMockResponse();
      when(converter.toResp(queryResult)).thenReturn(expectedResp);

      // When: 执行方法
      ExprSnapshotResp result = endpoint.getSnapshot(provenanceCode, operationType, endpointName, null);

      // Then: 验证过滤参数正确传递
      assertThat(result).isNotNull();
      verify(orchestrator).loadSnapshot(provenanceCode, operationType, endpointName, null);
    }
  }

  // ========== 测试数据构建助手 ==========

  private ExprSnapshotQuery createMockQuery() {
    // 创建简化的查询结果
    return new ExprSnapshotQuery(
        Collections.emptyList(), // fields
        Collections.emptyList(), // capabilities
        Collections.emptyList(), // renderRules
        Collections.emptyList()  // paramMappings
    );
  }

  private ExprSnapshotResp createMockResponse() {
    // 创建简化的 API 响应
    return new ExprSnapshotResp(
        Collections.emptyList(), // fields
        Collections.emptyList(), // capabilities
        Collections.emptyList(), // renderRules
        Collections.emptyList()  // paramMappings
    );
  }
}
