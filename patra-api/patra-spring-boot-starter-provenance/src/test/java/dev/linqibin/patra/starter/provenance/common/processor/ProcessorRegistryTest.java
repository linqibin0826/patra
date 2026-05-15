package dev.linqibin.patra.starter.provenance.common.processor;

import static org.assertj.core.api.Assertions.*;

import dev.linqibin.patra.starter.provenance.common.provider.ProviderRequest;
import dev.linqibin.patra.common.model.CanonicalPublication;
import dev.linqibin.patra.common.model.DataType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ProcessorRegistry单元测试
///
/// 测试策略：
///
/// - 自动注册功能测试
///   - Processor查找测试（getProcessor、findProcessor）
///   - 类型支持检查测试（supports、getSupportedTypes）
///   - 异常情况测试（重复注册、Processor不存在、空列表）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProcessorRegistry注册表测试")
class ProcessorRegistryTest {

  // ==================== 自动注册测试 ====================

  @Test
  @DisplayName("应该自动注册所有Processor")
  void should_auto_register_processors_from_spring() {
    // Given: 创建多个Processor
    List<DataProcessor<?>> processors =
        List.of(new MockPublicationProcessor(), new MockJournalProcessor());

    // When: 自动注册
    ProcessorRegistry registry = new ProcessorRegistry(processors);

    // Then: 验证注册成功
    assertThat(registry.supports(DataType.PUBLICATION)).isTrue();
    assertThat(registry.supports(DataType.JOURNAL)).isTrue();
    assertThat(registry.getSupportedTypes()).hasSize(2);
  }

  // ==================== getProcessor方法测试 ====================

  @Test
  @DisplayName("应该根据DataType获取Processor")
  void should_get_processor_by_data_type() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When
    DataProcessor<CanonicalPublication> processor = registry.getProcessor(DataType.PUBLICATION);

