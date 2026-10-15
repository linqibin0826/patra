package dev.patra.tool.web.json.config;

import dev.patra.tool.web.json.LongToStringHttpMessageConvertersCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/// MVC 响应的 JSON 定制。
///
/// 只注册 [LongToStringHttpMessageConvertersCustomizer]：包装类型 `Long` 在 HTTP 响应里输出
/// 字符串。容器里的 `JsonMapper` 由 Boot 装配，本模块不另建 mapper。
///
/// `@WebMvcTest` 切片不会自动带上本配置，需要响应里 Long → String 的切片测试要显式
/// `@ImportAutoConfiguration(WebJsonAutoConfiguration.class)`。
@AutoConfiguration(
    after = {JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(JsonMapper.class)
public class WebJsonAutoConfiguration {

  /// 注册把 MVC 响应里的 `Long` 写成字符串的转换器定制器。
  ///
  /// @param jsonMapper 容器里的 `JsonMapper`
  /// @return 转换器定制器
  @Bean
  @ConditionalOnMissingBean
  public LongToStringHttpMessageConvertersCustomizer longToStringHttpMessageConvertersCustomizer(
      JsonMapper jsonMapper) {
    return new LongToStringHttpMessageConvertersCustomizer(jsonMapper);
  }
}
