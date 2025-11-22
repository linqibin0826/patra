package com.patra.registry.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ExprQueryAssembler;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;
import com.patra.registry.domain.port.ExprRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// ExprQueryOrchestrator 单元测试。
/// 
/// 测试覆盖:
/// 
/// - ✅ 正常场景 - 加载表达式快照成功
///   - ✅ 正常场景 - 使用所有过滤参数
///   - ✅ 正常场景 - 使用默认时间点 (null)
///   - ✅ 异常场景 - Repository 抛出异常
///   - ✅ 异常场景 - 无效的数据源代码
///   - ✅ 边界场景 - null operationType
///   - ✅ 边界场景 - null endpointName
///   - ✅ 验证调用 - Repository 和 Assembler 调用正确
/// 
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("ExprQueryOrchestrator 单元测试")
class ExprQueryOrchestratorTest {

  @Mock private ExprRepository exprRepository;

  @Mock private ExprQueryAssembler assembler;

  @Mock private ExprSnapshot domainSnapshot;

  @Mock private ExprSnapshotQuery snapshotQuery;

  private ExprQueryOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new ExprQueryOrchestrator(exprRepository, assembler);
  }

  @Nested
  @DisplayName("loadSnapshot() 方法测试")
  class LoadSnapshotTests {

    @Test
    @DisplayName("应该成功加载表达式快照")
    void shouldLoadExprSnapshotSuccessfully() {
      // Given: 准备测试数据
      String provenanceCodeStr = "PUBMED";
      String operationType = "HARVEST";
      String endpointName = "SEARCH";
      Instant at = Instant.parse("2025-01-01T00:00:00Z");

      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

      // Mock Repository 返回
      when(exprRepository.loadSnapshot(provenanceCode, operationType, endpointName, at))
          .thenReturn(domainSnapshot);

      // Mock Assembler 返回
      when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);

      // When: 执行方法
      ExprSnapshotQuery result =
          orchestrator.loadSnapshot(provenanceCodeStr, operationType, endpointName, at);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo(snapshotQuery);

      // 验证调用
      verify(exprRepository).loadSnapshot(provenanceCode, operationType, endpointName, at);
      verify(assembler).toQuery(domainSnapshot);
    }

    @Test
    @DisplayName("应该正确处理所有参数为 null 的情况")
    void shouldHandleAllNullParameters() {
      // Given: 准备测试数据
      String provenanceCodeStr = "EPMC";
      ProvenanceCode provenanceCode = ProvenanceCode.EPMC;

      // Mock Repository 返回
      when(exprRepository.loadSnapshot(provenanceCode, null, null, null))
          .thenReturn(domainSnapshot);

      // Mock Assembler 返回
      when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);
      when(snapshotQuery.fields()).thenReturn(List.of());
      when(snapshotQuery.capabilities()).thenReturn(List.of());
      when(snapshotQuery.renderRules()).thenReturn(List.of());
      when(snapshotQuery.apiParamMappings()).thenReturn(List.of());

      // When: 执行方法 (所有可选参数为 null)
      ExprSnapshotQuery result = orchestrator.loadSnapshot(provenanceCodeStr, null, null, null);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.fields()).isEmpty();
      assertThat(result.capabilities()).isEmpty();
      assertThat(result.renderRules()).isEmpty();
      assertThat(result.apiParamMappings()).isEmpty();

      // 验证调用
      verify(exprRepository).loadSnapshot(provenanceCode, null, null, null);
      verify(assembler).toQuery(domainSnapshot);
    }

    @Test
    @DisplayName("应该正确处理仅指定操作类型的情况")
    void shouldHandleOnlyOperationTypeSpecified() {
      // Given: 准备测试数据
      String provenanceCodeStr = "CROSSREF";
      String operationType = "UPDATE";
      ProvenanceCode provenanceCode = ProvenanceCode.CROSSREF;

      // Mock Repository 返回
      when(exprRepository.loadSnapshot(provenanceCode, operationType, null, null))
          .thenReturn(domainSnapshot);

      // Mock Assembler 返回
      when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);

      // When: 执行方法
      ExprSnapshotQuery result =
          orchestrator.loadSnapshot(provenanceCodeStr, operationType, null, null);

      // Then: 验证结果
      assertThat(result).isNotNull();

      // 验证调用
      verify(exprRepository).loadSnapshot(provenanceCode, operationType, null, null);
      verify(assembler).toQuery(domainSnapshot);
    }

    @Test
    @DisplayName("应该正确处理仅指定端点名称的情况")
    void shouldHandleOnlyEndpointNameSpecified() {
      // Given: 准备测试数据
      String provenanceCodeStr = "PUBMED";
      String endpointName = "DETAIL";
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

      // Mock Repository 返回
      when(exprRepository.loadSnapshot(provenanceCode, null, endpointName, null))
          .thenReturn(domainSnapshot);

      // Mock Assembler 返回
      when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);

      // When: 执行方法
      ExprSnapshotQuery result =
          orchestrator.loadSnapshot(provenanceCodeStr, null, endpointName, null);

      // Then: 验证结果
      assertThat(result).isNotNull();

      // 验证调用
      verify(exprRepository).loadSnapshot(provenanceCode, null, endpointName, null);
      verify(assembler).toQuery(domainSnapshot);
    }

    @Test
    @DisplayName("当 Repository 抛出异常时应该传播异常")
    void shouldPropagateRepositoryException() {
      // Given: Repository 抛出异常
      when(exprRepository.loadSnapshot(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then: 验证异常
      assertThatThrownBy(
              () -> orchestrator.loadSnapshot("PUBMED", "HARVEST", "SEARCH", Instant.now()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database connection failed");
    }

    @Test
    @DisplayName("当 Assembler 抛出异常时应该传播异常")
    void shouldPropagateAssemblerException() {
      // Given: Repository 正常返回，但 Assembler 抛出异常
      when(exprRepository.loadSnapshot(any(), any(), any(), any())).thenReturn(domainSnapshot);

      when(assembler.toQuery(any(ExprSnapshot.class)))
          .thenThrow(new RuntimeException("Mapping conversion failed"));

      // When & Then: 验证异常
      assertThatThrownBy(() -> orchestrator.loadSnapshot("PUBMED", null, null, null))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Mapping conversion failed");
    }

    @Test
    @DisplayName("当数据源代码无效时应该抛出异常")
    void shouldThrowExceptionForInvalidProvenanceCode() {
      // When & Then: 验证异常
      assertThatThrownBy(() -> orchestrator.loadSnapshot("INVALID_CODE", null, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该正确传递指定的时间点参数")
    void shouldPassSpecifiedTimePoint() {
      // Given: 准备测试数据
      String provenanceCodeStr = "PUBMED";
      Instant specificTime = Instant.parse("2024-12-31T23:59:59Z");
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

      // Mock Repository 返回
      when(exprRepository.loadSnapshot(provenanceCode, null, null, specificTime))
          .thenReturn(domainSnapshot);

      // Mock Assembler 返回
      when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);

      // When: 执行方法
      orchestrator.loadSnapshot(provenanceCodeStr, null, null, specificTime);

      // Then: 验证 Repository 调用时使用了指定的时间点
      verify(exprRepository).loadSnapshot(provenanceCode, null, null, specificTime);
    }

    @Test
    @DisplayName("应该正确处理空字符串参数")
    void shouldHandleEmptyStringParameters() {
      // Given: 准备测试数据
      String provenanceCodeStr = "PUBMED";
      String operationType = "";
      String endpointName = "";
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

      // Mock Repository 返回
      when(exprRepository.loadSnapshot(eq(provenanceCode), eq(""), eq(""), any()))
          .thenReturn(domainSnapshot);

      // Mock Assembler 返回
      when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);

      // When: 执行方法
      ExprSnapshotQuery result =
          orchestrator.loadSnapshot(provenanceCodeStr, operationType, endpointName, null);

      // Then: 验证结果
      assertThat(result).isNotNull();

      // 验证调用 (空字符串应该被传递)
      verify(exprRepository).loadSnapshot(eq(provenanceCode), eq(""), eq(""), any());
    }

    @Test
    @DisplayName("应该正确处理不同的数据源代码")
    void shouldHandleDifferentProvenanceCodes() {
      // Given: 测试不同的数据源代码
      String[] codes = {"PUBMED", "EPMC", "CROSSREF"};

      for (String code : codes) {
        ProvenanceCode provenanceCode = ProvenanceCode.parse(code);

        when(exprRepository.loadSnapshot(eq(provenanceCode), any(), any(), any()))
            .thenReturn(domainSnapshot);
        when(assembler.toQuery(domainSnapshot)).thenReturn(snapshotQuery);

        // When: 执行方法
        ExprSnapshotQuery result = orchestrator.loadSnapshot(code, null, null, null);

        // Then: 验证结果
        assertThat(result).isNotNull();
        verify(exprRepository).loadSnapshot(eq(provenanceCode), any(), any(), any());
      }
    }
  }
}
