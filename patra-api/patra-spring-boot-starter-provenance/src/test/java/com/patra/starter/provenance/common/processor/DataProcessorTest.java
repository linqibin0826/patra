package com.patra.starter.provenance.common.processor;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.provider.BatchExecutionParams;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DataProcessor 接口契约测试
 *
 * <p>测试策略：使用Mock实现进行接口契约测试，验证接口设计的正确性。
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
@DisplayName("DataProcessor 策略接口测试")
class DataProcessorTest {

  // ==================== 测试辅助方法 ====================

  /** 创建测试用的ProviderRequest */
  private ProviderRequest createTestRequest() {
    return ProviderRequest.builder()
        .config(createTestConfig())
        .executionParams(createTestExecutionParams())
        .build();
  }

  /** 创建测试用的ProvenanceConfig */
  private ProvenanceConfig createTestConfig() {
    return new ProvenanceConfig("https://api.test.com", null, null, null, null, null, null);
  }

  /** 创建测试用的BatchExecutionParams */
  private BatchExecutionParams createTestExecutionParams() {
    return new BatchExecutionParams("test query", null);
  }

  /** 创建测试用的ProviderContext */
  private ProviderContext createTestContext() {
    return ProviderContext.builder()
        .config(createTestConfig())
        .client(new Object())
        .attributes(new HashMap<>())
        .build();
  }

  /** 创建测试用的CanonicalLiterature */
  private CanonicalLiterature createValidLiterature() {
    return CanonicalLiterature.builder()
        .title("Test Article")
        .abstractContent(CanonicalLiterature.Abstract.builder().text("Test abstract").build())
        .identifiers(
            List.of(CanonicalLiterature.Identifier.builder().type("PMID").value("12345").build()))
        .build();
  }

  /** 创建测试用的无效CanonicalLiterature（缺少必填字段） */
  private CanonicalLiterature createInvalidLiterature() {
    return CanonicalLiterature.builder()
        .abstractContent(
            CanonicalLiterature.Abstract.builder().text("Test abstract without title").build())
        .build();
  }

  // ==================== Mock实现 ====================

  /** Mock实现：用于测试接口契约 */
  static class MockLiteratureProcessor implements DataProcessor<CanonicalLiterature> {

    private final ProcessResult<CanonicalLiterature> mockResult;
    private final ValidationResult mockValidationResult;
    private final CanonicalLiterature mockTransformResult;

    public MockLiteratureProcessor() {
      this(ProcessResult.success(List.of(), null), ValidationResult.success(), null);
    }

    public MockLiteratureProcessor(
        ProcessResult<CanonicalLiterature> mockResult,
        ValidationResult mockValidationResult,
        CanonicalLiterature mockTransformResult) {
      this.mockResult = mockResult;
      this.mockValidationResult = mockValidationResult;
      this.mockTransformResult = mockTransformResult;
    }

    @Override
    public DataType getDataType() {
      return DataType.LITERATURE;
    }

    @Override
    public ProcessResult<CanonicalLiterature> process(
        ProviderRequest request, ProviderContext context) {
      return mockResult;
    }

    @Override
    public ValidationResult validate(CanonicalLiterature data) {
      return mockValidationResult;
    }

    @Override
    public CanonicalLiterature transform(Object rawData) throws TransformationException {
      if (mockTransformResult == null) {
        throw new TransformationException("Mock transformation failed");
      }
      return mockTransformResult;
    }
  }

  // ==================== 接口基本契约测试 ====================

  @Nested
  @DisplayName("接口基本契约测试")
  class InterfaceContractTest {

    @Test
    @DisplayName("should_return_correct_data_type")
    void shouldReturnCorrectDataType() {
      // Given: 创建Processor
      DataProcessor<CanonicalLiterature> processor = new MockLiteratureProcessor();

      // When: 获取数据类型
      DataType dataType = processor.getDataType();

      // Then: 返回正确的数据类型
      assertThat(dataType).isEqualTo(DataType.LITERATURE);
    }

    @Test
    @DisplayName("should_support_correct_data_type")
    void shouldSupportCorrectDataType() {
      // Given: 创建Processor
      DataProcessor<CanonicalLiterature> processor = new MockLiteratureProcessor();

      // When & Then: 支持对应的数据类型
      assertThat(processor.supports(DataType.LITERATURE)).isTrue();
      assertThat(processor.supports(DataType.JOURNAL)).isFalse();
      assertThat(processor.supports(DataType.DRUG)).isFalse();
    }
  }

