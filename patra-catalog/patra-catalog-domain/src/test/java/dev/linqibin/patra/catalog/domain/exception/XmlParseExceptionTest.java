package dev.linqibin.patra.catalog.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link XmlParseException} 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("XmlParseException 单元测试")
class XmlParseExceptionTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用 message 构造异常")
    void shouldConstructWithMessage() {
      // Given
      String message = "XML 格式不符合 DTD 规范";

      // When
      XmlParseException exception = new XmlParseException(message);

      // Then
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("应该使用 message 和 cause 构造异常")
    void shouldConstructWithMessageAndCause() {
      // Given
      String message = "XML 解析失败";
      Throwable cause = new XMLStreamException("意外的元素结束");

      // When
      XmlParseException exception = new XmlParseException(message, cause);

      // Then
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
      RuntimeException rootCause = new RuntimeException("文件读取失败");
      XMLStreamException cause = new XMLStreamException("XML 流异常", rootCause);
      XmlParseException exception = new XmlParseException("解析失败", cause);

      // When & Then
      assertThat(exception.getCause()).isEqualTo(cause);
      assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }
  }

  @Nested
  @DisplayName("ErrorTraits 测试")
  class ErrorTraitsTests {

    @Test
    @DisplayName("应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTrait() {
      // Given
      XmlParseException exception = new XmlParseException("XML 格式错误");

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(StandardErrorTrait.RULE_VIOLATION);
    }

    @Test
    @DisplayName("带 cause 时也应该包含 RULE_VIOLATION 错误特征")
    void shouldContainRuleViolationErrorTraitWithCause() {
      // Given
      XmlParseException exception =
          new XmlParseException("解析失败", new XMLStreamException("无效的 XML"));

      // When
      Set<ErrorTrait> traits = exception.getErrorTraits();

      // Then
      assertThat(traits).containsExactly(StandardErrorTrait.RULE_VIOLATION);
    }
  }

  @Nested
  @DisplayName("继承关系测试")
  class InheritanceTests {

    @Test
    @DisplayName("应该继承自 CatalogException")
    void shouldExtendCatalogException() {
      // Given
      XmlParseException exception = new XmlParseException("解析失败");

      // When & Then
      assertThat(exception).isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("应该实现 HasErrorTraits 接口")
    void shouldImplementHasErrorTraits() {
      // Given
      XmlParseException exception = new XmlParseException("解析失败");

      // When & Then
      assertThat(exception).isInstanceOf(HasErrorTraits.class);
    }

    @Test
    @DisplayName("应该是 RuntimeException")
    void shouldBeRuntimeException() {
      // Given
      XmlParseException exception = new XmlParseException("解析失败");

      // When & Then
      assertThat(exception).isInstanceOf(RuntimeException.class);
    }
  }
}
