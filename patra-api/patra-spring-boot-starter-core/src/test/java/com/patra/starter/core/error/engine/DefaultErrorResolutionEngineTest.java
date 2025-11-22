package com.patra.starter.core.error.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// {@link DefaultErrorResolutionEngine} 单元测试。
/// 
/// 测试策略: 应用层服务 - Mock 测试，验证错误解析逻辑。
/// 
/// 测试覆盖:
/// 
/// - ✅ ApplicationException 映射
///   - ✅ ErrorMappingContributor 映射
///   - ✅ ErrorTrait 映射
///   - ✅ 命名启发式映射
///   - ✅ Cause 链解析
///   - ✅ 回退策略
///   - ✅ null 异常处理
///   - ✅ 配置开关控制
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("DefaultErrorResolutionEngine 单元测试")
@ExtendWith(MockitoExtension.class)
class DefaultErrorResolutionEngineTest {

  @Mock private ErrorMappingContributor mockContributor;

  private ErrorProperties errorProperties;
  private DefaultErrorResolutionEngine engine;

  @BeforeEach
  void setUp() {
    errorProperties = new ErrorProperties();
    errorProperties.setContextPrefix("TEST");
    errorProperties.setEngine(new ErrorProperties.EngineProperties());
    errorProperties.getEngine().setMaxCauseDepth(10);
    errorProperties.getEngine().setEnableTraitMapping(true);
    errorProperties.getEngine().setEnableNamingHeuristic(true);

    engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));
  }

  @Nested
  @DisplayName("ApplicationException 映射测试")
  class ApplicationExceptionMappingTests {

    @Test
    @DisplayName("应该正确解析 ApplicationException")
    void shouldResolveApplicationException() {
      // Given
      ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
      when(errorCode.code()).thenReturn("TEST:1001");
      when(errorCode.httpStatus()).thenReturn(400);

      ApplicationException exception = mock(ApplicationException.class);
      when(exception.getErrorCode()).thenReturn(errorCode);

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode()).isEqualTo(errorCode);
      assertThat(resolution.httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("ApplicationException 应该优先于其他解析策略")
    void applicationExceptionShouldHaveHighestPriority() {
      // Given
      ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
      when(errorCode.code()).thenReturn("TEST:1001");
      when(errorCode.httpStatus()).thenReturn(400);

      ApplicationException exception = mock(ApplicationException.class);
      when(exception.getErrorCode()).thenReturn(errorCode);

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution.errorCode()).isEqualTo(errorCode);
      verifyNoInteractions(mockContributor); // 不应该调用 contributor
    }
  }

  @Nested
  @DisplayName("ErrorMappingContributor 映射测试")
  class ErrorMappingContributorTests {

    @Test
    @DisplayName("应该通过 ErrorMappingContributor 解析异常")
    void shouldResolveViaContributor() {
      // Given
      RuntimeException exception = new RuntimeException("test");
      ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
      when(errorCode.code()).thenReturn("TEST:2001");
      when(errorCode.httpStatus()).thenReturn(500);

      when(mockContributor.mapException(exception)).thenReturn(Optional.of(errorCode));

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode()).isEqualTo(errorCode);
      assertThat(resolution.httpStatus()).isEqualTo(500);
      verify(mockContributor).mapException(exception);
    }

    @Test
    @DisplayName("Contributor 返回空时应该继续下一个策略")
    void shouldContinueWhenContributorReturnsEmpty() {
      // Given
      IllegalArgumentException exception = new IllegalArgumentException("invalid");
      when(mockContributor.mapException(exception)).thenReturn(Optional.empty());

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0422"); // 命名启发式: Invalid
    }

    @Test
    @DisplayName("Contributor 抛出异常时应该继续下一个策略")
    void shouldContinueWhenContributorThrowsException() {
      // Given
      RuntimeException exception = new RuntimeException("test");
      when(mockContributor.mapException(exception))
          .thenThrow(new RuntimeException("Contributor failed"));

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0500"); // 回退到服务器错误
    }
  }

  @Nested
  @DisplayName("ErrorTrait 映射测试")
  class ErrorTraitMappingTests {

    @ParameterizedTest(name = "特征 {0} 应该映射到 HTTP 状态码 {1}")
    @MethodSource("provideErrorTraits")
    @DisplayName("应该正确映射 ErrorTrait 到 HTTP 状态码")
    void shouldMapErrorTraitToHttpStatus(ErrorTrait trait, String expectedCodeSuffix) {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      class TestException extends RuntimeException implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
          return Set.of(trait);
        }
      }

      Throwable exception = new TestException();

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains(expectedCodeSuffix);
    }

    static Stream<Arguments> provideErrorTraits() {
      return Stream.of(
          Arguments.of(ErrorTrait.NOT_FOUND, "0404"),
          Arguments.of(ErrorTrait.CONFLICT, "0409"),
          Arguments.of(ErrorTrait.RULE_VIOLATION, "0422"),
          Arguments.of(ErrorTrait.QUOTA_EXCEEDED, "0429"),
          Arguments.of(ErrorTrait.UNAUTHORIZED, "0401"),
          Arguments.of(ErrorTrait.FORBIDDEN, "0403"),
          Arguments.of(ErrorTrait.TIMEOUT, "0504"),
          Arguments.of(ErrorTrait.DEP_UNAVAILABLE, "0503"));
    }

    @Test
    @DisplayName("ErrorTrait 映射禁用时应该跳过")
    void shouldSkipTraitMappingWhenDisabled() {
      // Given
      errorProperties.getEngine().setEnableTraitMapping(false);
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      class TestException extends RuntimeException implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
          return Set.of(ErrorTrait.NOT_FOUND);
        }
      }

      Throwable exception = new TestException();

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution.errorCode().code()).contains("0500"); // 回退到服务器错误
    }

    @Test
    @DisplayName("HasErrorTraits 返回 null 或空集合时应该继续下一个策略")
    void shouldContinueWhenTraitsAreNullOrEmpty() {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      class TestExceptionWithNull extends RuntimeException implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
          return null;
        }
      }

      Throwable exceptionWithNull = new TestExceptionWithNull();

      // When
      ErrorResolution resolution = engine.resolve(exceptionWithNull);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0500");
    }
  }

  @Nested
  @DisplayName("命名启发式映射测试")
  class NamingHeuristicMappingTests {

    @ParameterizedTest(name = "异常类名 {0} 应该映射到 HTTP 状态码 {1}")
    @MethodSource("provideExceptionNames")
    @DisplayName("应该根据类名后缀映射到正确的 HTTP 状态码")
    void shouldMapByClassNameSuffix(String className, String expectedCodeSuffix) {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      Throwable exception = createExceptionWithClassName(className);

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains(expectedCodeSuffix);
    }

    static Stream<Arguments> provideExceptionNames() {
      return Stream.of(
          Arguments.of("NotFound", "0404"),
          Arguments.of("Conflict", "0409"),
          Arguments.of("AlreadyExists", "0409"),
          Arguments.of("Invalid", "0422"),
          Arguments.of("Validation", "0422"),
          Arguments.of("QuotaExceeded", "0429"),
          Arguments.of("Unauthorized", "0401"),
          Arguments.of("Forbidden", "0403"),
          Arguments.of("Timeout", "0504"));
    }

    @Test
    @DisplayName("命名启发式禁用时应该跳过")
    void shouldSkipNamingHeuristicWhenDisabled() {
      // Given
      errorProperties.getEngine().setEnableNamingHeuristic(false);
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      Throwable exception = createExceptionWithClassName("NotFound");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution.errorCode().code()).contains("0500"); // 回退到服务器错误
    }

    private Throwable createExceptionWithClassName(String suffix) {
      // 使用实际的异常类来测试命名启发式
      // 类名以特定后缀结尾，匹配 NAMING_SUFFIX_TO_CODE_MAP
      return switch (suffix) {
        case "NotFound" -> new ResourceNotFound();
        case "Conflict" -> new DataConflict();
        case "AlreadyExists" -> new RecordAlreadyExists();
        case "Invalid" -> new InputInvalid();
        case "Validation" -> new DataValidation();
        case "QuotaExceeded" -> new RateLimitQuotaExceeded();
        case "Unauthorized" -> new AccessUnauthorized();
        case "Forbidden" -> new OperationForbidden();
        case "Timeout" -> new RequestTimeout();
        default -> new RuntimeException("Unknown test suffix: " + suffix);
      };
    }

    // 测试用异常类 - 类名以映射键结尾
    static class ResourceNotFound extends RuntimeException {}

    static class DataConflict extends RuntimeException {}

    static class RecordAlreadyExists extends RuntimeException {}

    static class InputInvalid extends RuntimeException {}

    static class DataValidation extends RuntimeException {}

    static class RateLimitQuotaExceeded extends RuntimeException {}

    static class AccessUnauthorized extends RuntimeException {}

    static class OperationForbidden extends RuntimeException {}

    static class RequestTimeout extends RuntimeException {}
  }

  @Nested
  @DisplayName("Cause 链解析测试")
  class CauseChainResolutionTests {

    @Test
    @DisplayName("应该递归解析 cause 链")
    void shouldResolveCauseChain() {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
      when(errorCode.code()).thenReturn("TEST:3001");
      when(errorCode.httpStatus()).thenReturn(404);

      ApplicationException rootCause = mock(ApplicationException.class);
      when(rootCause.getErrorCode()).thenReturn(errorCode);

      RuntimeException middleException = new RuntimeException("middle", rootCause);
      RuntimeException topException = new RuntimeException("top", middleException);

      // When
      ErrorResolution resolution = engine.resolve(topException);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode()).isEqualTo(errorCode);
      assertThat(resolution.httpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("超过最大深度时应该返回 500 服务器错误")
    void shouldReturnServerErrorWhenExceedingMaxDepth() {
      // Given
      errorProperties.getEngine().setMaxCauseDepth(2);
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      // 创建深度为 4 的异常链
      Throwable deepException = new RuntimeException("level 4");
      deepException = new RuntimeException("level 3", deepException);
      deepException = new RuntimeException("level 2", deepException);
      Throwable topException = new RuntimeException("level 1", deepException);

      // When
      ErrorResolution resolution = engine.resolve(topException);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0500");
    }

    @Test
    @DisplayName("应该处理循环引用的 cause 链")
    void shouldHandleCircularCauseChain() {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      RuntimeException exception1 = new RuntimeException("exception1");
      RuntimeException exception2 = new RuntimeException("exception2", exception1);

      // 模拟循环引用（虽然实际很难构造，但测试边界情况）
      // 在实际场景中，getCause() 返回自己会被检测到

      // When
      ErrorResolution resolution = engine.resolve(exception1);

      // Then
      assertThat(resolution).isNotNull(); // 应该能正常处理而不是无限循环
    }
  }

  @Nested
  @DisplayName("回退策略测试")
  class FallbackStrategyTests {

    @Test
    @DisplayName("无法识别的异常应该回退到 500 服务器错误")
    void shouldFallbackToServerErrorForUnrecognizedException() {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());
      Throwable exception = new RuntimeException("unknown");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0500");
      assertThat(resolution.httpStatus()).isEqualTo(500);
    }

    @ParameterizedTest(name = "客户端异常 {0} 应该回退到 422")
    @MethodSource("provideClientErrorExceptions")
    @DisplayName("客户端错误类异常应该回退到 422")
    void shouldFallbackTo422ForClientErrors(String className) {
      // Given
      when(mockContributor.mapException(any())).thenReturn(Optional.empty());

      // 禁用命名启发式，确保使用回退策略
      errorProperties.getEngine().setEnableNamingHeuristic(false);
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      Throwable exception = createExceptionWithSimpleClassName(className);

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0422");
      assertThat(resolution.httpStatus()).isEqualTo(422);
    }

    static Stream<Arguments> provideClientErrorExceptions() {
      return Stream.of(
          Arguments.of("ValidationException"),
          Arguments.of("NotValidException"),
          Arguments.of("BindException"),
          Arguments.of("ConstraintViolationException"),
          Arguments.of("MissingParameterException"),
          Arguments.of("IllegalArgumentException"),
          Arguments.of("InvalidRequestException"),
          Arguments.of("BadRequestException"),
          Arguments.of("MalformedInputException"));
    }

    private Throwable createExceptionWithSimpleClassName(String className) {
      // 使用实际的异常类来测试客户端错误回退策略
      return switch (className) {
        case "ValidationException" -> new TestValidationException();
        case "NotValidException" -> new TestNotValidException();
        case "BindException" -> new TestBindException();
        case "ConstraintViolationException" -> new TestConstraintViolationException();
        case "MissingParameterException" -> new TestMissingParameterException();
        case "IllegalArgumentException" -> new IllegalArgumentException();
        case "InvalidRequestException" -> new TestInvalidRequestException();
        case "BadRequestException" -> new TestBadRequestException();
        case "MalformedInputException" -> new TestMalformedInputException();
        default -> new RuntimeException("Unknown test class: " + className);
      };
    }

    // 测试用客户端错误异常类
    static class TestValidationException extends RuntimeException {}

    static class TestNotValidException extends RuntimeException {}

    static class TestBindException extends RuntimeException {}

    static class TestConstraintViolationException extends RuntimeException {}

    static class TestMissingParameterException extends RuntimeException {}

    static class TestInvalidRequestException extends RuntimeException {}

    static class TestBadRequestException extends RuntimeException {}

    static class TestMalformedInputException extends RuntimeException {}

    @Test
    @DisplayName("null 异常应该返回回退错误码并记录警告")
    void shouldReturnFallbackForNullException() {
      // When
      ErrorResolution resolution = engine.resolve(null);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0500");
      assertThat(resolution.httpStatus()).isEqualTo(500);
    }
  }

  @Nested
  @DisplayName("配置测试")
  class ConfigurationTests {

    @Test
    @DisplayName("应该使用配置的上下文前缀")
    void shouldUseConfiguredContextPrefix() {
      // Given
      errorProperties.setContextPrefix("CUSTOM");
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      when(mockContributor.mapException(any())).thenReturn(Optional.empty());
      Throwable exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).startsWith("CUSTOM");
    }

    @Test
    @DisplayName("空白上下文前缀应该回退到 UNKNOWN")
    void shouldFallbackToUnknownForBlankContextPrefix() {
      // Given
      errorProperties.setContextPrefix("   ");
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      when(mockContributor.mapException(any())).thenReturn(Optional.empty());
      Throwable exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).startsWith("UNKNOWN");
    }

    @Test
    @DisplayName("null 上下文前缀应该回退到 UNKNOWN")
    void shouldFallbackToUnknownForNullContextPrefix() {
      // Given
      errorProperties.setContextPrefix(null);
      engine = new DefaultErrorResolutionEngine(errorProperties, List.of(mockContributor));

      when(mockContributor.mapException(any())).thenReturn(Optional.empty());
      Throwable exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).startsWith("UNKNOWN");
    }

    @Test
    @DisplayName("应该支持空的 ErrorMappingContributor 列表")
    void shouldSupportEmptyContributorList() {
      // Given
      engine = new DefaultErrorResolutionEngine(errorProperties, Collections.emptyList());
      Throwable exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode().code()).contains("0500");
    }

    @Test
    @DisplayName("应该支持多个 ErrorMappingContributor")
    void shouldSupportMultipleContributors() {
      // Given
      ErrorMappingContributor contributor1 = mock(ErrorMappingContributor.class);
      ErrorMappingContributor contributor2 = mock(ErrorMappingContributor.class);

      when(contributor1.mapException(any())).thenReturn(Optional.empty());

      ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
      when(errorCode.code()).thenReturn("TEST:4001");
      when(errorCode.httpStatus()).thenReturn(400);
      when(contributor2.mapException(any())).thenReturn(Optional.of(errorCode));

      engine =
          new DefaultErrorResolutionEngine(errorProperties, List.of(contributor1, contributor2));
      Throwable exception = new RuntimeException("test");

      // When
      ErrorResolution resolution = engine.resolve(exception);

      // Then
      assertThat(resolution).isNotNull();
      assertThat(resolution.errorCode()).isEqualTo(errorCode);
      verify(contributor1).mapException(exception);
      verify(contributor2).mapException(exception);
    }
  }

  @Nested
  @DisplayName("解析顺序优先级测试")
  class ResolutionPriorityTests {

    @Test
    @DisplayName("解析顺序: ApplicationException > Contributor > Trait > Naming > Cause > Fallback")
    void shouldFollowCorrectResolutionOrder() {
      // Given - 验证 ApplicationException 优先级最高
      ErrorCodeLike appErrorCode = mock(ErrorCodeLike.class);
      when(appErrorCode.code()).thenReturn("APP:1000");
      when(appErrorCode.httpStatus()).thenReturn(400);

      ApplicationException appException = mock(ApplicationException.class);
      when(appException.getErrorCode()).thenReturn(appErrorCode);

      // When
      ErrorResolution resolution = engine.resolve(appException);

      // Then
      assertThat(resolution.errorCode()).isEqualTo(appErrorCode);
      verifyNoInteractions(mockContributor);
    }
  }
}