  // ==================== process方法测试 ====================

  @Nested
  @DisplayName("process方法测试")
  class ProcessMethodTest {

    @Test
    @DisplayName("should_process_data_successfully")
    void shouldProcessDataSuccessfully() {
      // Given: 准备成功的处理结果
      CanonicalLiterature literature = createValidLiterature();
      ProcessResult<CanonicalLiterature> successResult =
          ProcessResult.success(List.of(literature), "cursor123");

      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(successResult, ValidationResult.success(), null);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalLiterature> result = processor.process(request, context);

      // Then: 返回成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).hasSize(1);
      assertThat(result.data().get(0)).isEqualTo(literature);
      assertThat(result.nextCursor()).isEqualTo("cursor123");
      assertThat(result.status()).isEqualTo(ProcessStatus.SUCCESS);
      assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("should_handle_process_failure")
    void shouldHandleProcessFailure() {
      // Given: 准备失败的处理结果
      ProcessResult<CanonicalLiterature> failureResult = ProcessResult.failure("Network timeout");

      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(failureResult, ValidationResult.success(), null);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalLiterature> result = processor.process(request, context);

      // Then: 返回失败结果
      assertThat(result.success()).isFalse();
      assertThat(result.data()).isNull();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.status()).isEqualTo(ProcessStatus.FAILED);
      assertThat(result.errorMessage()).isEqualTo("Network timeout");
    }

    @Test
    @DisplayName("should_handle_partial_success")
    void shouldHandlePartialSuccess() {
      // Given: 准备部分成功的处理结果
      CanonicalLiterature literature = createValidLiterature();
      ProcessResult<CanonicalLiterature> partialResult =
          ProcessResult.partialSuccess(
              List.of(literature), "cursor456", "2 out of 10 records failed to transform");

      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(partialResult, ValidationResult.success(), null);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalLiterature> result = processor.process(request, context);

      // Then: 返回部分成功结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).hasSize(1);
      assertThat(result.nextCursor()).isEqualTo("cursor456");
      assertThat(result.status()).isEqualTo(ProcessStatus.PARTIAL_SUCCESS);
      assertThat(result.errorMessage()).isEqualTo("2 out of 10 records failed to transform");
    }

    @Test
    @DisplayName("should_handle_validation_error")
    void shouldHandleValidationError() {
      // Given: 准备验证错误的处理结果
      ProcessResult<CanonicalLiterature> validationErrorResult =
          ProcessResult.validationError("Title is required");

      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(validationErrorResult, ValidationResult.success(), null);

      ProviderRequest request = createTestRequest();
      ProviderContext context = createTestContext();

      // When: 处理数据
      ProcessResult<CanonicalLiterature> result = processor.process(request, context);

      // Then: 返回验证错误结果
      assertThat(result.success()).isFalse();
      assertThat(result.status()).isEqualTo(ProcessStatus.VALIDATION_ERROR);
      assertThat(result.errorMessage()).isEqualTo("Title is required");
    }
  }

  // ==================== transform方法测试 ====================

  @Nested
  @DisplayName("transform方法测试")
  class TransformMethodTest {

    @Test
    @DisplayName("should_transform_raw_data_to_target_type")
    void shouldTransformRawDataToTargetType() throws TransformationException {
      // Given: 准备转换结果
      CanonicalLiterature expectedResult = createValidLiterature();
      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(
              ProcessResult.success(List.of(), null), ValidationResult.success(), expectedResult);

      Object rawData = new Object(); // 模拟原始数据

      // When: 转换数据
      CanonicalLiterature result = processor.transform(rawData);

      // Then: 返回转换后的数据
      assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("should_throw_exception_on_invalid_transform")
    void shouldThrowExceptionOnInvalidTransform() {
      // Given: 准备会抛出异常的Processor
      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(
              ProcessResult.success(List.of(), null), ValidationResult.success(), null // 会导致抛出异常
              );

      Object rawData = new Object();

      // When & Then: 抛出TransformationException
      assertThatThrownBy(() -> processor.transform(rawData))
          .isInstanceOf(TransformationException.class)
          .hasMessage("Mock transformation failed");
    }

    @Test
    @DisplayName("should_throw_exception_with_cause")
    void shouldThrowExceptionWithCause() {
      // Given: 创建带原因的异常
      Exception cause = new RuntimeException("Original error");
      TransformationException exception =
          new TransformationException("Transformation failed", cause);

      // When & Then: 验证异常信息
      assertThat(exception).hasMessage("Transformation failed").hasCause(cause);
    }
  }

