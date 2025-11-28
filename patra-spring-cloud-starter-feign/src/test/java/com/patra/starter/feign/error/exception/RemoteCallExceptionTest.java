package com.patra.starter.feign.error.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.problem.ErrorKeys;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/// RemoteCallException 单元测试。
@DisplayName("RemoteCallException 单元测试")
class RemoteCallExceptionTest {

  @Nested
  @DisplayName("HasErrorTraits 接口实现测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该从 ProblemDetail 解析单个 trait")
    void shouldParseSingleTraitFromProblemDetail() {
      // Given: 包含 traits 的 ProblemDetail
      ProblemDetail problemDetail = ProblemDetail.forStatus(404);
      problemDetail.setDetail("资源未找到");
      problemDetail.setProperty(ErrorKeys.CODE, "REG-0404");
      problemDetail.setProperty(ErrorKeys.TRAITS, List.of("NOT_FOUND"));

      // When: 创建 RemoteCallException
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#find");

      // Then: 验证 traits 被正确解析
      assertThat(exception.hasErrorTraits()).isTrue();
      assertThat(exception.getErrorTraits()).containsExactly(StandardErrorTrait.NOT_FOUND);
    }

    @Test
    @DisplayName("应该从 ProblemDetail 解析多个 traits")
    void shouldParseMultipleTraitsFromProblemDetail() {
      // Given: 包含多个 traits 的 ProblemDetail
      ProblemDetail problemDetail = ProblemDetail.forStatus(409);
      problemDetail.setDetail("冲突");
      problemDetail.setProperty(ErrorKeys.CODE, "REG-0409");
      problemDetail.setProperty(ErrorKeys.TRAITS, List.of("CONFLICT", "RULE_VIOLATION"));

      // When: 创建 RemoteCallException
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#update");

      // Then: 验证所有 traits 被解析
      assertThat(exception.hasErrorTraits()).isTrue();
      Set<ErrorTrait> traits = exception.getErrorTraits();
      assertThat(traits).hasSize(2);
      assertThat(traits)
          .containsExactlyInAnyOrder(
              StandardErrorTrait.CONFLICT, StandardErrorTrait.RULE_VIOLATION);
    }

    @Test
    @DisplayName("应该忽略未知的 trait 名称")
    void shouldIgnoreUnknownTraitNames() {
      // Given: 包含未知 trait 的 ProblemDetail
      ProblemDetail problemDetail = ProblemDetail.forStatus(500);
      problemDetail.setDetail("错误");
      problemDetail.setProperty(
          ErrorKeys.TRAITS, List.of("NOT_FOUND", "UNKNOWN_TRAIT", "CONFLICT"));

      // When: 创建 RemoteCallException
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#call");

      // Then: 只解析已知的 traits
      assertThat(exception.hasErrorTraits()).isTrue();
      Set<ErrorTrait> traits = exception.getErrorTraits();
      assertThat(traits).hasSize(2);
      assertThat(traits)
          .containsExactlyInAnyOrder(StandardErrorTrait.NOT_FOUND, StandardErrorTrait.CONFLICT);
    }

    @Test
    @DisplayName("应该在 traits 字段为空时返回空集合")
    void shouldReturnEmptySetWhenTraitsFieldIsEmpty() {
      // Given: traits 字段为空列表的 ProblemDetail
      ProblemDetail problemDetail = ProblemDetail.forStatus(500);
      problemDetail.setDetail("错误");
      problemDetail.setProperty(ErrorKeys.TRAITS, List.of());

      // When: 创建 RemoteCallException
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#call");

      // Then: traits 应为空
      assertThat(exception.hasErrorTraits()).isFalse();
      assertThat(exception.getErrorTraits()).isEmpty();
    }

