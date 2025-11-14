package com.patra.starter.provenance.common.provider;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.processor.DataProcessor;
import com.patra.starter.provenance.common.processor.ProcessResult;
import com.patra.starter.provenance.common.processor.ProviderContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DataSourceProvider接口测试
 *
 * <p>测试v2.0多数据类型架构的核心提供者接口。
 *
 * <p><strong>TDD测试策略</strong>：
 *
 * <ol>
 *   <li>测试基本接口契约（getProvenanceCode、getSupportedDataTypes）
 *   <li>测试类型支持检查（supports方法）
 *   <li>测试泛型fetchData方法（多种数据类型）
 *   <li>测试getProcessor可选方法
 *   <li>测试ProviderResult泛型化
 * </ol>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
@DisplayName("DataSourceProvider接口测试")
class DataSourceProviderTest {

  // ==================== 1. 基本接口契约测试 ====================

  @Nested
  @DisplayName("基本接口契约")
  class BasicContractTests {

    @Test
    @DisplayName("应该返回数据源代码")
    void should_return_provenance_code() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When: 获取数据源代码
      ProvenanceCode provenanceCode = provider.getProvenanceCode();

      // Then: 应该返回正确的代码
      assertThat(provenanceCode).isEqualTo(ProvenanceCode.PUBMED);
    }

    @Test
    @DisplayName("应该返回支持的数据类型集合")
    void should_return_supported_data_types() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When: 获取支持的数据类型
      Set<DataType> supportedTypes = provider.getSupportedDataTypes();