  // ==================== validate方法测试 ====================

  @Nested
  @DisplayName("validate方法测试")
  class ValidateMethodTest {

    @Test
    @DisplayName("should_validate_data_successfully")
    void shouldValidateDataSuccessfully() {
      // Given: 准备成功的验证结果
      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(
              ProcessResult.success(List.of(), null), ValidationResult.success(), null);

      CanonicalLiterature data = createValidLiterature();

      // When: 验证数据
      ValidationResult result = processor.validate(data);

      // Then: 返回成功的验证结果
      assertThat(result.isValid()).isTrue();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("should_return_validation_errors")
    void shouldReturnValidationErrors() {
      // Given: 准备失败的验证结果
      List<String> expectedErrors = List.of("Title is required", "PMID is required");
      ValidationResult failureResult = ValidationResult.failure(expectedErrors);

      DataProcessor<CanonicalLiterature> processor =
          new MockLiteratureProcessor(ProcessResult.success(List.of(), null), failureResult, null);

      CanonicalLiterature data = createInvalidLiterature();

      // When: 验证数据
      ValidationResult result = processor.validate(data);

      // Then: 返回验证错误
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(2);
      assertThat(result.errors()).containsExactlyElementsOf(expectedErrors);
    }
  }

  // ==================== ProcessResult测试 ====================

  @Nested
  @DisplayName("ProcessResult测试")
  class ProcessResultTest {

