package com.patra.catalog.infra.adapter.parser.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// XmlParsingContext 单元测试。
///
/// 验证解析上下文的创建和属性访问。
@DisplayName("XmlParsingContext 解析上下文")
class XmlParsingContextTest {

  @Test
  @DisplayName("of() 应创建包含版本号的上下文")
  void of_shouldCreateContextWithVersion() {
    var context = XmlParsingContext.of("2025");
    assertEquals("2025", context.meshVersion());
  }

  @Test
  @DisplayName("of() 应支持任意版本号字符串")
  void of_shouldSupportAnyVersionString() {
    var context = XmlParsingContext.of("2024-preview");
    assertEquals("2024-preview", context.meshVersion());
  }

  @Test
  @DisplayName("empty() 应创建版本号为 null 的上下文")
  void empty_shouldCreateContextWithNullVersion() {
    var context = XmlParsingContext.empty();
    assertNull(context.meshVersion());
  }

  @Test
  @DisplayName("record 应正确实现 equals")
  void record_shouldImplementEquals() {
    var context1 = XmlParsingContext.of("2025");
    var context2 = XmlParsingContext.of("2025");
    assertEquals(context1, context2);
  }
}