    // Then
    assertThat(processor).isNotNull();
    assertThat(processor.getDataType()).isEqualTo(DataType.PUBLICATION);
  }

  @Test
  @DisplayName("应该在Processor不存在时抛出异常")
  void should_throw_exception_when_processor_not_found() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When & Then
    assertThatThrownBy(() -> registry.getProcessor(DataType.DRUG))
        .isInstanceOf(ProcessorNotFoundException.class)
        .hasMessageContaining("未找到DataType的Processor")
        .hasMessageContaining("DRUG");
  }

  // ==================== findProcessor方法测试 ====================

  @Test
  @DisplayName("应该使用Optional返回存在的Processor")
  void should_find_processor_with_optional() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When
    Optional<DataProcessor<?>> processor = registry.findProcessor(DataType.PUBLICATION);

    // Then
    assertThat(processor).isPresent();
    assertThat(processor.get().getDataType()).isEqualTo(DataType.PUBLICATION);
  }

  @Test
  @DisplayName("应该在Processor不存在时返回空Optional")
  void should_return_empty_optional_when_not_found() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When
    Optional<DataProcessor<?>> processor = registry.findProcessor(DataType.DRUG);

    // Then
    assertThat(processor).isEmpty();
  }

  // ==================== supports方法测试 ====================

  @Test
  @DisplayName("应该检查DataType是否支持")
  void should_check_data_type_support() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When & Then: 支持的类型
    assertThat(registry.supports(DataType.PUBLICATION)).isTrue();
    assertThat(registry.supports(DataType.JOURNAL)).isTrue();

    // When & Then: 不支持的类型
    assertThat(registry.supports(DataType.DRUG)).isFalse();
    assertThat(registry.supports(DataType.CITATION)).isFalse();
  }

  // ==================== getSupportedTypes方法测试 ====================

  @Test
  @DisplayName("应该返回所有支持的数据类型")
  void should_return_all_supported_types() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When
    Set<DataType> types = registry.getSupportedTypes();

    // Then
    assertThat(types).hasSize(2).contains(DataType.PUBLICATION, DataType.JOURNAL);
  }

  @Test
  @DisplayName("返回的数据类型集合应该是不可变的")
  void should_return_immutable_supported_types() {
    // Given
    ProcessorRegistry registry = createTestRegistry();

    // When
    Set<DataType> types = registry.getSupportedTypes();

    // Then: 验证不可变性
    assertThatThrownBy(() -> types.add(DataType.DRUG))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ==================== 重复注册检测测试 ====================

  @Test
  @DisplayName("应该检测重复注册并只保留第一个Processor")
  void should_detect_duplicate_registration() {
    // Given: 两个相同类型的Processor
    List<DataProcessor<?>> processors =
        List.of(new MockPublicationProcessor(), new AnotherPublicationProcessor());

    // When: 自动注册
    ProcessorRegistry registry = new ProcessorRegistry(processors);

    // Then: 验证只保留第一个注册的Processor
    DataProcessor<?> processor = registry.getProcessor(DataType.PUBLICATION);
    assertThat(processor)
        .isInstanceOf(MockPublicationProcessor.class)
        .isNotInstanceOf(AnotherPublicationProcessor.class);
  }

  // ==================== 空Processor列表测试 ====================

  @Test
  @DisplayName("应该处理空Processor列表")
  void should_handle_empty_processor_list() {
    // Given
    ProcessorRegistry registry = new ProcessorRegistry(List.of());

    // When
    Set<DataType> types = registry.getSupportedTypes();

    // Then
    assertThat(types).isEmpty();
    assertThat(registry.supports(DataType.PUBLICATION)).isFalse();
  }

  @Test
  @DisplayName("应该处理null Processor列表")
  void should_handle_null_processor_list() {
    // Given & When
    ProcessorRegistry registry = new ProcessorRegistry(null);

    // When
    Set<DataType> types = registry.getSupportedTypes();

    // Then
    assertThat(types).isEmpty();
    assertThat(registry.supports(DataType.PUBLICATION)).isFalse();
  }

  // ==================== 边界条件测试 ====================

  @Test
  @DisplayName("应该处理单个Processor注册")
  void should_handle_single_processor_registration() {
    // Given
    List<DataProcessor<?>> processors = List.of(new MockPublicationProcessor());

    // When
    ProcessorRegistry registry = new ProcessorRegistry(processors);

    // Then
    assertThat(registry.getSupportedTypes()).hasSize(1);
    assertThat(registry.supports(DataType.PUBLICATION)).isTrue();
    assertThat(registry.supports(DataType.JOURNAL)).isFalse();
  }

  @Test
  @DisplayName("应该处理多种数据类型的Processor注册")
  void should_handle_multiple_data_type_processors() {
    // Given: 创建多种类型的Processor
    List<DataProcessor<?>> processors =
        List.of(
            new MockPublicationProcessor(),
            new MockJournalProcessor(),
            new MockCitationProcessor());

    // When
    ProcessorRegistry registry = new ProcessorRegistry(processors);

    // Then
    assertThat(registry.getSupportedTypes()).hasSize(3);
    assertThat(registry.supports(DataType.PUBLICATION)).isTrue();
    assertThat(registry.supports(DataType.JOURNAL)).isTrue();
    assertThat(registry.supports(DataType.CITATION)).isTrue();
  }

  // ==================== 辅助方法 ====================

  /// 创建测试用的ProcessorRegistry
  private ProcessorRegistry createTestRegistry() {
    List<DataProcessor<?>> processors =
        List.of(new MockPublicationProcessor(), new MockJournalProcessor());
    return new ProcessorRegistry(processors);
  }

  // ==================== Mock实现类 ====================

  /// Mock文献Processor
  static class MockPublicationProcessor implements DataProcessor<CanonicalPublication> {
    @Override
    public DataType getDataType() {
      return DataType.PUBLICATION;
    }

    @Override
    public ProcessResult<CanonicalPublication> process(
        ProviderRequest request, ProviderContext context) {
      return ProcessResult.success(List.of(), null);
    }

    @Override
    public ValidationResult validate(CanonicalPublication data) {
      return ValidationResult.success();
    }

    @Override
    public CanonicalPublication transform(Object rawData) throws TransformationException {
      return null;
    }
  }

  /// Mock期刊Processor
  static class MockJournalProcessor implements DataProcessor<MockJournal> {
    @Override
    public DataType getDataType() {
      return DataType.JOURNAL;
    }

    @Override
    public ProcessResult<MockJournal> process(ProviderRequest request, ProviderContext context) {
      return ProcessResult.success(List.of(), null);
    }

    @Override
    public ValidationResult validate(MockJournal data) {
      return ValidationResult.success();
    }

    @Override
    public MockJournal transform(Object rawData) throws TransformationException {
      return null;
    }
  }

  /// Mock引用Processor
  static class MockCitationProcessor implements DataProcessor<MockCitation> {
    @Override
    public DataType getDataType() {
      return DataType.CITATION;
    }

    @Override
    public ProcessResult<MockCitation> process(ProviderRequest request, ProviderContext context) {
      return ProcessResult.success(List.of(), null);
    }

    @Override
    public ValidationResult validate(MockCitation data) {
      return ValidationResult.success();
    }

    @Override
    public MockCitation transform(Object rawData) throws TransformationException {
      return null;
    }
  }

  /// 另一个文献Processor（用于测试重复注册）
  static class AnotherPublicationProcessor implements DataProcessor<CanonicalPublication> {
    @Override
    public DataType getDataType() {
      return DataType.PUBLICATION;
    }

    @Override
    public ProcessResult<CanonicalPublication> process(
        ProviderRequest request, ProviderContext context) {
      return ProcessResult.success(List.of(), null);
    }

    @Override
    public ValidationResult validate(CanonicalPublication data) {
      return ValidationResult.success();
    }

    @Override
    public CanonicalPublication transform(Object rawData) throws TransformationException {
      return null;
    }
  }

  // ==================== Mock数据类型 ====================

  /// Mock期刊数据类型（用于测试）
  static class MockJournal {
    private String issn;
    private String name;
  }

  /// Mock引用数据类型（用于测试）
  static class MockCitation {
    private String citingPmid;
    private String citedPmid;
  }
}
