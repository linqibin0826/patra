package dev.linqibin.starter.jpa.json;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.databind.ObjectMapper;
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
/// 通过 `HibernatePropertiesCustomizer` 自动注册到 Hibernate 配置：
///
/// ```java
/// hibernateProperties.put(AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper());
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

  /// Jackson 3.x ObjectMapper 实例。
  private final ObjectMapper objectMapper;

  /// 使用默认配置创建 Jackson3JsonFormatMapper。
  public Jackson3JsonFormatMapper() {
    this.objectMapper = JsonMapper.builder().build();
  }

  /// 使用自定义 ObjectMapper 创建 Jackson3JsonFormatMapper。
  ///
  /// @param objectMapper 自定义的 Jackson ObjectMapper
  public Jackson3JsonFormatMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> T fromString(
      CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
    if (charSequence == null) {
      return null;
    }
    // Jackson 3.x 的 readValue 不再抛出检查型异常，而是抛出 JacksonException（运行时异常）
    return objectMapper.readValue(
        charSequence.toString(), objectMapper.constructType(javaType.getJavaType()));
  }

  @Override
  public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
    if (value == null) {
      return null;
    }
    // Jackson 3.x 的 writeValueAsString 不再抛出检查型异常
    return objectMapper.writeValueAsString(value);
  }
}
