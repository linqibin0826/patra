package dev.patra.tool.jpa.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.json.Jackson3JsonFormatMapper;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.descriptor.java.spi.UnknownBasicJavaType;
import org.hibernate.type.format.FormatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/// HibernatePropertiesCustomizer 单元测试：JSON 列的 FormatMapper 使用注入的容器 `JsonMapper`。
@DisplayName("PatraHibernatePropertiesCustomizer 单元测试")
class PatraHibernatePropertiesCustomizerTest {

  /// 只有测试模块知道怎么序列化的类型。
  record Tagged(String name) {}

  /// 带 `Long` 字段的类型。
  record Identified(Long id) {}

  /// 把 {@link Tagged} 写成固定字符串的序列化器。
  static class TaggedSerializer extends ValueSerializer<Tagged> {

    /// 忽略值内容，固定写出标记字符串。
    ///
    /// @param value 待序列化的值
    /// @param gen JSON 生成器
    /// @param ctxt 序列化上下文
    @Override
    public void serialize(Tagged value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeString("written-by-injected-mapper");
    }
  }

  /// 用给定 mapper 执行定制，取出注册的 JSON FormatMapper。
  ///
  /// @param jsonMapper 注入的 mapper
  /// @return Hibernate 属性里的 JSON FormatMapper
  private static FormatMapper customizeAndGetFormatMapper(JsonMapper jsonMapper) {
    Map<String, Object> properties = new HashMap<>();
    new PatraHibernatePropertiesCustomizer(jsonMapper).customize(properties);
    Object formatMapper = properties.get(AvailableSettings.JSON_FORMAT_MAPPER);
    assertThat(formatMapper).isInstanceOf(Jackson3JsonFormatMapper.class);
    return (FormatMapper) formatMapper;
  }

  @Test
  @DisplayName("FormatMapper 使用注入的 JsonMapper 序列化")
  void should_serialize_with_injected_mapper() {
    SimpleModule testOnlyModule = new SimpleModule("test-only");
    testOnlyModule.addSerializer(Tagged.class, new TaggedSerializer());
    JsonMapper jsonMapper = JsonMapper.builder().addModule(testOnlyModule).build();

    FormatMapper formatMapper = customizeAndGetFormatMapper(jsonMapper);
    String json =
        formatMapper.toString(
            new Tagged("ignored"), new UnknownBasicJavaType<>(Tagged.class), null);

    assertThat(json).isEqualTo("\"written-by-injected-mapper\"");
  }

  @Test
  @DisplayName("Long 字段写成数字而不是字符串")
  void should_write_long_field_as_number() {
    FormatMapper formatMapper = customizeAndGetFormatMapper(JsonMapper.builder().build());

    String json =
        formatMapper.toString(
            new Identified(1L), new UnknownBasicJavaType<>(Identified.class), null);

    assertThat(json).isEqualTo("{\"id\":1}");
  }
}
