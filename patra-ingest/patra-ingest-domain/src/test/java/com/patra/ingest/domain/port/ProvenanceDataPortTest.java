package com.patra.ingest.domain.port;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.DataType;
import com.patra.common.type.TypeReference;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.domain.port.ProvenanceDataPort.DataFetchResult;
import com.patra.ingest.domain.port.ProvenanceDataPort.DataFetchResult.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ProvenanceDataPort 接口测试
 *
 * <p>测试 v2.0 多数据类型架构的核心接口：泛型化数据获取端口
 *
 * <p><strong>测试策略</strong>：
 *
 * <ul>
 *   <li>使用 Mock 实现进行接口契约测试
 *   <li>验证泛型方法的类型安全性
 *   <li>验证 DataFetchResult 工厂方法
 *   <li>验证 supports 和 getSupportedTypes 方法
 * </ul>
 *
 * @since 0.1.0.0
 */
@DisplayName("ProvenanceDataPort v2.0 接口测试")
class ProvenanceDataPortTest {

  // ========== Mock 实现 ==========

  /** Mock 数据源端口实现（用于接口契约测试） */
  static class MockProvenanceDataPort implements ProvenanceDataPort {
    private final Map<String, Set<DataType>> supportedTypes =
        Map.of(
            ProvenanceCode.PUBMED.getCode(),
            Set.of(DataType.LITERATURE, DataType.CITATION),
            ProvenanceCode.DOAJ.getCode(),
            Set.of(DataType.JOURNAL),
            ProvenanceCode.EPMC.getCode(),
            Set.of(DataType.DRUG));

    @Override
    public QuerySession prepareQuerySession(ExecutionContext context, DataType dataType) {
      // Mock 实现：返回空的查询会话
      return QuerySession.empty(context.provenanceCode());
    }

    @Override
    public <T> DataFetchResult<T> fetchData(
        ExecutionContext context,
        DataType dataType,
        TypeReference<T> typeRef,
        Batch batch,
        QuerySession querySession) {
      // Mock 实现：返回空数据成功结果
      return DataFetchResult.success(List.of(), dataType, null);
    }

    @Override
    public boolean supports(ProvenanceCode provenanceCode, DataType dataType) {
      if (provenanceCode == null || dataType == null) {
        return false;
      }
      Set<DataType> types = supportedTypes.get(provenanceCode.getCode());
      return types != null && types.contains(dataType);
    }

    @Override
    public Set<DataType> getSupportedTypes(ProvenanceCode provenanceCode) {
      if (provenanceCode == null) {
        return Set.of();
      }
      return supportedTypes.getOrDefault(provenanceCode.getCode(), Set.of());
    }
  }

  // ========== 测试用例 ==========

  @Nested
  @DisplayName("泛型方法测试")
  class GenericMethodTests {

    @Test
    @DisplayName("应该使用泛型方法获取文献数据")
    void should_fetch_literature_data_with_generic_method() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();
      ExecutionContext context = createTestContext(ProvenanceCode.PUBMED);
      TypeReference<CanonicalLiterature> typeRef = new TypeReference<>() {};
      Batch batch = createTestBatch();

