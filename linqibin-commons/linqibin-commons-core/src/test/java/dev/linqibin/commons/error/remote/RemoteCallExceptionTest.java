package dev.linqibin.commons.error.remote;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.problem.ErrorKeys;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// RemoteCallException 单元测试
///
/// 测试策略：
///
/// - 测试各种构造函数
/// - 测试 ErrorTraits 解析逻辑
/// - 测试扩展属性存取
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RemoteCallException 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class RemoteCallExceptionTest {

  private static final int HTTP_STATUS = 404;
  private static final String MESSAGE = "Resource not found";
  private static final String METHOD_KEY = "GET /api/users/1";
  private static final String TRACE_ID = "abc-123-def";
  private static final String ERROR_CODE = "USER-0404";

  @Nested
  @DisplayName("构造函数测试")
  class ConstructorTests {

    @Test
    @DisplayName("简单构造函数 - traits 应为空")
    void simpleConstructorShouldHaveEmptyTraits() {
      // When
      var exception = new RemoteCallException(HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID);

      // Then
      assertThat(exception.getHttpStatus()).isEqualTo(HTTP_STATUS);
      assertThat(exception.getMessage()).isEqualTo(MESSAGE);
      assertThat(exception.getMethodKey()).isEqualTo(METHOD_KEY);
      assertThat(exception.getTraceId()).isEqualTo(TRACE_ID);
      assertThat(exception.getErrorCode()).isNull();
      assertThat(exception.getErrorTraits()).isEmpty();
      assertThat(exception.getExtensions()).isEmpty();
    }

    @Test
    @DisplayName("完整构造函数 - 自动从 extensions 解析 traits")
    void fullConstructorShouldParseTraitsFromExtensions() {
      // Given
      Map<String, Object> extensions = new HashMap<>();
      extensions.put(ErrorKeys.TRAITS, List.of("NOT_FOUND", "DEP_UNAVAILABLE"));

      // When
      var exception =
          new RemoteCallException(
              ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, extensions);

      // Then
      assertThat(exception.getErrorCode()).isEqualTo(ERROR_CODE);
      assertThat(exception.getErrorTraits())
          .containsExactlyInAnyOrder(
              StandardErrorTrait.NOT_FOUND, StandardErrorTrait.DEP_UNAVAILABLE);
    }

    @Test
    @DisplayName("完整构造函数 - 显式指定 traits")
    void fullConstructorWithExplicitTraits() {
      // Given
      Set<ErrorTrait> traits =
          Set.of(StandardErrorTrait.CONFLICT, StandardErrorTrait.RULE_VIOLATION);

      // When
      var exception =
          new RemoteCallException(
              ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, null, traits);

      // Then
      assertThat(exception.getErrorTraits())
          .containsExactlyInAnyOrder(
              StandardErrorTrait.CONFLICT, StandardErrorTrait.RULE_VIOLATION);
    }

    @Test
    @DisplayName("null extensions 应返回空 map")
    void nullExtensionsShouldReturnEmptyMap() {
      // When
      var exception =
          new RemoteCallException(ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, null);

      // Then
      assertThat(exception.getExtensions()).isEmpty();
      assertThat(exception.getErrorTraits()).isEmpty();
    }
  }

  @Nested
  @DisplayName("ErrorTraits 解析测试")
  class ParseErrorTraitsTests {

    @Test
    @DisplayName("解析单个 trait")
    void shouldParseSingleTrait() {
      // Given
      List<String> traits = List.of("NOT_FOUND");

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result).containsExactly(StandardErrorTrait.NOT_FOUND);
    }

    @Test
    @DisplayName("解析多个 traits")
    void shouldParseMultipleTraits() {
      // Given
      List<String> traits = List.of("NOT_FOUND", "TIMEOUT", "CONFLICT");

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result)
          .containsExactlyInAnyOrder(
              StandardErrorTrait.NOT_FOUND,
              StandardErrorTrait.TIMEOUT,
              StandardErrorTrait.CONFLICT);
    }

    @Test
    @DisplayName("trait 名称大小写不敏感")
    void shouldBeCaseInsensitive() {
      // Given
      List<String> traits = List.of("not_found", "Timeout", "CONFLICT");

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result)
          .containsExactlyInAnyOrder(
              StandardErrorTrait.NOT_FOUND,
              StandardErrorTrait.TIMEOUT,
              StandardErrorTrait.CONFLICT);
    }

    @Test
    @DisplayName("忽略未知 trait 名称")
    void shouldIgnoreUnknownTraits() {
      // Given
      List<String> traits = List.of("NOT_FOUND", "UNKNOWN_TRAIT", "TIMEOUT");

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result)
          .containsExactlyInAnyOrder(StandardErrorTrait.NOT_FOUND, StandardErrorTrait.TIMEOUT);
    }

    @Test
    @DisplayName("空列表返回空集合")
    void shouldReturnEmptySetForEmptyList() {
      // Given
      List<String> traits = Collections.emptyList();

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 值返回空集合")
    void shouldReturnEmptySetForNull() {
      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("非 List 类型返回空集合")
    void shouldReturnEmptySetForNonListType() {
      // Given
      String notAList = "NOT_FOUND";

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(notAList);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("列表中的非字符串元素被忽略")
    void shouldIgnoreNonStringElements() {
      // Given
      List<Object> traits = List.of("NOT_FOUND", 123, "TIMEOUT");

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result)
          .containsExactlyInAnyOrder(StandardErrorTrait.NOT_FOUND, StandardErrorTrait.TIMEOUT);
    }

    @Test
    @DisplayName("处理空白字符串")
    void shouldHandleBlankStrings() {
      // Given
      List<String> traits = List.of("NOT_FOUND", "", "  ", "TIMEOUT");

      // When
      Set<ErrorTrait> result = RemoteCallException.parseErrorTraits(traits);

      // Then
      assertThat(result)
          .containsExactlyInAnyOrder(StandardErrorTrait.NOT_FOUND, StandardErrorTrait.TIMEOUT);
    }
  }

  @Nested
  @DisplayName("扩展属性测试")
  class ExtensionTests {

    @Test
    @DisplayName("获取扩展属性")
    void shouldGetExtension() {
      // Given
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("customKey", "customValue");

      var exception =
          new RemoteCallException(
              ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, extensions);

      // When & Then
      assertThat(exception.getExtension("customKey")).isEqualTo("customValue");
      assertThat(exception.getExtension("nonExistent")).isNull();
    }

    @Test
    @DisplayName("获取类型化扩展属性")
    void shouldGetTypedExtension() {
      // Given
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("count", 42);
      extensions.put("name", "test");

      var exception =
          new RemoteCallException(
              ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, extensions);

      // When & Then
      assertThat(exception.getExtension("count", Integer.class)).isEqualTo(42);
      assertThat(exception.getExtension("name", String.class)).isEqualTo("test");
      assertThat(exception.getExtension("count", String.class)).isNull(); // 类型不匹配
      assertThat(exception.getExtension("nonExistent", String.class)).isNull();
    }

    @Test
    @DisplayName("扩展属性不可变")
    void extensionsShouldBeImmutable() {
      // Given
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("key", "value");

      var exception =
          new RemoteCallException(
              ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, extensions);

      // When: 修改原始 map
      extensions.put("newKey", "newValue");

      // Then: 异常中的 extensions 不受影响
      assertThat(exception.getExtensions()).doesNotContainKey("newKey");
    }
  }

  @Nested
  @DisplayName("辅助方法测试")
  class HelperMethodTests {

    @Test
    @DisplayName("hasErrorCode - 有错误码")
    void hasErrorCodeShouldReturnTrueWhenPresent() {
      var exception =
          new RemoteCallException(ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, null);

      assertThat(exception.hasErrorCode()).isTrue();
    }

    @Test
    @DisplayName("hasErrorCode - 无错误码")
    void hasErrorCodeShouldReturnFalseWhenAbsent() {
      var exception = new RemoteCallException(HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID);

      assertThat(exception.hasErrorCode()).isFalse();
    }

    @Test
    @DisplayName("hasErrorCode - 空白错误码")
    void hasErrorCodeShouldReturnFalseWhenBlank() {
      var exception =
          new RemoteCallException("  ", HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, null);

      assertThat(exception.hasErrorCode()).isFalse();
    }

    @Test
    @DisplayName("hasTraceId - 有跟踪 ID")
    void hasTraceIdShouldReturnTrueWhenPresent() {
      var exception = new RemoteCallException(HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID);

      assertThat(exception.hasTraceId()).isTrue();
    }

    @Test
    @DisplayName("hasTraceId - 无跟踪 ID")
    void hasTraceIdShouldReturnFalseWhenAbsent() {
      var exception = new RemoteCallException(HTTP_STATUS, MESSAGE, METHOD_KEY, null);

      assertThat(exception.hasTraceId()).isFalse();
    }

    @Test
    @DisplayName("hasErrorTraits - 有 traits")
    void hasErrorTraitsShouldReturnTrueWhenPresent() {
      Map<String, Object> extensions = new HashMap<>();
      extensions.put(ErrorKeys.TRAITS, List.of("NOT_FOUND"));

      var exception =
          new RemoteCallException(
              ERROR_CODE, HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID, extensions);

      assertThat(exception.hasErrorTraits()).isTrue();
    }

    @Test
    @DisplayName("hasErrorTraits - 无 traits")
    void hasErrorTraitsShouldReturnFalseWhenAbsent() {
      var exception = new RemoteCallException(HTTP_STATUS, MESSAGE, METHOD_KEY, TRACE_ID);

      assertThat(exception.hasErrorTraits()).isFalse();
    }
  }
}
