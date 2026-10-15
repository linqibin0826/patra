package dev.patra.tool.web.json;

import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/// 让 MVC 响应里的包装类型 `Long` 以字符串输出。
///
/// 雪花 ID 超过 JavaScript 的安全整数范围（2^53），浏览器端必须拿到字符串。这是 HTTP 响应的
/// 策略，所以只挂在 MVC 的 JSON 转换器上：转换器用的是从容器 `JsonMapper` 派生出来的实例，
/// 保留 Boot 的全部默认配置与 ProblemDetail 定制，只多一个 `Long` 序列化器。容器里的
/// `JsonMapper` 本身不受影响，持久化、消息、服务间请求体里的 `Long` 仍是数字。
///
/// 原始类型 `long` 不受影响。
public final class LongToStringHttpMessageConvertersCustomizer
    implements ServerHttpMessageConvertersCustomizer, Ordered {

  /// Boot 自带的 Jackson 转换器定制器是 0，本定制器排在它之后才能替换掉它注册的 JSON 转换器。
  public static final int ORDER = 100;

  private final JacksonJsonHttpMessageConverter jsonConverter;

  /// 从容器的 `JsonMapper` 派生出带 Long → String 序列化器的响应转换器。
  ///
  /// @param jsonMapper 容器里的 `JsonMapper`
  public LongToStringHttpMessageConvertersCustomizer(JsonMapper jsonMapper) {
    SimpleModule module = new SimpleModule("LongToString");
    module.addSerializer(Long.class, ToStringSerializer.instance);
    this.jsonConverter =
        new JacksonJsonHttpMessageConverter(jsonMapper.rebuild().addModule(module).build());
  }

  /// 用派生的转换器替换服务端的 JSON 转换器。
  ///
  /// @param builder 服务端转换器装配器
  @Override
  public void customize(HttpMessageConverters.ServerBuilder builder) {
    builder.withJsonConverter(jsonConverter);
  }

  /// 排序值，见 [#ORDER]。
  ///
  /// @return 排序值
  @Override
  public int getOrder() {
    return ORDER;
  }
}