    @Test
    @DisplayName("should_create_success_result")
    void shouldCreateSuccessResult() {
      // Given: 准备数据
      List<CanonicalLiterature> data = List.of(createValidLiterature());
      String cursor = "cursor123";

      // When: 创建成功结果
      ProcessResult<CanonicalLiterature> result = ProcessResult.success(data, cursor);

      // Then: 验证结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).isEqualTo(data);
      assertThat(result.nextCursor()).isEqualTo(cursor);
      assertThat(result.status()).isEqualTo(ProcessStatus.SUCCESS);
      assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("should_create_success_result_with_null_cursor")
    void shouldCreateSuccessResultWithNullCursor() {
      // Given: 准备数据（最后一页，无下一页游标）
      List<CanonicalLiterature> data = List.of(createValidLiterature());

      // When: 创建成功结果
      ProcessResult<CanonicalLiterature> result = ProcessResult.success(data, null);

      // Then: 验证结果
      assertThat(result.success()).isTrue();
      assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("should_create_failure_result")
    void shouldCreateFailureResult() {
      // Given: 准备错误消息
      String errorMessage = "Network timeout occurred";

      // When: 创建失败结果
      ProcessResult<CanonicalLiterature> result = ProcessResult.failure(errorMessage);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.data()).isNull();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.status()).isEqualTo(ProcessStatus.FAILED);
      assertThat(result.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("should_create_partial_success_result")
    void shouldCreatePartialSuccessResult() {
      // Given: 准备数据
      List<CanonicalLiterature> data = List.of(createValidLiterature());
      String cursor = "cursor456";
      String warningMessage = "2 records failed";

      // When: 创建部分成功结果
      ProcessResult<CanonicalLiterature> result =
          ProcessResult.partialSuccess(data, cursor, warningMessage);

      // Then: 验证结果
      assertThat(result.success()).isTrue();
      assertThat(result.data()).isEqualTo(data);
      assertThat(result.nextCursor()).isEqualTo(cursor);
      assertThat(result.status()).isEqualTo(ProcessStatus.PARTIAL_SUCCESS);
      assertThat(result.errorMessage()).isEqualTo(warningMessage);
    }

    @Test
    @DisplayName("should_create_validation_error_result")
    void shouldCreateValidationErrorResult() {
      // Given: 准备错误消息
      String errorMessage = "Title is required";

      // When: 创建验证错误结果
      ProcessResult<CanonicalLiterature> result = ProcessResult.validationError(errorMessage);

      // Then: 验证结果
      assertThat(result.success()).isFalse();
      assertThat(result.status()).isEqualTo(ProcessStatus.VALIDATION_ERROR);
      assertThat(result.errorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("should_support_metadata")
    void shouldSupportMetadata() {
      // Given: 准备元数据
      Map<String, Object> metadata = Map.of("recordCount", 100, "processingTime", 1250L);

      // When: 创建带元数据的结果
      ProcessResult<CanonicalLiterature> result =
          ProcessResult.<CanonicalLiterature>builder()
              .success(true)
              .data(List.of(createValidLiterature()))
              .status(ProcessStatus.SUCCESS)
              .metadata(metadata)
              .build();

      // Then: 验证元数据
      assertThat(result.metadata()).isEqualTo(metadata);
      assertThat(result.metadata().get("recordCount")).isEqualTo(100);
      assertThat(result.metadata().get("processingTime")).isEqualTo(1250L);
    }
  }

  // ==================== ValidationResult测试 ====================

  @Nested
  @DisplayName("ValidationResult测试")
  class ValidationResultTest {

    @Test
    @DisplayName("should_create_valid_result")
    void shouldCreateValidResult() {
      // When: 创建成功的验证结果
      ValidationResult result = ValidationResult.success();

      // Then: 验证结果
      assertThat(result.isValid()).isTrue();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("should_create_invalid_result_with_errors")
    void shouldCreateInvalidResultWithErrors() {
      // Given: 准备错误列表
      List<String> errors = List.of("Title is required", "PMID is required");

      // When: 创建失败的验证结果
      ValidationResult result = ValidationResult.failure(errors);

      // Then: 验证结果
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(2);
      assertThat(result.errors()).containsExactlyElementsOf(errors);
    }

    @Test
    @DisplayName("should_create_invalid_result_with_single_error")
    void shouldCreateInvalidResultWithSingleError() {
      // Given: 准备单个错误
      String error = "Title is required";

      // When: 创建失败的验证结果
      ValidationResult result = ValidationResult.failure(error);

      // Then: 验证结果
      assertThat(result.isValid()).isFalse();
      assertThat(result.errors()).hasSize(1);
      assertThat(result.errors()).contains(error);
    }

    @Test
    @DisplayName("should_return_immutable_error_list")
    void shouldReturnImmutableErrorList() {
      // Given: 准备错误列表
      List<String> errors = List.of("Error 1", "Error 2");
      ValidationResult result = ValidationResult.failure(errors);

      // When & Then: 验证错误列表是不可变的
      assertThatThrownBy(() -> result.errors().add("Error 3"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  // ==================== ProcessStatus枚举测试 ====================

  @Nested
  @DisplayName("ProcessStatus枚举测试")
  class ProcessStatusTest {

    @Test
    @DisplayName("should_have_all_required_statuses")
    void shouldHaveAllRequiredStatuses() {
      // When & Then: 验证所有状态都存在
      assertThat(ProcessStatus.SUCCESS).isNotNull();
      assertThat(ProcessStatus.PARTIAL_SUCCESS).isNotNull();
      assertThat(ProcessStatus.FAILED).isNotNull();
      assertThat(ProcessStatus.VALIDATION_ERROR).isNotNull();
    }

    @Test
    @DisplayName("should_have_exactly_four_statuses")
    void shouldHaveExactlyFourStatuses() {
      // When & Then: 验证状态数量
      assertThat(ProcessStatus.values()).hasSize(4);
    }
  }

  // ==================== ProviderContext测试 ====================

  @Nested
  @DisplayName("ProviderContext测试")
  class ProviderContextTest {

    @Test
    @DisplayName("should_get_client_with_correct_type")
    void shouldGetClientWithCorrectType() {
      // Given: 准备特定类型的客户端
      String mockClient = "PubMedClient";
      ProviderContext context =
          ProviderContext.builder()
              .config(createTestConfig())
              .client(mockClient)
              .build();

      // When: 获取客户端
      String client = context.getClient(String.class);

      // Then: 返回正确类型的客户端
      assertThat(client).isEqualTo(mockClient);
    }

    @Test
    @DisplayName("should_throw_exception_on_type_mismatch")
    void shouldThrowExceptionOnTypeMismatch() {
      // Given: 准备客户端
      ProviderContext context =
          ProviderContext.builder()
              .config(createTestConfig())
              .client("StringClient")
              .build();

      // When & Then: 类型不匹配时抛出异常
      assertThatThrownBy(() -> context.getClient(Integer.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Client type mismatch");
    }

    @Test
    @DisplayName("should_support_attributes")
    void shouldSupportAttributes() {
      // Given: 准备属性
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("key1", "value1");
      attributes.put("key2", 123);

      ProviderContext context =
          ProviderContext.builder()
              .config(createTestConfig())
              .client(new Object())
              .attributes(attributes)
              .build();

      // When & Then: 验证属性
      assertThat(context.getAttributes()).containsEntry("key1", "value1");
      assertThat(context.getAttributes()).containsEntry("key2", 123);
    }
  }
}
