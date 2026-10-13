package dev.linqibin.patra.starter.nacos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/// 在 Spring Boot 加载配置之前，给环境补上 Nacos 的连接和服务发现设置。
///
/// 所有启动方式都经过它，包括测试，所以这里只放测试也需要的设置；服务名和导入由启动器
/// `PatraApplication` 负责。
///
/// - 默认值并入 default properties，优先级最低：服务的 `application.yml`、Nacos 配置、环境变量
///   都能覆盖；启动器已放进 default properties 的同名键保持不变。
/// - 环境里没有 `nacos:` 导入时（测试、没用启动器的应用）关闭 Nacos 配置中心，否则 SCA 的导入
///   检查会因为 `spring.config.import` 里没有 `nacos:` 让应用启动失败。这里只看得到已在环境里的
///   导入（启动器、启动参数、系统属性、环境变量）；写在 `application.yml` 里的导入这时还没加载，
///   按约定也不应该写在那里。
public class NacosDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 排在 Spring Boot 加载配置之前：SCA 解析 `nacos:` 导入时要读到这里注入的连接设置。
  public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER - 1;

  /// Nacos 配置中心的总开关。
  static final String CONFIG_ENABLED_PROPERTY = "spring.cloud.nacos.config.enabled";

  private static final String IMPORT_PROPERTY = "spring.config.import";
  private static final String OPTIONAL_PREFIX = "optional:";
  private static final String NACOS_PREFIX = "nacos:";

  /// 连接与服务发现的默认值。配置中心经 SCA 绑定 `spring.cloud.nacos` 取地址和账号，服务发现在
  /// 自己的地址、账号为空时也回落到这里。
  private static final Map<String, Object> DEFAULTS =
      Map.of(
          "spring.cloud.nacos.server-addr",
          "${PATRA_NACOS_ADDR:${PATRA_INFRA_HOST:127.0.0.1}:8848}",
          "spring.cloud.nacos.username",
          "${NACOS_USERNAME}",
          "spring.cloud.nacos.password",
          "${NACOS_PASSWORD}",
          "spring.cloud.nacos.discovery.service",
          "${spring.application.name}",
          "spring.cloud.nacos.discovery.ip",
          "${PATRA_ADVERTISE_IP:}",
          // nacos 3.x gRPC 握手未完成时同步注册会失败，fail-fast 会让整个应用启动失败
          "spring.cloud.nacos.discovery.fail-fast",
          "false");

  /// 补上默认值，并在没有 `nacos:` 导入时关闭配置中心。
  ///
  /// @param environment 要补充的环境
  /// @param application 正在启动的应用
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults = new LinkedHashMap<>(DEFAULTS);
    if (!hasNacosImport(environment)) {
      defaults.put(CONFIG_ENABLED_PROPERTY, "false");
    }
    addDefaults(environment.getPropertySources(), defaults);
  }

  /// 排序值，见 [#ORDER]。
  ///
  /// @return 排序值
  @Override
  public int getOrder() {
    return ORDER;
  }

  /// 环境里是否已有 `nacos:` 导入（含 `optional:nacos:`）。
  ///
  /// @param environment 要检查的环境
  /// @return 有任意一条 `nacos:` 导入时为 `true`
  private static boolean hasNacosImport(ConfigurableEnvironment environment) {
    List<String> imports =
        Binder.get(environment)
            .bind(IMPORT_PROPERTY, Bindable.listOf(String.class))
            .orElse(List.of());
    return imports.stream()
        .map(String::strip)
        .map(
            location ->
                location.startsWith(OPTIONAL_PREFIX)
                    ? location.substring(OPTIONAL_PREFIX.length())
                    : location)
        .anyMatch(location -> location.startsWith(NACOS_PREFIX));
  }

  /// 把默认值并入 default properties；已有的同名键（启动器放的）不覆盖。
  ///
  /// @param sources 环境的属性源
  /// @param defaults 要并入的默认值，会被修改
  private static void addDefaults(MutablePropertySources sources, Map<String, Object> defaults) {
    PropertySource<?> existing = sources.get(DefaultPropertiesPropertySource.NAME);
    if (existing != null) {
      defaults.keySet().removeIf(existing::containsProperty);
    }
    DefaultPropertiesPropertySource.addOrMerge(defaults, sources);
  }
}
