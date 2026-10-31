package dev.patra.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;

/// jar 里配置文件的约束（PAP-76）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  /// Gateway 路由列表的配置前缀。
  private static final String ROUTES = "spring.cloud.gateway.server.webmvc.routes";

  @Test
  @DisplayName("classpath 上没有 profile 文件")
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }

  @Test
  @DisplayName("application.yml 不写服务名、激活的 profile 和 Nacos 接入")
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  @Test
  @DisplayName("配置刷新时 Gateway 读得出 HTTP 客户端设置：刷新用的新环境只认枚举常量原名")
  void should_let_gateway_read_http_client_settings_on_refresh() throws IOException {
    // ConfigDataContextRefresher 在新的 StandardEnvironment 上重跑环境后处理器，它用默认转换服务，
    // 不认 dont-follow 这类横线写法；正常启动时 Boot 换成了宽松的转换服务，所以只有刷新会失败。
    StandardEnvironment environment = new StandardEnvironment();
    new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml"))
        .forEach(environment.getPropertySources()::addLast);
    List<EnvironmentPostProcessor> gatewayHttpClient =
        SpringFactoriesLoader.forDefaultResourceLocation()
            .load(
                EnvironmentPostProcessor.class,
                ArgumentResolver.of(DeferredLogFactory.class, supplier -> supplier.get()),
                FailureHandler.logging(null))
            .stream()
            .filter(
                processor ->
                    processor
                        .getClass()
                        .getSimpleName()
                        .equals("GatewayHttpClientEnvironmentPostProcessor"))
            .toList();

    assertThat(gatewayHttpClient).hasSize(1);
    assertThatCode(
            () ->
                gatewayHttpClient
                    .getFirst()
                    .postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("推送路由排在通用 notification 路由之前、走 lb-push，推送池上限 500")
  void should_route_push_stream_before_general_notification_route() throws IOException {
    PropertySource<?> yaml =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"))
            .getFirst();
    List<Object> routeIds =
        IntStream.range(0, 20)
            .mapToObj(i -> yaml.getProperty(ROUTES + "[" + i + "].id"))
            .filter(Objects::nonNull)
            .toList();

    int push = routeIds.indexOf("patra-notification-stream");
    assertThat(push).isNotNegative().isLessThan(routeIds.indexOf("patra-notification"));
    assertThat(yaml.getProperty(ROUTES + "[" + push + "].uri"))
        .hasToString("lb-push://patra-notification");
    assertThat(yaml.getProperty("patra.gateway.push.max-connections")).hasToString("500");
  }

  /// 读出 classpath 上一份 YAML 的全部属性名，多文档合在一起。
  ///
  /// @param resource classpath 上的文件名
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames(String resource) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
}