      // Then: 应该包含正确的类型
      assertThat(supportedTypes)
          .isNotNull()
          .isNotEmpty()
          .contains(DataType.LITERATURE, DataType.CITATION);
    }

    @Test
    @DisplayName("支持的数据类型集合应该是不可变的")
    void supported_data_types_should_be_immutable() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When: 获取支持的数据类型
      Set<DataType> supportedTypes = provider.getSupportedDataTypes();

      // Then: 尝试修改应该抛出异常
      assertThatThrownBy(() -> supportedTypes.add(DataType.DRUG))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  // ==================== 2. 类型支持检查测试 ====================

  @Nested
  @DisplayName("类型支持检查")
  class TypeSupportTests {

    @Test
    @DisplayName("应该正确判断支持的数据类型")
    void should_support_registered_data_types() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When & Then: 支持的类型应该返回true
      assertThat(provider.supports(DataType.LITERATURE)).isTrue();
      assertThat(provider.supports(DataType.CITATION)).isTrue();
    }

    @Test
    @DisplayName("应该拒绝不支持的数据类型")
    void should_not_support_unregistered_data_types() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When & Then: 不支持的类型应该返回false
      assertThat(provider.supports(DataType.DRUG)).isFalse();
      assertThat(provider.supports(DataType.JOURNAL)).isFalse();
    }
  }

  // ==================== 3. 泛型fetchData方法测试 ====================

  @Nested
  @DisplayName("泛型fetchData方法")
  class FetchDataTests {

    @Test
    @DisplayName("应该成功获取LITERATURE数据")
    void should_fetch_literature_data() {
      // Given: 创建Mock Provider和请求
      DataSourceProvider provider = new MockPubmedProvider();
      ProviderRequest request = createMockRequest();

      // When: 获取LITERATURE数据
      ProviderResult<CanonicalLiterature> result =
          provider.fetchData(request, DataType.LITERATURE, CanonicalLiterature.class);

      // Then: 应该返回成功结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).isNotNull();
    }

    @Test
    @DisplayName("应该成功获取CITATION数据")
    void should_fetch_citation_data() {
      // Given: 创建Mock Provider和请求
      DataSourceProvider provider = new MockPubmedProvider();
      ProviderRequest request = createMockRequest();

      // When: 获取CITATION数据
      ProviderResult<DataType.Citation> result =
          provider.fetchData(request, DataType.CITATION, DataType.Citation.class);

      // Then: 应该返回成功结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.CITATION);
    }

    @Test
    @DisplayName("应该拒绝不支持的数据类型")
    void should_reject_unsupported_data_type() {
      // Given: 创建Mock Provider和请求
      DataSourceProvider provider = new MockPubmedProvider();
      ProviderRequest request = createMockRequest();

      // When: 尝试获取不支持的数据类型
      ProviderResult<DataType.Drug> result =
          provider.fetchData(request, DataType.DRUG, DataType.Drug.class);

      // Then: 应该返回失败结果
      assertThat(result).isNotNull();
      assertThat(result.success()).isFalse();
      assertThat(result.errorType()).isEqualTo(ProviderResult.ErrorType.NON_RETRIABLE);
      assertThat(result.errorMessage()).contains("不支持", "DRUG");
    }

    @Test
    @DisplayName("应该在fetchedCount中反映实际获取的数据量")
    void should_reflect_fetched_count() {
      // Given: 创建Mock Provider和请求
      DataSourceProvider provider = new MockPubmedProvider();
      ProviderRequest request = createMockRequest();

      // When: 获取数据
      ProviderResult<CanonicalLiterature> result =
          provider.fetchData(request, DataType.LITERATURE, CanonicalLiterature.class);

      // Then: fetchedCount应该等于data.size()
      assertThat(result.fetchedCount()).isEqualTo(result.data().size());
    }
  }

  // ==================== 4. getProcessor可选方法测试 ====================

  @Nested
  @DisplayName("getProcessor可选方法")
  class GetProcessorTests {

    @Test
    @DisplayName("应该为支持的类型返回Processor")
    void should_return_processor_for_supported_type() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When: 获取LITERATURE的Processor
      Optional<DataProcessor<?>> processor = provider.getProcessor(DataType.LITERATURE);

      // Then: 应该返回非空的Optional
      assertThat(processor).isPresent();
    }

    @Test
    @DisplayName("应该为不支持的类型返回空Optional")
    void should_return_empty_for_unsupported_type() {
      // Given: 创建Mock Provider
      DataSourceProvider provider = new MockPubmedProvider();

      // When: 获取DRUG的Processor
      Optional<DataProcessor<?>> processor = provider.getProcessor(DataType.DRUG);

      // Then: 应该返回空Optional
      assertThat(processor).isEmpty();
    }
  }

  // ==================== 5. ProviderResult泛型化测试 ====================

  @Nested
  @DisplayName("ProviderResult泛型化")
  class ProviderResultGenericTests {

    @Test
    @DisplayName("应该创建泛型成功结果")
    void should_create_generic_success_result() {
      // Given: 创建Mock数据
      List<CanonicalLiterature> literatures = List.of(createMockLiterature("PMID001"));

      // When: 创建成功结果
      ProviderResult<CanonicalLiterature> result =
          ProviderResult.success(literatures, DataType.LITERATURE, "cursor123");

      // Then: 应该包含正确的信息
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).hasSize(1);
      assertThat(result.nextCursorToken()).isEqualTo("cursor123");
      assertThat(result.fetchedCount()).isEqualTo(1);
      assertThat(result.errorType()).isEqualTo(ProviderResult.ErrorType.NONE);
    }

    @Test
    @DisplayName("应该创建泛型失败结果")
    void should_create_generic_failure_result() {
      // When: 创建失败结果
      ProviderResult<CanonicalLiterature> result =
          ProviderResult.failure(DataType.LITERATURE, "网络错误", ProviderResult.ErrorType.RETRIABLE);

      // Then: 应该包含错误信息
      assertThat(result.success()).isFalse();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).isEmpty();
      assertThat(result.errorMessage()).isEqualTo("网络错误");
      assertThat(result.errorType()).isEqualTo(ProviderResult.ErrorType.RETRIABLE);
    }

    @Test
    @DisplayName("应该创建泛型部分成功结果")
    void should_create_generic_partial_success_result() {
      // Given: 创建Mock数据
      List<CanonicalLiterature> literatures = List.of(createMockLiterature("PMID001"));

      // When: 创建部分成功结果
      ProviderResult<CanonicalLiterature> result =
          ProviderResult.partialSuccess(literatures, DataType.LITERATURE, "cursor456", "部分数据转换失败");

      // Then: 应该包含警告信息
      assertThat(result.success()).isTrue();
      assertThat(result.dataType()).isEqualTo(DataType.LITERATURE);
      assertThat(result.data()).hasSize(1);
      assertThat(result.errorMessage()).isEqualTo("部分数据转换失败");
      assertThat(result.errorType()).isEqualTo(ProviderResult.ErrorType.PARTIAL_SUCCESS);
    }

    @Test
    @DisplayName("数据列表应该是不可变的")
    void data_list_should_be_immutable() {
      // Given: 创建成功结果
      List<CanonicalLiterature> literatures = List.of(createMockLiterature("PMID001"));
      ProviderResult<CanonicalLiterature> result =
          ProviderResult.success(literatures, DataType.LITERATURE, null);

      // When & Then: 尝试修改数据列表应该抛出异常
      assertThatThrownBy(() -> result.data().add(createMockLiterature("PMID002")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  // ==================== Mock实现类 ====================

  /** Mock PubMed Provider实现（用于测试） */
  private static class MockPubmedProvider implements DataSourceProvider {

    private static final Set<DataType> SUPPORTED_TYPES =
        Set.of(DataType.LITERATURE, DataType.CITATION);

    @Override
    public ProvenanceCode getProvenanceCode() {
      return ProvenanceCode.PUBMED;
    }

    @Override
    public Set<DataType> getSupportedDataTypes() {
      return SUPPORTED_TYPES;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ProviderResult<T> fetchData(
        ProviderRequest request, DataType dataType, Class<T> targetClass) {

      // 检查是否支持该数据类型
      if (!supports(dataType)) {
        return (ProviderResult<T>)
            ProviderResult.failure(
                dataType, "不支持的数据类型: " + dataType, ProviderResult.ErrorType.NON_RETRIABLE);
      }

      // Mock实现：返回空数据
      if (dataType == DataType.LITERATURE) {
        return (ProviderResult<T>) ProviderResult.success(List.of(), DataType.LITERATURE, null);
      } else if (dataType == DataType.CITATION) {
        return (ProviderResult<T>) ProviderResult.success(List.of(), DataType.CITATION, null);
      }

      return (ProviderResult<T>)
          ProviderResult.failure(dataType, "未实现的数据类型", ProviderResult.ErrorType.NON_RETRIABLE);
    }

    @Override
    public Optional<DataProcessor<?>> getProcessor(DataType dataType) {
      if (supports(dataType)) {
        return Optional.of(new MockLiteratureProcessor());
      }
      return Optional.empty();
    }
  }

  /** Mock Literature Processor（用于测试） */
  private static class MockLiteratureProcessor implements DataProcessor<CanonicalLiterature> {

    @Override
    public DataType getDataType() {
      return DataType.LITERATURE;
    }

    @Override
    public ProcessResult<CanonicalLiterature> process(
        ProviderRequest request, ProviderContext context) {
      return ProcessResult.success(List.of(), null);
    }

    @Override
    public com.patra.starter.provenance.common.processor.ValidationResult validate(
        CanonicalLiterature data) {
      return com.patra.starter.provenance.common.processor.ValidationResult.success();
    }

    @Override
    public CanonicalLiterature transform(Object rawData) {
      return null;
    }
  }

  // ==================== 辅助方法 ====================

  /** 创建Mock请求 */
  private static ProviderRequest createMockRequest() {
    return ProviderRequest.builder()
        .executionParams(new BatchExecutionParams("test", null))
        .metadata(BatchMetadata.first())
        .build();
  }

  /** 创建Mock文献 */
  private static CanonicalLiterature createMockLiterature(String identifier) {
    // CanonicalLiterature使用Builder模式
    return CanonicalLiterature.builder()
        .title("Test Literature")
        .abstractContent(CanonicalLiterature.Abstract.builder().text("Test Abstract").build())
        .authors(List.of())
        .journal(null)
        .identifiers(
            List.of(
                CanonicalLiterature.Identifier.builder().type("PMID").value(identifier).build()))
        .dates(null)
        .keywords(List.of())
        .build();
  }
}
