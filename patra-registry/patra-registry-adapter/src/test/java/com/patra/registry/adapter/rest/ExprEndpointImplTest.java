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
import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.exception.RegistryConflict;
import com.patra.registry.domain.exception.RegistryQuotaExceeded;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
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
      ExprSnapshotResp result =
          endpoint.getSnapshot(provenanceCode, operationType, endpointName, at);

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
      ExprSnapshotResp result =
          endpoint.getSnapshot(provenanceCode, operationType, endpointName, null);

      // Then: 验证过滤参数正确传递
      assertThat(result).isNotNull();
      verify(orchestrator).loadSnapshot(provenanceCode, operationType, endpointName, null);
    }
  }

  @Nested
  @DisplayName("getSnapshot() 异常场景测试")
  class GetSnapshotExceptionTests {

    @Test
    @DisplayName("当 provenanceCode 无效时应该抛出 IllegalArgumentException")
    void shouldThrowIllegalArgumentExceptionWhenProvenanceCodeInvalid() {
      // Given: 无效的 provenanceCode
      String invalidProvenanceCode = "INVALID_CODE";

      // When & Then: 验证 IllegalArgumentException 被传播
      // ProvenanceCode.parse() 会在 Orchestrator 内部抛出此异常
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new IllegalArgumentException("未知的数据源: " + invalidProvenanceCode));

      assertThatThrownBy(() -> endpoint.getSnapshot(invalidProvenanceCode, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的数据源");
    }

    @Test
    @DisplayName("当 provenanceCode 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowIllegalArgumentExceptionWhenProvenanceCodeNull() {
      // Given: null provenanceCode
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new IllegalArgumentException("数据源标识符不能为 null"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot(null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("数据源标识符不能为 null");
    }

    @Test
    @DisplayName("当领域验证失败时应该抛出 DomainValidationException")
    void shouldThrowDomainValidationExceptionWhenValidationFails() {
      // Given: Orchestrator 抛出领域验证异常
      String provenanceCode = "PUBMED";
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new DomainValidationException("operationType 不能为空白"));

      // When & Then: 验证 DomainValidationException 被传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, "", null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("operationType 不能为空白");
    }

    @Test
    @DisplayName("当参数超出范围时应该抛出 DomainValidationException")
    void shouldThrowDomainValidationExceptionWhenParameterOutOfRange() {
      // Given: 参数验证失败（例如时间点在未来）
      String provenanceCode = "EPMC";
      Instant futureTime = Instant.parse("2099-01-01T00:00:00Z");
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new DomainValidationException("at 必须在过去或现在"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, futureTime))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("at 必须在过去或现在");
    }

    @Test
    @DisplayName("当数据源配置未找到时应该抛出 RegistryNotFound")
    void shouldThrowRegistryNotFoundWhenProvenanceConfigNotFound() {
      // Given: 数据源配置不存在
      String provenanceCode = "ARXIV";
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new ProvenanceNotFoundException("数据源配置未找到: " + provenanceCode));

      // When & Then: 验证 RegistryNotFound 异常被传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, null))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("数据源配置未找到");
    }

    @Test
    @DisplayName("当指定操作类型的配置未找到时应该抛出 RegistryNotFound")
    void shouldThrowRegistryNotFoundWhenOperationTypeConfigNotFound() {
      // Given: 特定操作类型的配置不存在
      String provenanceCode = "PUBMED";
      String operationType = "UNKNOWN_OPERATION";
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new ProvenanceNotFoundException("未找到操作类型 " + operationType + " 的配置"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, operationType, null, null))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("未找到操作类型");
    }

    @Test
    @DisplayName("当指定端点的配置未找到时应该抛出 RegistryNotFound")
    void shouldThrowRegistryNotFoundWhenEndpointConfigNotFound() {
      // Given: 特定端点的配置不存在
      String provenanceCode = "EPMC";
      String operationType = "HARVEST";
      String endpointName = "unknown_endpoint";
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new ProvenanceNotFoundException("未找到端点 " + endpointName + " 的配置"));

      // When & Then: 验证异常传播
      assertThatThrownBy(
              () -> endpoint.getSnapshot(provenanceCode, operationType, endpointName, null))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("未找到端点");
    }

    @Test
    @DisplayName("当指定时间点的历史配置未找到时应该抛出 RegistryNotFound")
    void shouldThrowRegistryNotFoundWhenHistoricalConfigNotFound() {
      // Given: 指定时间点没有有效配置
      String provenanceCode = "PUBMED";
      Instant historicalTime = Instant.parse("2000-01-01T00:00:00Z");
      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new ProvenanceNotFoundException("在时间点 " + historicalTime + " 未找到有效配置"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, historicalTime))
          .isInstanceOf(ProvenanceNotFoundException.class)
          .hasMessageContaining("未找到有效配置");
    }

    @Test
    @DisplayName("当并发查询超出配额时应该抛出 RegistryQuotaExceeded")
    void shouldThrowRegistryQuotaExceededWhenConcurrentQueriesExceedLimit() {
      // Given: 并发查询数超出配额
      String provenanceCode = "PUBMED";

      // 创建一个具体的 RegistryQuotaExceeded 子类实例
      class ConcurrentQueryQuotaExceeded extends RegistryQuotaExceeded {
        public ConcurrentQueryQuotaExceeded(String message) {
          super(message);
        }
      }

      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new ConcurrentQueryQuotaExceeded("并发查询数超出配额限制"));

      // When & Then: 验证 RegistryQuotaExceeded 异常被传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, null))
          .isInstanceOf(RegistryQuotaExceeded.class)
          .hasMessageContaining("并发查询数超出配额限制");
    }

    @Test
    @DisplayName("当查询结果大小超出配额时应该抛出 RegistryQuotaExceeded")
    void shouldThrowRegistryQuotaExceededWhenResultSizeExceedsLimit() {
      // Given: 查询结果大小超出限制
      String provenanceCode = "EPMC";

      class ResultSizeQuotaExceeded extends RegistryQuotaExceeded {
        public ResultSizeQuotaExceeded(String message) {
          super(message);
        }
      }

      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new ResultSizeQuotaExceeded("查询结果大小超出限制: 最大 10MB"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, null))
          .isInstanceOf(RegistryQuotaExceeded.class)
          .hasMessageContaining("查询结果大小超出限制");
    }

    @Test
    @DisplayName("当版本冲突时应该抛出 RegistryConflict")
    void shouldThrowRegistryConflictWhenVersionConflict() {
      // Given: 版本冲突（例如乐观锁失败）
      String provenanceCode = "PUBMED";

      class VersionConflictException extends RegistryConflict {
        public VersionConflictException(String message) {
          super(message);
        }
      }

      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new VersionConflictException("配置版本冲突,请重试"));

      // When & Then: 验证 RegistryConflict 异常被传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, null))
          .isInstanceOf(RegistryConflict.class)
          .hasMessageContaining("配置版本冲突");
    }

    @Test
    @DisplayName("当时态切片状态冲突时应该抛出 RegistryConflict")
    void shouldThrowRegistryConflictWhenTemporalSliceConflict() {
      // Given: 时态切片状态不一致
      String provenanceCode = "CROSSREF";
      Instant at = Instant.parse("2024-06-01T00:00:00Z");

      class TemporalSliceConflictException extends RegistryConflict {
        public TemporalSliceConflictException(String message) {
          super(message);
        }
      }

      when(orchestrator.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new TemporalSliceConflictException("时态切片状态冲突"));

      // When & Then: 验证异常传播
      assertThatThrownBy(() -> endpoint.getSnapshot(provenanceCode, null, null, at))
          .isInstanceOf(RegistryConflict.class)
          .hasMessageContaining("时态切片状态冲突");
    }
  }

  // ========== 测试数据构建助手 ==========

  private ExprSnapshotQuery createMockQuery() {
    // 创建简化的查询结果
    return new ExprSnapshotQuery(
        Collections.emptyList(), // fields
        Collections.emptyList(), // capabilities
        Collections.emptyList(), // renderRules
        Collections.emptyList() // paramMappings
        );
  }

  private ExprSnapshotResp createMockResponse() {
    // 创建简化的 API 响应
    return new ExprSnapshotResp(
        Collections.emptyList(), // fields
        Collections.emptyList(), // capabilities
        Collections.emptyList(), // renderRules
        Collections.emptyList() // paramMappings
        );
  }
}
