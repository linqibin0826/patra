package com.patra.registry.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.exception.RegistryQuotaExceeded;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * RegistryErrorMappingContributor 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>验证所有异常类型正确映射到对应的 HTTP 错误码
 *   <li>验证未知异常返回 Optional.empty()
 *   <li>使用 Mock HttpStdErrors.Group 验证错误码调用
 *   <li>覆盖领域异常、数据层异常和边界场景
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("RegistryErrorMappingContributor 单元测试")
class RegistryErrorMappingContributorTest {

  private HttpStdErrors.Group httpGroup;
  private RegistryErrorMappingContributor contributor;

  // Mock 错误码
  private ErrorCodeLike badRequest;
  private ErrorCodeLike conflict;
  private ErrorCodeLike unprocessable;

  @BeforeEach
  void setUp() {
    // Given: 初始化 Mock 对象
    httpGroup = mock(HttpStdErrors.Group.class);

    badRequest = mockErrorCode("REG-0400", 400);
    conflict = mockErrorCode("REG-0409", 409);
    unprocessable = mockErrorCode("REG-0422", 422);

    when(httpGroup.BAD_REQUEST()).thenReturn(badRequest);
    when(httpGroup.CONFLICT()).thenReturn(conflict);
    when(httpGroup.UNPROCESSABLE()).thenReturn(unprocessable);

    contributor = new RegistryErrorMappingContributor(httpGroup);
  }

  @Nested
  @DisplayName("领域异常映射测试")
  class DomainExceptionMappingTests {

    @Test
    @DisplayName("应该将 DomainValidationException 映射为 BAD_REQUEST")
    void should_map_to_bad_request_when_domain_validation_exception() {
      // Given: 准备领域验证异常
      DomainValidationException exception = new DomainValidationException("字段不能为空");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证映射结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(badRequest);
      assertThat(result.get().httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("应该将 RegistryQuotaExceeded 映射为 CONFLICT")
    void should_map_to_conflict_when_registry_quota_exceeded() {
      // Given: 准备配额超限异常（使用具体子类）
      RegistryQuotaExceeded exception = new TestRegistryQuotaExceeded("超出配额限制");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证映射结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
      assertThat(result.get().httpStatus()).isEqualTo(409);
    }
  }

  @Nested
  @DisplayName("数据层异常映射测试")
  class DataLayerExceptionMappingTests {

    @Test
    @DisplayName("应该将 DuplicateKeyException 映射为 CONFLICT")
    void should_map_to_conflict_when_duplicate_key_exception() {
      // Given: 准备唯一键冲突异常
      DuplicateKeyException exception = new DuplicateKeyException("重复键冲突");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证映射结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
      assertThat(result.get().httpStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("应该将 DataIntegrityViolationException 映射为 UNPROCESSABLE")
    void should_map_to_unprocessable_when_data_integrity_violation_exception() {
      // Given: 准备数据完整性违反异常
      DataIntegrityViolationException exception = new DataIntegrityViolationException("违反数据完整性约束");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证映射结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(unprocessable);
      assertThat(result.get().httpStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("应该将 OptimisticLockingFailureException 映射为 CONFLICT")
    void should_map_to_conflict_when_optimistic_locking_failure_exception() {
      // Given: 准备乐观锁失败异常
      OptimisticLockingFailureException exception = new OptimisticLockingFailureException("乐观锁冲突");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证映射结果
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
      assertThat(result.get().httpStatus()).isEqualTo(409);
    }
  }

  @Nested
  @DisplayName("边界场景测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该对未知异常返回空 Optional")
    void should_return_empty_when_unknown_exception() {
      // Given: 准备未知类型的异常
      RuntimeException exception = new RuntimeException("未知异常");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证返回空
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该对 IllegalArgumentException 返回空 Optional")
    void should_return_empty_when_illegal_argument_exception() {
      // Given: 准备标准异常
      IllegalArgumentException exception = new IllegalArgumentException("参数错误");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证返回空（委托给默认处理器）
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该对 NullPointerException 返回空 Optional")
    void should_return_empty_when_null_pointer_exception() {
      // Given: 准备空指针异常
      NullPointerException exception = new NullPointerException("空指针");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证返回空（委托给默认处理器）
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理带有 cause 的异常")
    void should_handle_exception_with_cause() {
      // Given: 准备带原因的异常
      DomainValidationException rootCause = new DomainValidationException("根原因");
      DuplicateKeyException exception = new DuplicateKeyException("重复键", rootCause);

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证根据顶层异常类型映射
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
    }
  }

  @Nested
  @DisplayName("异常继承层次测试")
  class ExceptionInheritanceTests {

    @Test
    @DisplayName("应该正确处理 RegistryQuotaExceeded 的子类")
    void should_handle_subclass_of_registry_quota_exceeded() {
      // Given: 准备 RegistryQuotaExceeded 的子类实例
      RegistryQuotaExceeded exception = new TestRegistryQuotaExceeded("子类配额超限");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证通过 instanceof 正确识别
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
    }

    @Test
    @DisplayName("应该正确处理 DataIntegrityViolationException 的子类（如 DuplicateKeyException）")
    void should_handle_duplicate_key_as_specific_mapping() {
      // Given: DuplicateKeyException 是 DataIntegrityViolationException 的子类
      DuplicateKeyException exception = new DuplicateKeyException("重复键");

      // When: 调用映射方法
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then: 验证优先使用更具体的映射（CONFLICT 而非 UNPROCESSABLE）
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
      assertThat(result.get().httpStatus()).isEqualTo(409);
    }
  }

  // ========== 测试辅助类 ==========

  /**
   * Mock ErrorCodeLike 对象。
   *
   * @param code 错误码字符串
   * @param httpStatus HTTP 状态码
   * @return ErrorCodeLike 实例
   */
  private ErrorCodeLike mockErrorCode(String code, int httpStatus) {
    return new ErrorCodeLike() {
      @Override
      public String code() {
        return code;
      }

      @Override
      public int httpStatus() {
        return httpStatus;
      }

      @Override
      public String toString() {
        return code;
      }
    };
  }

  /**
   * 测试用 RegistryQuotaExceeded 具体实现。
   *
   * <p>由于 RegistryQuotaExceeded 是抽象类，需要具体子类用于测试。
   */
  private static class TestRegistryQuotaExceeded extends RegistryQuotaExceeded {
    public TestRegistryQuotaExceeded(String message) {
      super(message);
    }
  }
}
