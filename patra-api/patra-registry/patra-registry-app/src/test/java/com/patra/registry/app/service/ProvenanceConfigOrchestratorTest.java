package com.patra.registry.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ProvenanceQueryAssembler;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// ProvenanceConfigOrchestrator 单元测试。
/// 
/// 测试覆盖:
/// 
/// - ✅ listProvenances() - 正常场景，返回多个数据源
///   - ✅ listProvenances() - 空列表场景
///   - ✅ listProvenances() - Repository 抛出异常
///   - ✅ findProvenance() - 找到数据源
///   - ✅ findProvenance() - 未找到数据源 (Optional.empty)
///   - ✅ findProvenance() - Repository 抛出异常
///   - ✅ loadConfiguration() - 加载配置成功
///   - ✅ loadConfiguration() - 使用 null 时间点 (默认当前时间)
///   - ✅ loadConfiguration() - 数据源不存在，返回 Optional.empty
///   - ✅ loadConfiguration() - 配置不存在，返回 Optional.empty
/// 
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("ProvenanceConfigOrchestrator 单元测试")
class ProvenanceConfigOrchestratorTest {

  @Mock private ProvenanceConfigRepository repository;

  @Mock private ProvenanceQueryAssembler assembler;

  @Mock private ProvenanceConfiguration configuration;

  @Mock private ProvenanceConfigQuery configQuery;

