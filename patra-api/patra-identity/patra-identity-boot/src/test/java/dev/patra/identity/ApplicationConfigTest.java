package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.ClassPathResource;

/// jar 里配置文件的约束（PAP-76）：随环境变的值在 Nacos，jar 里没有 profile 文件，
/// 服务名由启动器传入，Nacos 接入由 starter 注入。
@DisplayName("jar 内配置文件约束")
class ApplicationConfigTest {

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
  @DisplayName("可在线刷新的登录失败限制不写在 jar 里：默认值在配置类，要调就在 Nacos 里加键")
  void should_leave_refreshable_settings_to_nacos() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(name -> name.startsWith("patra.identity.login-throttle."));
  }

  @Test
  @DisplayName("identity 不发信（PAP-104）：jar 里没有邮件服务商、发件人与邮件健康检查的键；门户地址随环境变、在 Nacos")
  void should_carry_no_mail_sending_settings_and_keep_portal_address_in_nacos() throws IOException {
    assertThat(propertyNames("application.yml"))
        .noneMatch(
            name ->
                name.startsWith("spring.mail.")
                    || name.startsWith("management.health.mail.")
                    || name.equals("patra.identity.mail.from")
                    || name.equals("patra.identity.mail.links.base-url"));
  }

  @Test
  @DisplayName("安全邮件事件发完即删登记行（带一次性令牌）：identity 自己写死 delete，不跟事件模块的默认值走")
  void should_pin_event_completion_mode_to_delete() throws IOException {
    assertThat(property("application.yml", "spring.modulith.events.completion-mode"))
        .isEqualTo("delete");
  }

  @Test
  @DisplayName("账号安全的参数在 jar 里：再认证窗口、找回密码与改邮箱的限流、三个落地页路径")
  void should_keep_account_security_settings_in_jar() throws IOException {
    assertThat(property("application.yml", "patra.identity.reauth.window")).isEqualTo("10m");
    assertThat(property("application.yml", "patra.identity.password-reset.per-email-daily"))
        .isEqualTo("5");
    assertThat(property("application.yml", "patra.identity.password-reset.per-ip-hourly"))
        .isEqualTo("20");
    assertThat(property("application.yml", "patra.identity.password-reset.submit-per-ip-minutely"))
        .isEqualTo("10");
    assertThat(property("application.yml", "patra.identity.email-change.per-account-daily"))
        .isEqualTo("3");
    assertThat(property("application.yml", "patra.identity.mail.links.reset-password-path"))
        .isEqualTo("/reset-password");
    assertThat(property("application.yml", "patra.identity.mail.links.confirm-email-change-path"))
        .isEqualTo("/confirm-email-change");
    assertThat(property("application.yml", "patra.identity.mail.links.revert-email-change-path"))
        .isEqualTo("/revert-email-change");
  }

  /// 读出 classpath 上一份 YAML 里某个属性的值。
  ///
  /// @param resource classpath 上的文件名
  /// @param name 属性名
  /// @return 值的字符串形式；没有时为 `null`
  /// @throws IOException 读取失败时
  private static String property(String resource, String name) throws IOException {
    return new YamlPropertySourceLoader()
        .load(resource, new ClassPathResource(resource)).stream()
            .map(source -> source.getProperty(name))
            .filter(java.util.Objects::nonNull)
            .map(String::valueOf)
            .findFirst()
            .orElse(null);
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
