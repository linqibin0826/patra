package dev.patra.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;

/// jar 里配置文件的约束：随环境变的值在 Nacos，jar 里没有 profile 文件，服务名由启动器传入，
/// Nacos 接入由 starter 注入；Flyway 历史留在默认 schema。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

  /// classpath 上没有 profile 文件。
  @Test
  void should_not_ship_profile_files() {
    assertThat(getClass().getResource("/application-dev.yml")).isNull();
    assertThat(getClass().getResource("/application-container.yml")).isNull();
  }

  /// `application.yml` 不写服务名、激活的 profile 和 Nacos 接入。
  ///
  /// @throws IOException 读取失败时
  @Test
  void should_leave_name_profile_and_nacos_to_launcher_and_starter() throws IOException {
    assertThat(propertyNames())
        .noneMatch(
            name ->
                name.startsWith("spring.application.name")
                    || name.startsWith("spring.profiles.active")
                    || name.startsWith("spring.cloud.nacos"));
  }

  /// 端口 6600，错误码前缀 `NOTIFICATION`。
  ///
  /// @throws IOException 读取失败时
  @Test
  void should_listen_on_6600_with_notification_error_prefix() throws IOException {
    assertThat(property("server.port")).isEqualTo("6600");
    assertThat(property("patra.web.error.context-prefix")).isEqualTo("NOTIFICATION");
  }

  /// 推送（PAP-145）：心跳短于网关读超时 60 秒、Cloudflare 125 秒；寿命 30 分钟，也是显式设的异步超时
  /// （不设是 Tomcat 默认的 30 秒）。
  @Test
  @DisplayName("推送心跳 25 秒、连接寿命 30 分钟")
  void should_set_realtime_heartbeat_and_connection_age() throws IOException {
    assertThat(property("patra.notification.realtime.heartbeat-interval")).isEqualTo("25s");
    assertThat(property("patra.notification.realtime.max-connection-age")).isEqualTo("30m");
  }

  /// Flyway 递归扫 `db/migration`，不设 `schemas`：设了 Flyway 会自己建 schema 并把历史表挪进去。
  ///
  /// @throws IOException 读取失败时
  @Test
  void should_keep_flyway_history_in_default_schema() throws IOException {
    assertThat(property("spring.flyway.enabled")).isEqualTo("true");
    assertThat(property("spring.flyway.locations")).isEqualTo("classpath:db/migration");
    assertThat(propertyNames()).noneMatch(name -> name.startsWith("spring.flyway.schemas"));
  }

  /// notification 不连 Redis（compose 也不加 `redis.env`）。
  ///
  /// @throws IOException 读取失败时
  @Test
  void should_not_configure_redis() throws IOException {
    assertThat(propertyNames()).noneMatch(name -> name.startsWith("spring.data.redis"));
  }

  /// 信箱监听按 `__TypeId__` 分发，信任包里要有 interaction 三个事件包：少了有帮助的包，`helpful.marked` 反序列化失败、
  /// 重试三次后进死信。
  ///
  /// @throws IOException 读取失败时
  @Test
  void should_trust_interaction_event_packages() throws IOException {
    List<String> trusted = new ArrayList<>();
    for (int i = 0; property("patra.events.consumer.trusted-packages[" + i + "]") != null; i++) {
      trusted.add(property("patra.events.consumer.trusted-packages[" + i + "]"));
    }

    assertThat(trusted)
        .contains(
            "dev.patra.interaction.api.discussion.event",
            "dev.patra.interaction.api.post.event",
            "dev.patra.interaction.api.reaction.event");
  }

  /// 读出 `application.yml` 里某个属性的值。
  ///
  /// @param name 属性名
  /// @return 值的字符串形式；没有时为 `null`
  /// @throws IOException 读取失败时
  private static String property(String name) throws IOException {
    return new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml")).stream()
            .map(source -> source.getProperty(name))
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .findFirst()
            .orElse(null);
  }

  /// 读出 `application.yml` 的全部属性名，多文档合在一起。
  ///
  /// @return 扁平化后的属性名
  /// @throws IOException 读取失败时
  private static List<String> propertyNames() throws IOException {
    return new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml")).stream()
            .map(source -> (EnumerablePropertySource<?>) source)
            .flatMap(source -> Arrays.stream(source.getPropertyNames()))
            .toList();
  }
}
