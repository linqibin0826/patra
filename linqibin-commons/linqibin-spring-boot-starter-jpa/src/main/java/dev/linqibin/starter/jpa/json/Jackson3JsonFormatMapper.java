package dev.linqibin.starter.jpa.json;

import java.lang.reflect.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.databind.json.JsonMapper;

/// Jackson 3.x JSON 格式映射器。
///
/// 解决 Hibernate 7.1 与 Jackson 3.x 的兼容性问题。
/// Hibernate 7.1 的默认 JSON 格式映射器无法自动检测 Jackson 3.x，
/// 因为 Jackson 3.x 将包名从 `com.fasterxml.jackson` 改为 `tools.jackson`。
///
/// 此类实现 Hibernate 的 `FormatMapper` 接口，使用 Jackson 3.x 的 `JsonMapper`
/// 进行 JSON 序列化和反序列化。
///
/// ### 配置方式
///
/// 由 `HibernatePropertiesCustomizer` 用注入的容器 `JsonMapper` 创建并注册到 Hibernate 配置，
/// JSON 列与应用其他部分共用同一份 Jackson 配置：
///
/// ```java
/// hibernateProperties.put(
///     AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(jsonMapper));
/// ```
///
/// ### 参考资料
///
/// - [Hibernate ORM Forum: Missing FormatMapper for JSON format with Jackson
// 3.x](https://discourse.hibernate.org/t/missing-formatmapper-for-json-format-with-jackson-3-x-hibernate-7-x/11819)
/// - [Spring Boot Issue #33870](https://github.com/spring-projects/spring-boot/issues/33870)
///
/// @author linqibin
/// @since 0.1.0
/// @see FormatMapper
public class Jackson3JsonFormatMapper implements FormatMapper {

  /// Jackson 3.x JsonMapper 实例（容器注入）。
  private final JsonMapper objectMapper;

  /// 使用给定 JsonMapper 创建 Jackson3JsonFormatMapper。
  ///
  /// @param objectMapper 容器里的 Jackson JsonMapper
  public Jackson3JsonFormatMapper(JsonMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /// 将 JSON 文本反序列化为目标类型的值。
  ///
  /// String/Object 目标类型直接透传原始 JSON 文本（与 Hibernate 官方
  /// `AbstractJsonFormatMapper` 对齐），其余类型经 Jackson 反序列化。
  ///
  /// @param charSequence JSON 文本，可为 null
  /// @param javaType Hibernate 目标类型描述
  /// @param wrapperOptions Hibernate 包装选项（未使用）
  /// @return 反序列化后的值；入参为 null 时返回 null
  @Override
  @SuppressWarnings("unchecked")
  public <T> T fromString(
      CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
    if (charSequence == null) {
      return null;
    }
    // 与 Hibernate 官方 AbstractJsonFormatMapper 对齐：String/Object 目标类型透传原始 JSON 文本，
    // 不经 Jackson 反序列化——否则 JSONB 对象读进 String 字段（如 BaseJpaEntity.recordRemarks）
    // 会抛 "Cannot deserialize value of type String from Object value"
    final Type type = javaType.getJavaType();
    if (type == String.class || type == Object.class) {
      return (T) charSequence.toString();
    }
    // Jackson 3.x 的 readValue 不再抛出检查型异常，而是抛出 JacksonException（运行时异常）
    return objectMapper.readValue(charSequence.toString(), objectMapper.constructType(type));
  }

  /// 将值序列化为 JSON 文本。
  ///
  /// String 值（含 Object 目标类型下的 String 值）直接透传，避免二次序列化成
  /// 带引号的 JSON 字符串；其余类型经 Jackson 序列化。
  ///
  /// @param value 待序列化的值，可为 null
  /// @param javaType Hibernate 目标类型描述
  /// @param wrapperOptions Hibernate 包装选项（未使用）
  /// @return JSON 文本；入参为 null 时返回 null
  @Override
  public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
    if (value == null) {
      return null;
    }
    // 对称透传：String 属性本身承载 JSON 文本，直接写出，不做二次序列化（会变成带引号的 JSON 字符串）
    final Type type = javaType.getJavaType();
    if (type == String.class || (type == Object.class && value instanceof String)) {
      return (String) value;
    }
    // Jackson 3.x 的 writeValueAsString 不再抛出检查型异常
    return objectMapper.writeValueAsString(value);
  }
}