    @Test
    @DisplayName("应该在无 traits 字段时返回空集合")
    void shouldReturnEmptySetWhenNoTraitsField() {
      // Given: 没有 traits 字段的 ProblemDetail
      ProblemDetail problemDetail = ProblemDetail.forStatus(404);
      problemDetail.setDetail("未找到");
      problemDetail.setProperty(ErrorKeys.CODE, "REG-0404");
      // 没有设置 traits

      // When: 创建 RemoteCallException
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#find");

      // Then: traits 应为空
      assertThat(exception.hasErrorTraits()).isFalse();
      assertThat(exception.getErrorTraits()).isEmpty();
    }

    @Test
    @DisplayName("应该对 trait 名称大小写不敏感")
    void shouldBeCaseInsensitiveForTraitNames() {
      // Given: 包含小写 trait 名称的 ProblemDetail
      ProblemDetail problemDetail = ProblemDetail.forStatus(404);
      problemDetail.setDetail("未找到");
      problemDetail.setProperty(ErrorKeys.TRAITS, List.of("not_found", "CONFLICT", "Timeout"));

      // When: 创建 RemoteCallException
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#find");

      // Then: 所有 traits 都应该被正确解析
      assertThat(exception.hasErrorTraits()).isTrue();
      Set<ErrorTrait> traits = exception.getErrorTraits();
      assertThat(traits).hasSize(3);
      assertThat(traits)
          .containsExactlyInAnyOrder(
              StandardErrorTrait.NOT_FOUND,
              StandardErrorTrait.CONFLICT,
              StandardErrorTrait.TIMEOUT);
    }

    @Test
    @DisplayName("使用简单构造函数时 traits 应为空")
    void shouldHaveEmptyTraitsWithSimpleConstructor() {
      // When: 使用简单构造函数创建异常
      RemoteCallException exception =
          new RemoteCallException(500, "服务器错误", "TestClient#call", "trace-123");

      // Then: traits 应为空
      assertThat(exception.hasErrorTraits()).isFalse();
      assertThat(exception.getErrorTraits()).isEmpty();
    }

    @Test
    @DisplayName("使用完整构造函数并显式传入 traits")
    void shouldAcceptExplicitTraitsInConstructor() {
      // Given: 显式指定的 traits
      Set<ErrorTrait> traits = Set.of(StandardErrorTrait.NOT_FOUND, StandardErrorTrait.TIMEOUT);

      // When: 使用完整构造函数
      RemoteCallException exception =
          new RemoteCallException(
              "ERR-0404", 404, "未找到", "TestClient#find", "trace-123", Map.of(), traits);

      // Then: traits 应该正确设置
      assertThat(exception.hasErrorTraits()).isTrue();
      assertThat(exception.getErrorTraits())
          .containsExactlyInAnyOrder(StandardErrorTrait.NOT_FOUND, StandardErrorTrait.TIMEOUT);
    }
  }

  @Nested
  @DisplayName("基本功能测试")
  class BasicFunctionalityTests {

    @Test
    @DisplayName("应该从 ProblemDetail 提取错误码")
    void shouldExtractErrorCodeFromProblemDetail() {
      // Given
      ProblemDetail problemDetail = ProblemDetail.forStatus(404);
      problemDetail.setProperty(ErrorKeys.CODE, "REG-0404");
      problemDetail.setProperty(ErrorKeys.TRACE_ID, "trace-abc");

      // When
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#find");

      // Then
      assertThat(exception.getErrorCode()).isEqualTo("REG-0404");
      assertThat(exception.hasErrorCode()).isTrue();
      assertThat(exception.getTraceId()).isEqualTo("trace-abc");
      assertThat(exception.hasTraceId()).isTrue();
      assertThat(exception.getHttpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("应该保留所有扩展属性")
    void shouldPreserveAllExtensions() {
      // Given
      ProblemDetail problemDetail = ProblemDetail.forStatus(400);
      problemDetail.setProperty("customField", "customValue");
      problemDetail.setProperty("numericField", 42);

      // When
      RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#call");

      // Then
      assertThat(exception.getExtension("customField")).isEqualTo("customValue");
      assertThat(exception.getExtension("numericField", Integer.class)).isEqualTo(42);
      assertThat(exception.getAllExtensions()).containsKey("customField");
    }
  }
}
