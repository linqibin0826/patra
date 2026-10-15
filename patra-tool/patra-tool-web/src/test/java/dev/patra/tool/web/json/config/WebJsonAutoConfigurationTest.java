package dev.patra.tool.web.json.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.web.json.LongToStringHttpMessageConvertersCustomizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import tools.jackson.databind.json.JsonMapper;

/// {@link WebJsonAutoConfiguration} 契约测试。
///
/// 钉住"Long → String 只发生在 MVC 响应"这条边界：
///
/// - 容器里的 `JsonMapper` bean 把 `Long` 写成数字，持久化、消息、服务间请求体共用它
/// - MVC 响应的 JSON 转换器把包装类型 `Long` 写成字符串，原始类型 `long` 仍是数字
/// - 派生出的响应转换器保留 Boot 的 ProblemDetail 定制（`properties` 平铺到顶层）
/// - 非 Servlet 环境不注册任何 bean
@DisplayName("WebJsonAutoConfiguration 契约测试")
class WebJsonAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  WebJsonAutoConfiguration.class));

  /// 带一个包装类型和一个原始类型的响应体。
  record Payload(Long id, long count) {}

  @Test
  @DisplayName("容器里的 JsonMapper 把 Long 写成数字")
  void jsonMapperBean_writes_long_as_number() {
    contextRunner.run(
        context -> {
          JsonMapper jsonMapper = context.getBean(JsonMapper.class);

          assertThat(jsonMapper.writeValueAsString(new Payload(1L, 2L)))
              .isEqualTo("{\"id\":1,\"count\":2}");
        });
  }

  @Test
  @DisplayName("MVC 响应转换器把包装类型 Long 写成字符串，原始类型 long 仍是数字")
  void serverConverter_writes_boxed_long_as_string() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(LongToStringHttpMessageConvertersCustomizer.class);

          String json = writeWithServerJsonConverter(context, new Payload(1L, 2L));

          assertThat(json).isEqualTo("{\"id\":\"1\",\"count\":2}");
        });
  }

  @Test
  @DisplayName("派生的响应转换器保留 Boot 的 ProblemDetail 定制")
  void serverConverter_keeps_problem_detail_mixin() {
    contextRunner.run(
        context -> {
          ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
          problem.setProperty("code", "REG-0409");

          String json = writeWithServerJsonConverter(context, problem);

          assertThat(json).contains("\"code\":\"REG-0409\"").doesNotContain("\"properties\"");
        });
  }

  @Test
  @DisplayName("非 Servlet 环境不注册定制器")
  void nonServlet_registers_nothing() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(JacksonAutoConfiguration.class, WebJsonAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(LongToStringHttpMessageConvertersCustomizer.class));
  }

  /// 按容器里定制器的顺序装配服务端转换器，再用其中的 JSON 转换器写出 `body`。
  ///
  /// 这和 Spring MVC 自动配置装配转换器的方式一致，能验证本定制器排在 Boot 的之后。
  ///
  /// @param context 已启动的容器
  /// @param body 要写出的响应体
  /// @return 写出的 JSON 文本
  /// @throws IOException 写出失败
  private static String writeWithServerJsonConverter(ApplicationContext context, Object body)
      throws IOException {
    HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer();
    context
        .getBeanProvider(ServerHttpMessageConvertersCustomizer.class)
        .orderedStream()
        .forEach(customizer -> customizer.customize(builder));
    HttpMessageConverter<Object> converter = jsonConverter(builder.build());

    MockHttpOutputMessage output = new MockHttpOutputMessage();
    converter.write(body, MediaType.APPLICATION_JSON, output);
    return output.getBodyAsString(StandardCharsets.UTF_8);
  }

  /// 从转换器列表里取出 Jackson 的 JSON 转换器。
  ///
  /// @param converters 装配好的转换器
  /// @return JSON 转换器
  @SuppressWarnings("unchecked")
  private static HttpMessageConverter<Object> jsonConverter(HttpMessageConverters converters) {
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof JacksonJsonHttpMessageConverter) {
        return (HttpMessageConverter<Object>) converter;
      }
    }
    throw new AssertionError("没有找到 JacksonJsonHttpMessageConverter");
  }
}
