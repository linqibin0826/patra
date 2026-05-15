package dev.linqibin.patra.registry.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import dev.linqibin.patra.registry.domain.exception.RegistryQuotaExceeded;
import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// RegistryErrorMappingContributor 单元测试。
///
/// 测试策略：
///
/// - 验证领域异常正确映射到对应的 HTTP 错误码
/// - 验证未知异常返回 Optional.empty()
/// - 使用 Mock HttpStdErrors.Group 验证错误码调用
///
/// 数据层异常（如 DuplicateKeyException、DataIntegrityViolationException）
/// 由更高优先级的 JpaErrorMappingContributor 统一处理，不在本 Contributor 职责范围内。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(2)
@DisplayName("RegistryErrorMappingContributor 单元测试")
class RegistryErrorMappingContributorTest {

  private HttpStdErrors.Group httpGroup;
  private RegistryErrorMappingContributor contributor;

  // Mock 错误码
  private ErrorCodeLike badRequest;
  private ErrorCodeLike conflict;

  @BeforeEach
  void setUp() {
    httpGroup = mock(HttpStdErrors.Group.class);

    badRequest = mockErrorCode("REG-0400", 400);
    conflict = mockErrorCode("REG-0409", 409);

    when(httpGroup.BAD_REQUEST()).thenReturn(badRequest);
    when(httpGroup.CONFLICT()).thenReturn(conflict);

    contributor = new RegistryErrorMappingContributor(httpGroup);
  }

  @Nested
  @DisplayName("领域异常映射测试")
  class DomainExceptionMappingTests {

    @Test
    @DisplayName("应该将 DomainValidationException 映射为 BAD_REQUEST")
    void should_map_to_bad_request_when_domain_validation_exception() {
      // Given
      DomainValidationException exception = new DomainValidationException("字段不能为空");

      // When
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(badRequest);
      assertThat(result.get().httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("应该将 RegistryQuotaExceeded 映射为 CONFLICT")
    void should_map_to_conflict_when_registry_quota_exceeded() {
      // Given
      RegistryQuotaExceeded exception = new TestRegistryQuotaExceeded("超出配额限制");

      // When
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then
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
      // Given
      RuntimeException exception = new RuntimeException("未知异常");

      // When
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该对 IllegalArgumentException 返回空 Optional")
    void should_return_empty_when_illegal_argument_exception() {
      // Given
      IllegalArgumentException exception = new IllegalArgumentException("参数错误");

      // When
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该对 NullPointerException 返回空 Optional")
    void should_return_empty_when_null_pointer_exception() {
      // Given
      NullPointerException exception = new NullPointerException("空指针");

      // When
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("异常继承层次测试")
  class ExceptionInheritanceTests {

    @Test
    @DisplayName("应该正确处理 RegistryQuotaExceeded 的子类")
    void should_handle_subclass_of_registry_quota_exceeded() {
      // Given
      RegistryQuotaExceeded exception = new TestRegistryQuotaExceeded("子类配额超限");

      // When
      Optional<ErrorCodeLike> result = contributor.mapException(exception);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(conflict);
    }
  }

  // ========== 测试辅助类 ==========

  /// Mock ErrorCodeLike 对象。
  ///
  /// @param code 错误码字符串
  /// @param httpStatus HTTP 状态码
  /// @return ErrorCodeLike 实例
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

  /// 测试用 RegistryQuotaExceeded 具体实现。
  ///
  /// 由于 RegistryQuotaExceeded 是抽象类，需要具体子类用于测试。
  private static class TestRegistryQuotaExceeded extends RegistryQuotaExceeded {
    public TestRegistryQuotaExceeded(String message) {
      super(message);
    }
  }
}
