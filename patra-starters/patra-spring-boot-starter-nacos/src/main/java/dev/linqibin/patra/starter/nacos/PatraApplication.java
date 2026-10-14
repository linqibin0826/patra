package dev.linqibin.patra.starter.nacos;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/// Patra 服务的启动器，服务的启动类用它代替 `SpringApplication.run`：
///
/// ```java
/// PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
/// ```
///
/// 启动前把服务名、Nacos 配置的导入列表等放进 Spring 的 default properties（优先级最低，
/// 环境变量、配置文件、Nacos 都能覆盖），服务的配置文件里因此不写任何 Nacos 配置。
///
/// 导入不写在配置文件里，原因有两个：
/// - 初始导入里的服务名占位符解析不了；
/// - 写在 `application.yml` 里的导入，测试关不掉。
///
/// 测试不经过启动器：不导入 Nacos 配置，也不做签到检查。
public final class PatraApplication {

  /// 跨服务共享的 Nacos 配置。
  static final String COMMON_DATA_ID = "patra-common.yaml";

  /// 配置中心的 namespace：只有 dev 一个环境。
  static final String NAMESPACE = "dev";

  /// 只有静态方法，不实例化。
  private PatraApplication() {}

  /// 以 Patra 服务的方式启动应用。
  ///
  /// @param applicationName 服务名，也是 Nacos 里本服务配置的文件名（`{服务名}.yaml`）
  /// @param source 启动类
  /// @param args 启动参数
  /// @return 启动好的应用上下文
  public static ConfigurableApplicationContext run(
      String applicationName, Class<?> source, String... args) {
    SpringApplication application = new SpringApplication(source);
    application.setDefaultProperties(defaultProperties(applicationName));
    return application.run(args);
  }

  /// 启动器放进 default properties 的内容。
  ///
  /// @param applicationName 服务名
  /// @return 服务名、两条导入、namespace、Nacos 优先、签到开关
  /// @throws IllegalArgumentException 服务名为空时
  static Map<String, Object> defaultProperties(String applicationName) {
    if (applicationName == null || applicationName.isBlank()) {
      throw new IllegalArgumentException("服务名不能为空");
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.application.name", applicationName);
    // 后导入的覆盖先导入的：本服务的配置覆盖公共配置
    properties.put(
        "spring.config.import", "nacos:" + COMMON_DATA_ID + ",nacos:" + applicationName + ".yaml");
    properties.put("spring.cloud.nacos.config.namespace", NAMESPACE);
    // Nacos 的值覆盖 jar 里的默认值（SCA 默认是本地优先）
    properties.put("spring.cloud.nacos.config.preference", "remote");
    properties.put(ConfigSourceCheckPostProcessor.REQUIRED_PROPERTY, "true");
    return properties;
  }
}
