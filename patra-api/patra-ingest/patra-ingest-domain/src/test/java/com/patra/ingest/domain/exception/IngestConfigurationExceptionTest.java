package com.patra.ingest.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.trait.ErrorTrait;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link IngestConfigurationException} 的单元测试。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("IngestConfigurationException 单元测试")
class IngestConfigurationExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 provenanceCode、operationCode 和 message 构造异常")
    void shouldConstructWithProvenanceCodeOperationCodeAndMessage() {
      // Given
      String provenanceCode = "PUBMED";
      String operationCode = "FETCH_ARTICLES";
      String message = "配置缺失:必填字段 apiKey 为空";

      // When
      IngestConfigurationException exception =
          new IngestConfigurationException(provenanceCode, operationCode, message);

      // Then
      assertThat(exception.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(exception.getOperationCode()).isEqualTo(operationCode);
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 provenanceCode、operationCode、message 和 cause 构造异常")
    void shouldConstructWithProvenanceCodeOperationCodeMessageAndCause() {
      // Given
      String provenanceCode = "EPMC";
      String operationCode = "SEARCH";
      String message = "配置加载失败";
      Throwable cause = new RuntimeException("网络超时");

      // When
      IngestConfigurationException exception =
          new IngestConfigurationException(provenanceCode, operationCode, message, cause);

      // Then
      assertThat(exception.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(exception.getOperationCode()).isEqualTo(operationCode);
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isEqualTo(cause);
    }
  }

  @Nested
  @DisplayName("异常链测试")
  class ExceptionChainTests {

    @Test
    @DisplayName("应该正确传播异常链")
    void shouldPropagateExceptionChain() {
      // Given
      RuntimeException rootCause = new RuntimeException("根本原因");
      IllegalStateException cause = new IllegalStateException("中间原因", rootCause);
      IngestConfigurationException exception =
          new IngestConfigurationException("PUBMED", "FETCH", "配置错误", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("上下文信息测试")
  class ContextInformationTests {

    @Test
    @DisplayName("应该正确保存 provenanceCode 和 operationCode")
    void shouldCorrectlyStoreProvenanceCodeAndOperationCode() {
      // Given
      String provenanceCode = "ARXIV";
      String operationCode = "DOWNLOAD";
      IngestConfigurationException exception =
          new IngestConfigurationException(provenanceCode, operationCode, "配置错误");

      // When & Then
      assertThat(exception.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(exception.getOperationCode()).isEqualTo(operationCode);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTrait() {
      // Given
      IngestConfigurationException exception =
          new IngestConfigurationException("PUBMED", "FETCH", "配置错误");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(ErrorTrait.RULE_VIOLATION);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 IngestException")
    void shouldExtendIngestException() {
      // Given
      IngestConfigurationException exception =
          new IngestConfigurationException("PUBMED", "FETCH", "配置错误");

      // When & Then
      assertThat(exception).isInstanceOf(IngestException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      IngestConfigurationException exception =
          new IngestConfigurationException("PUBMED", "FETCH", "配置错误");

      // When & Then
      assertThat(exception).isInstanceOf(com.patra.common.error.trait.HasErrorTraits.class);
    }
  }
}