      // When: 调用泛型方法
      DataFetchResult<CanonicalLiterature> result =
          port.fetchData(
              context,
              DataType.LITERATURE,
              typeRef,
              batch,
              createTestSession(ProvenanceCode.PUBMED));

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).isNotNull().isEmpty();
      assertThat(result.errorType()).isEqualTo(ErrorType.NONE);
    }

    @Test
    @DisplayName("应该使用泛型方法获取期刊数据")
    void should_fetch_journal_data_with_different_type() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();
      ExecutionContext context = createTestContext(ProvenanceCode.DOAJ);
      TypeReference<DataType.Journal> typeRef = new TypeReference<>() {};
      Batch batch = createTestBatch();

      // When: 调用泛型方法
      DataFetchResult<DataType.Journal> result =
          port.fetchData(
              context, DataType.JOURNAL, typeRef, batch, createTestSession(ProvenanceCode.DOAJ));

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.JOURNAL);
      assertThat(result.data()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("应该使用泛型方法获取药品数据")
    void should_fetch_drug_data_with_different_type() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();
      ExecutionContext context = createTestContext(ProvenanceCode.OPENALEX);
      TypeReference<DataType.Drug> typeRef = new TypeReference<>() {};
      Batch batch = createTestBatch();

      // When: 调用泛型方法
      DataFetchResult<DataType.Drug> result =
          port.fetchData(
              context, DataType.DRUG, typeRef, batch, createTestSession(ProvenanceCode.OPENALEX));

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.DRUG);
      assertThat(result.data()).isNotNull();
    }
  }

  @Nested
  @DisplayName("类型安全性测试")
  class TypeSafetyTests {

    @Test
    @DisplayName("应该保持编译期类型安全")
    void should_maintain_type_safety() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();
      ExecutionContext context = createTestContext(ProvenanceCode.PUBMED);
      TypeReference<CanonicalLiterature> typeRef = new TypeReference<>() {};
      Batch batch = createTestBatch();

      // When: 调用泛型方法
      DataFetchResult<CanonicalLiterature> result =
          port.fetchData(
              context,
              DataType.LITERATURE,
              typeRef,
              batch,
              createTestSession(ProvenanceCode.PUBMED));

      // Then: 返回的 data 应该是 List<CanonicalLiterature>，而不是 List<Object>
      List<CanonicalLiterature> literatures = result.data();
      assertThat(literatures).isNotNull();
      // 编译期类型检查通过：literatures 是 List<CanonicalLiterature> 类型
    }

    @Test
    @DisplayName("应该在不同数据类型间保持类型隔离")
    void should_maintain_type_isolation_between_data_types() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();
      Batch batch = createTestBatch();

      // When: 获取文献数据
      TypeReference<CanonicalLiterature> litTypeRef = new TypeReference<>() {};
      DataFetchResult<CanonicalLiterature> litResult =
          port.fetchData(
              createTestContext(ProvenanceCode.PUBMED),
              DataType.LITERATURE,
              litTypeRef,
              batch,
              createTestSession(ProvenanceCode.PUBMED));

      // When: 获取期刊数据
      TypeReference<DataType.Journal> journalTypeRef = new TypeReference<>() {};
      DataFetchResult<DataType.Journal> journalResult =
          port.fetchData(
              createTestContext(ProvenanceCode.DOAJ),
              DataType.JOURNAL,
              journalTypeRef,
              batch,
              createTestSession(ProvenanceCode.DOAJ));

      // Then: 两种数据类型应该完全隔离
      assertThat(litResult.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(journalResult.dataType()).isEqualTo(DataType.JOURNAL);
      assertThat(litResult.dataType()).isNotEqualTo(journalResult.dataType());
    }
  }

  @Nested
  @DisplayName("supports 方法测试")
  class SupportsMethodTests {

    @Test
    @DisplayName("应该正确判断支持的数据源和数据类型组合")
    void should_check_support_for_provenance_and_data_type() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();

      // Then: PubMed 支持文献和引用
      assertThat(port.supports(ProvenanceCode.PUBMED, DataType.LITERATURE)).isTrue();
      assertThat(port.supports(ProvenanceCode.PUBMED, DataType.CITATION)).isTrue();

      // Then: PubMed 不支持药品
      assertThat(port.supports(ProvenanceCode.PUBMED, DataType.DRUG)).isFalse();

      // Then: DOAJ 支持期刊
      assertThat(port.supports(ProvenanceCode.DOAJ, DataType.JOURNAL)).isTrue();

      // Then: DOAJ 不支持文献
      assertThat(port.supports(ProvenanceCode.DOAJ, DataType.LITERATURE)).isFalse();

      // Then: 未知数据源不支持任何类型
      assertThat(port.supports(null, DataType.LITERATURE)).isFalse();
    }

    @Test
    @DisplayName("应该处理空值情况")
    void should_handle_null_cases() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();

      // Then: 空数据源代码应该返回 false
      assertThat(port.supports(null, DataType.LITERATURE)).isFalse();

      // Then: 空数据类型应该返回 false
      assertThat(port.supports(ProvenanceCode.PUBMED, null)).isFalse();
    }
  }

  @Nested
  @DisplayName("getSupportedTypes 方法测试")
  class GetSupportedTypesTests {

    @Test
    @DisplayName("应该返回指定数据源支持的所有数据类型")
    void should_return_supported_types_for_provenance() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();

      // When & Then: PubMed 支持文献和引用
      Set<DataType> pubmedTypes = port.getSupportedTypes(ProvenanceCode.PUBMED);
      assertThat(pubmedTypes)
          .isNotNull()
          .hasSize(2)
          .contains(DataType.LITERATURE, DataType.CITATION);

      // When & Then: DOAJ 支持期刊
      Set<DataType> doajTypes = port.getSupportedTypes(ProvenanceCode.DOAJ);
      assertThat(doajTypes).isNotNull().hasSize(1).contains(DataType.JOURNAL);

      // When & Then: DrugBank 支持药品
      Set<DataType> drugbankTypes = port.getSupportedTypes(ProvenanceCode.EPMC);
      assertThat(drugbankTypes).isNotNull().hasSize(1).contains(DataType.DRUG);
    }

    @Test
    @DisplayName("应该为未知数据源返回空集合")
    void should_return_empty_set_for_unknown_provenance() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();

      // When: 查询未知数据源
      Set<DataType> unknownTypes = port.getSupportedTypes(null);

      // Then: 返回空集合
      assertThat(unknownTypes).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("应该返回不可变集合")
    void should_return_immutable_set() {
      // Given: Mock 实现
      ProvenanceDataPort port = new MockProvenanceDataPort();

      // When: 获取支持的类型
      Set<DataType> types = port.getSupportedTypes(ProvenanceCode.PUBMED);

      // Then: 尝试修改应该抛出异常（不可变集合）
      assertThatThrownBy(() -> types.add(DataType.DRUG))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("DataFetchResult 泛型化测试")
  class DataFetchResultGenericTests {

    @Test
    @DisplayName("应该创建泛型化的成功结果")
    void should_create_generic_success_result() {
      // When: 创建成功结果（文献类型）
      List<CanonicalLiterature> data = List.of(createMockLiterature());
      DataFetchResult<CanonicalLiterature> result =
          DataFetchResult.success(data, DataType.LITERATURE, "cursor123");

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).hasSize(1);
      assertThat(result.nextCursorToken()).isEqualTo("cursor123");
      assertThat(result.fetchedCount()).isEqualTo(1);
      assertThat(result.errorType()).isEqualTo(ErrorType.NONE);
      assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("应该创建不同类型的成功结果")
    void should_create_success_result_for_different_types() {
      // When: 创建成功结果（期刊类型）
      DataFetchResult<DataType.Journal> result =
          DataFetchResult.success(List.of(), DataType.JOURNAL, null);

      // Then: 验证数据类型
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.JOURNAL);
      assertThat(result.data()).isEmpty();
    }

    @Test
    @DisplayName("应该创建失败结果")
    void should_create_failure_result() {
      // When: 创建失败结果
      DataFetchResult<CanonicalLiterature> result =
          DataFetchResult.failure(DataType.LITERATURE, "Network timeout", ErrorType.RETRIABLE);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isFalse();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.errorMessage()).isEqualTo("Network timeout");
      assertThat(result.errorType()).isEqualTo(ErrorType.RETRIABLE);
      assertThat(result.data()).isEmpty();
      assertThat(result.fetchedCount()).isZero();
      assertThat(result.nextCursorToken()).isNull();
    }

    @Test
    @DisplayName("应该创建不可重试的失败结果")
    void should_create_non_retriable_failure_result() {
      // When: 创建不可重试的失败结果
      DataFetchResult<CanonicalLiterature> result =
          DataFetchResult.failure(
              DataType.LITERATURE, "Invalid credentials", ErrorType.NON_RETRIABLE);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(ErrorType.NON_RETRIABLE);
      assertThat(result.errorMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    @DisplayName("应该创建部分成功结果")
    void should_create_partial_success_result() {
      // When: 创建部分成功结果
      List<CanonicalLiterature> data = List.of(createMockLiterature());
      DataFetchResult<CanonicalLiterature> result =
          DataFetchResult.partialSuccess(
              data, DataType.LITERATURE, "cursor456", "Some records failed");

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).hasSize(1);
      assertThat(result.nextCursorToken()).isEqualTo("cursor456");
      assertThat(result.errorMessage()).isEqualTo("Some records failed");
      assertThat(result.errorType()).isEqualTo(ErrorType.PARTIAL_SUCCESS);
      assertThat(result.fetchedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该支持扩展元数据")
    void should_support_metadata() {
      // When: 创建带元数据的结果
      Map<String, Object> metadata = Map.of("apiVersion", "v3", "rateLimit", 1000);
      DataFetchResult<CanonicalLiterature> result =
          DataFetchResult.<CanonicalLiterature>builder()
              .success(true)
              .data(List.of())
              .dataType(DataType.LITERATURE)
              .metadata(metadata)
              .errorType(ErrorType.NONE)
              .fetchedCount(0)
              .build();

      // Then: 验证元数据
      assertThat(result.metadata())
          .isNotNull()
          .hasSize(2)
          .containsEntry("apiVersion", "v3")
          .containsEntry("rateLimit", 1000);
    }

    @Test
    @DisplayName("应该处理空数据情况")
    void should_handle_null_data() {
      // When: 创建空数据结果
      DataFetchResult<CanonicalLiterature> result =
          DataFetchResult.success(null, DataType.LITERATURE, null);

      // Then: data 应该是空列表，而不是 null
      assertThat(result.data()).isNotNull().isEmpty();
      assertThat(result.fetchedCount()).isZero();
    }
  }

  @Nested
  @DisplayName("ErrorType 测试")
  class ErrorTypeTests {

    @Test
    @DisplayName("应该包含所有错误类型枚举常量")
    void should_contain_all_error_type_constants() {
      // Then: 验证所有枚举常量存在
      assertThat(ErrorType.values())
          .contains(
              ErrorType.NONE,
              ErrorType.RETRIABLE,
              ErrorType.NON_RETRIABLE,
              ErrorType.PARTIAL_SUCCESS);
    }

    @Test
    @DisplayName("应该正确判断可重试错误")
    void should_identify_retriable_errors() {
      // Given: 可重试错误结果
      DataFetchResult<CanonicalLiterature> retriableResult =
          DataFetchResult.failure(DataType.LITERATURE, "Timeout", ErrorType.RETRIABLE);

      // Then: 应该标记为可重试
      assertThat(retriableResult.errorType()).isEqualTo(ErrorType.RETRIABLE);
      assertThat(retriableResult.success()).isFalse();
    }

    @Test
    @DisplayName("应该正确判断不可重试错误")
    void should_identify_non_retriable_errors() {
      // Given: 不可重试错误结果
      DataFetchResult<CanonicalLiterature> nonRetriableResult =
          DataFetchResult.failure(DataType.LITERATURE, "Auth failed", ErrorType.NON_RETRIABLE);

      // Then: 应该标记为不可重试
      assertThat(nonRetriableResult.errorType()).isEqualTo(ErrorType.NON_RETRIABLE);
      assertThat(nonRetriableResult.success()).isFalse();
    }
  }

  // ========== 测试辅助方法 ==========

  /** 创建测试用的执行上下文 */
  private static ExecutionContext createTestContext(ProvenanceCode provenanceCode) {
    // 简化的 Mock 上下文（实际实现会更复杂）
    return new ExecutionContext(
        1L, // taskId
        1L, // runId
        1L, // planId
        1L, // sliceId
        1L, // scheduleInstanceId
        provenanceCode, // provenanceCode
        "test-op", // operationCode
        DataType.LITERATURE, // dataType
        null, // configSnapshot
        null, // exprHash
        null, // compiledQuery
        null, // compiledParams
        null, // normalizedExpression
        null // windowSpec
        );
  }

  /** 创建测试用的批次定义 */
  private static Batch createTestBatch() {
    // 简化的 Mock 批次（使用新的构造方法）
    return new Batch(
        1, // batchNo
        "test-query", // query
        0, // offset
        100 // limit
        );
  }

  /** 创建测试用的查询会话 */
  private static QuerySession createTestSession(ProvenanceCode provenanceCode) {
    return QuerySession.empty(provenanceCode);
  }

  /** 创建 Mock 文献对象 */
  private static CanonicalLiterature createMockLiterature() {
    // 简化的 Mock 文献（使用 builder）
    return CanonicalLiterature.builder().title("Test Literature").build();
  }
}