  private ProvenanceConfigOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new ProvenanceConfigOrchestrator(repository, assembler);
  }

  @Nested
  @DisplayName("listProvenances() 方法测试")
  class ListProvenancesTests {

    @Test
    @DisplayName("应该成功返回所有数据源列表")
    void shouldReturnAllProvenances() {
      // Given: 准备测试数据
      Provenance provenance1 = createMockProvenance(1L, "PUBMED", "PubMed");
      Provenance provenance2 = createMockProvenance(2L, "EPMC", "Europe PMC");

      ProvenanceQuery query1 = createMockProvenanceQuery(1L, "PUBMED", "PubMed");
      ProvenanceQuery query2 = createMockProvenanceQuery(2L, "EPMC", "Europe PMC");

      // Mock Repository 返回
      when(repository.findAllProvenances()).thenReturn(List.of(provenance1, provenance2));

      // Mock Assembler 返回
      when(assembler.toQuery(provenance1)).thenReturn(query1);
      when(assembler.toQuery(provenance2)).thenReturn(query2);

      // When: 执行方法
      List<ProvenanceQuery> result = orchestrator.listProvenances();

      // Then: 验证结果
      assertThat(result).hasSize(2);
      assertThat(result).containsExactly(query1, query2);

      // 验证调用
      verify(repository).findAllProvenances();
      verify(assembler).toQuery(provenance1);
      verify(assembler).toQuery(provenance2);
    }

    @Test
    @DisplayName("应该正确处理空数据源列表")
    void shouldHandleEmptyProvenanceList() {
      // Given: Repository 返回空列表
      when(repository.findAllProvenances()).thenReturn(List.of());

      // When: 执行方法
      List<ProvenanceQuery> result = orchestrator.listProvenances();

      // Then: 验证结果
      assertThat(result).isEmpty();

      // 验证调用
      verify(repository).findAllProvenances();
    }

    @Test
    @DisplayName("当 Repository 抛出异常时应该传播异常")
    void shouldPropagateRepositoryException() {
      // Given: Repository 抛出异常
      when(repository.findAllProvenances())
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then: 验证异常
      assertThatThrownBy(() -> orchestrator.listProvenances())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database connection failed");
    }

    @Test
    @DisplayName("应该正确处理大量数据源")
    void shouldHandleManyProvenances() {
      // Given: 准备多个数据源
      List<Provenance> provenances =
          List.of(
              createMockProvenance(1L, "PUBMED", "PubMed"),
              createMockProvenance(2L, "EPMC", "Europe PMC"),
              createMockProvenance(3L, "CROSSREF", "Crossref"),
              createMockProvenance(4L, "ARXIV", "arXiv"),
              createMockProvenance(5L, "BIORXIV", "bioRxiv"));

      when(repository.findAllProvenances()).thenReturn(provenances);

      // Mock Assembler 返回
      provenances.forEach(
          p ->
              when(assembler.toQuery(p))
                  .thenReturn(createMockProvenanceQuery(p.id(), p.code(), p.name())));

      // When: 执行方法
      List<ProvenanceQuery> result = orchestrator.listProvenances();

      // Then: 验证结果
      assertThat(result).hasSize(5);
    }
  }

  @Nested
  @DisplayName("findProvenance() 方法测试")
  class FindProvenanceTests {

    @Test
    @DisplayName("应该成功找到指定数据源")
    void shouldFindProvenanceSuccessfully() {
      // Given: 准备测试数据
      ProvenanceCode code = ProvenanceCode.PUBMED;
      Provenance provenance = createMockProvenance(1L, "PUBMED", "PubMed");
      ProvenanceQuery query = createMockProvenanceQuery(1L, "PUBMED", "PubMed");

      // Mock Repository 返回
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.of(provenance));

      // Mock Assembler 返回
      when(assembler.toQuery(provenance)).thenReturn(query);

      // When: 执行方法
      Optional<ProvenanceQuery> result = orchestrator.findProvenance(code);

      // Then: 验证结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(query);

      // 验证调用
      verify(repository).findProvenanceByCode(code);
      verify(assembler).toQuery(provenance);
    }

    @Test
    @DisplayName("当数据源不存在时应该返回 Optional.empty")
    void shouldReturnEmptyWhenProvenanceNotFound() {
      // Given: Repository 返回空
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.empty());

      // When: 执行方法
      Optional<ProvenanceQuery> result = orchestrator.findProvenance(code);

      // Then: 验证结果
      assertThat(result).isEmpty();

      // 验证调用
      verify(repository).findProvenanceByCode(code);
    }

    @Test
    @DisplayName("当 Repository 抛出异常时应该传播异常")
    void shouldPropagateRepositoryException() {
      // Given: Repository 抛出异常
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(repository.findProvenanceByCode(code))
          .thenThrow(new RuntimeException("Database query failed"));

      // When & Then: 验证异常
      assertThatThrownBy(() -> orchestrator.findProvenance(code))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database query failed");
    }

    @Test
    @DisplayName("应该正确处理不同的数据源代码")
    void shouldHandleDifferentProvenanceCodes() {
      // Given: 测试不同的数据源代码
      ProvenanceCode[] codes = {
        ProvenanceCode.PUBMED, ProvenanceCode.EPMC, ProvenanceCode.CROSSREF
      };

      for (ProvenanceCode code : codes) {
        Provenance provenance = createMockProvenance(1L, code.getCode(), code.getCode());
        ProvenanceQuery query = createMockProvenanceQuery(1L, code.getCode(), code.getCode());

        when(repository.findProvenanceByCode(code)).thenReturn(Optional.of(provenance));
        when(assembler.toQuery(provenance)).thenReturn(query);

        // When: 执行方法
        Optional<ProvenanceQuery> result = orchestrator.findProvenance(code);

        // Then: 验证结果
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo(code.getCode());
      }
    }
  }

  @Nested
  @DisplayName("loadConfiguration() 方法测试")
  class LoadConfigurationTests {

    @Test
    @DisplayName("应该成功加载配置")
    void shouldLoadConfigurationSuccessfully() {
      // Given: 准备测试数据
      ProvenanceCode code = ProvenanceCode.PUBMED;
      String operationType = "HARVEST";
      Instant at = Instant.parse("2025-01-01T00:00:00Z");

      Provenance provenance = createMockProvenance(1L, "PUBMED", "PubMed");

      // Mock Repository 返回
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.of(provenance));
      when(repository.loadConfiguration(1L, operationType, at))
          .thenReturn(Optional.of(configuration));

      // Mock Assembler 返回
      when(assembler.toQuery(configuration)).thenReturn(configQuery);

      // When: 执行方法
      Optional<ProvenanceConfigQuery> result =
          orchestrator.loadConfiguration(code, operationType, at);

      // Then: 验证结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(configQuery);

      // 验证调用
      verify(repository).findProvenanceByCode(code);
      verify(repository).loadConfiguration(1L, operationType, at);
      verify(assembler).toQuery(configuration);
    }

    @Test
    @DisplayName("当时间点为 null 时应该使用当前时间")
    void shouldUseCurrentTimeWhenAtIsNull() {
      // Given: 准备测试数据
      ProvenanceCode code = ProvenanceCode.PUBMED;
      String operationType = "HARVEST";

      Provenance provenance = createMockProvenance(1L, "PUBMED", "PubMed");

      // Mock Repository 返回
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.of(provenance));
      when(repository.loadConfiguration(eq(1L), eq(operationType), any(Instant.class)))
          .thenReturn(Optional.of(configuration));

      // Mock Assembler 返回
      when(assembler.toQuery(configuration)).thenReturn(configQuery);

      // When: 执行方法 (at = null)
      Optional<ProvenanceConfigQuery> result =
          orchestrator.loadConfiguration(code, operationType, null);

      // Then: 验证结果
      assertThat(result).isPresent();

      // 验证调用 (应该使用当前时间，而不是 null)
      verify(repository).loadConfiguration(eq(1L), eq(operationType), any(Instant.class));
    }

    @Test
    @DisplayName("应该正确处理 null 操作类型")
    void shouldHandleNullOperationType() {
      // Given: 准备测试数据
      ProvenanceCode code = ProvenanceCode.PUBMED;
      Instant at = Instant.now();

      Provenance provenance = createMockProvenance(1L, "PUBMED", "PubMed");

      // Mock Repository 返回
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.of(provenance));
      when(repository.loadConfiguration(1L, null, at)).thenReturn(Optional.of(configuration));

      // Mock Assembler 返回
      when(assembler.toQuery(configuration)).thenReturn(configQuery);

      // When: 执行方法 (operationType = null)
      Optional<ProvenanceConfigQuery> result = orchestrator.loadConfiguration(code, null, at);

      // Then: 验证结果
      assertThat(result).isPresent();

      // 验证调用
      verify(repository).loadConfiguration(1L, null, at);
    }

    @Test
    @DisplayName("当数据源不存在时应该返回 Optional.empty")
    void shouldReturnEmptyWhenProvenanceNotFound() {
      // Given: Repository 返回空 (数据源不存在)
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.empty());

      // When: 执行方法
      Optional<ProvenanceConfigQuery> result =
          orchestrator.loadConfiguration(code, "HARVEST", Instant.now());

      // Then: 验证结果
      assertThat(result).isEmpty();

      // 验证调用
      verify(repository).findProvenanceByCode(code);
    }

    @Test
    @DisplayName("当配置不存在时应该返回 Optional.empty")
    void shouldReturnEmptyWhenConfigurationNotFound() {
      // Given: 数据源存在，但配置不存在
      ProvenanceCode code = ProvenanceCode.PUBMED;
      Instant at = Instant.now();

      Provenance provenance = createMockProvenance(1L, "PUBMED", "PubMed");

      // Mock Repository 返回
      when(repository.findProvenanceByCode(code)).thenReturn(Optional.of(provenance));
      when(repository.loadConfiguration(1L, null, at)).thenReturn(Optional.empty());

      // When: 执行方法
      Optional<ProvenanceConfigQuery> result = orchestrator.loadConfiguration(code, null, at);

      // Then: 验证结果
      assertThat(result).isEmpty();

      // 验证调用
      verify(repository).findProvenanceByCode(code);
      verify(repository).loadConfiguration(1L, null, at);
    }

    @Test
    @DisplayName("当 Repository 抛出异常时应该传播异常")
    void shouldPropagateRepositoryException() {
      // Given: Repository 抛出异常
      ProvenanceCode code = ProvenanceCode.PUBMED;
      when(repository.findProvenanceByCode(code))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then: 验证异常
      assertThatThrownBy(() -> orchestrator.loadConfiguration(code, "HARVEST", Instant.now()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database connection failed");
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建 Mock Provenance。
  private Provenance createMockProvenance(Long id, String code, String name) {
    return new Provenance(
        id,
        code,
        name,
        "https://api.example.com",
        "UTC",
        "https://docs.example.com",
        true,
        "ACTIVE");
  }

  /// 创建 Mock ProvenanceQuery。
  private ProvenanceQuery createMockProvenanceQuery(Long id, String code, String name) {
    return new ProvenanceQuery(
        id,
        code,
        name,
        "https://api.example.com",
        "UTC",
        "https://docs.example.com",
        true,
        "ACTIVE");
  }
}
