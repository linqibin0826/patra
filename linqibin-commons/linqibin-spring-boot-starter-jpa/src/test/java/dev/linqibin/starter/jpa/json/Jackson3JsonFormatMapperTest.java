package dev.linqibin.starter.jpa.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.hibernate.type.descriptor.java.spi.UnknownBasicJavaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/// {@link Jackson3JsonFormatMapper} 单元测试。
///
/// 测试策略: 纯单元测试，验证与 Hibernate 官方 AbstractJsonFormatMapper 对齐的
/// String/Object 透传特例，以及非 String 类型的 Jackson 序列化/反序列化。
///
/// 测试覆盖:
///
/// - ✅ String 目标类型 - JSON 对象文本原样透传（JSONB → String 属性，如 recordRemarks）
/// - ✅ String 值写出 - 不做二次序列化（不会变成带引号的 JSON 字符串）
/// - ✅ Object 目标类型 - 读写两个方向的透传分支，非 String 值仍走 Jackson
/// - ✅ 空文本边界 - 原样透传
/// - ✅ 非 String 类型 - 走 Jackson 正常反序列化/序列化
/// - ✅ 非法 JSON - Jackson 反序列化抛运行时异常
/// - ✅ null 双向 - 返回 null
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("Jackson3JsonFormatMapper JSON 格式映射器单元测试")
class Jackson3JsonFormatMapperTest {

  private final Jackson3JsonFormatMapper mapper =
      new Jackson3JsonFormatMapper(JsonMapper.builder().build());

  @Nested
  @DisplayName("String 透传测试")
  class StringPassthroughTests {

    /// 验证 JSONB 对象文本读入 String 目标类型时原样透传，不经 Jackson 反序列化。
    @Test
    @DisplayName("JSON 对象文本读入 String 类型应该原样透传")
    void shouldPassThroughJsonObjectTextToString() {
      String json = "{\"migrated_from\": \"activity-svc\"}";

      String result = mapper.fromString(json, StringJavaType.INSTANCE, null);

      assertThat(result).isEqualTo(json);
    }

    /// 验证 String 值写出时原样透传，不被二次序列化成带引号的 JSON 字符串。
    @Test
    @DisplayName("String 值写出应该原样透传而非二次序列化")
    void shouldWriteStringValueAsIs() {
      String json = "{\"k\": 1}";

      String result = mapper.toString(json, StringJavaType.INSTANCE, null);

      assertThat(result).isEqualTo(json);
    }

    /// 验证空文本双向原样透传，不触发 Jackson 解析。
    @Test
    @DisplayName("空文本双向都应该原样透传")
    void shouldPassThroughEmptyText() {
      assertThat(mapper.fromString("", StringJavaType.INSTANCE, null)).isEmpty();
      assertThat(mapper.toString("", StringJavaType.INSTANCE, null)).isEmpty();
    }

    /// 验证 null 双向都返回 null。
    @Test
    @DisplayName("null 双向都应该返回 null")
    void shouldReturnNullForNull() {
      assertThat(mapper.fromString(null, StringJavaType.INSTANCE, null)).isNull();
      assertThat(mapper.toString(null, StringJavaType.INSTANCE, null)).isNull();
    }
  }

  @Nested
  @DisplayName("Object 透传测试")
  class ObjectPassthroughTests {

    private final UnknownBasicJavaType<Object> objectType =
        new UnknownBasicJavaType<>(Object.class);

    /// 验证 JSON 对象文本读入 Object 目标类型时原样透传，与 String 分支对齐。
    @Test
    @DisplayName("JSON 对象文本读入 Object 类型应该原样透传")
    void shouldPassThroughJsonObjectTextToObject() {
      String json = "{\"migrated_from\": \"gallery-svc\"}";

      Object result = mapper.fromString(json, objectType, null);

      assertThat(result).isEqualTo(json);
    }

    /// 验证 Object 目标类型下 String 值写出原样透传。
    @Test
    @DisplayName("Object 类型下 String 值写出应该原样透传")
    void shouldWriteStringValueAsIsForObjectType() {
      String json = "{\"k\": 1}";

      String result = mapper.toString((Object) json, objectType, null);

      assertThat(result).isEqualTo(json);
    }

    /// 验证 Object 目标类型下非 String 值仍走 Jackson 序列化。
    @Test
    @DisplayName("Object 类型下非 String 值应该走 Jackson 序列化")
    void shouldSerializeNonStringValueViaJacksonForObjectType() {
      String result = mapper.toString((Object) Map.of("a", 1), objectType, null);

      assertThat(result).isEqualTo("{\"a\":1}");
    }
  }

  @Nested
  @DisplayName("非 String 类型测试")
  class NonStringTests {

    /// 验证 Map 目标类型走 Jackson 反序列化为键值对。
    @Test
    @DisplayName("Map 类型应该走 Jackson 反序列化")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldDeserializeMapViaJackson() {
      UnknownBasicJavaType<Map> mapType = new UnknownBasicJavaType<>(Map.class);

      Map result = mapper.fromString("{\"a\": 1}", mapType, null);

      assertThat(result).containsEntry("a", 1);
    }

    /// 验证 Map 值走 Jackson 序列化为 JSON 文本。
    @Test
    @DisplayName("Map 值应该走 Jackson 序列化为 JSON 文本")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSerializeMapViaJackson() {
      UnknownBasicJavaType<Map> mapType = new UnknownBasicJavaType<>(Map.class);

      String result = mapper.toString(Map.of("a", 1), mapType, null);

      assertThat(result).isEqualTo("{\"a\":1}");
    }

    /// 验证非法 JSON 读入 Map 目标类型时抛出 Jackson 运行时异常。
    @Test
    @DisplayName("非法 JSON 读入 Map 类型应该抛出 Jackson 异常")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldThrowOnInvalidJsonForMap() {
      UnknownBasicJavaType<Map> mapType = new UnknownBasicJavaType<>(Map.class);

      assertThatThrownBy(() -> mapper.fromString("{not-json", mapType, null))
          .isInstanceOf(JacksonException.class);
    }
  }
}
