package dev.linqibin.patra.starter.nacos;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.PlaceholderResolutionException;
import org.springframework.util.StringUtils;

/// 签到检查：确认两份 Nacos 配置都到了，没到就让启动失败。
///
/// 每份 Nacos 配置里写一行签到，值为文件名：`patra-common.yaml` 写
/// `patra.config-source.common`，`{服务名}.yaml` 写 `patra.config-source.service`。jar 里永远
/// 不写，所以签到在，就说明对应的文件确实从 Nacos（或本地快照）读到了。SCA 拿不到配置时只打
/// warn、返回空配置，靠这一步兜底。
///
/// 签到之后再查 Nacos 配置里引用的变量：没有默认值、又解析不了的 `${变量}` 让启动失败。Spring 绑定
/// 配置时不严格解析占位符，不查的话会拿字面量 `${REDIS_PASSWORD}` 当密码，服务照常启动、用到时才报
/// 认证失败。所以 `patra-common.yaml` 里不写没有默认值的引用：没用到的服务也会被它挡住。
///
/// - 只在 `patra.config-source.required=true`（启动器设置）时检查；测试不经过启动器，不检查。
/// - 排在 Spring Boot 加载完配置之后，比任何 Bean 创建都早：Nacos 没到时，不会先冒出数据源、
///   Redis 之类不相关的报错。
/// - 配置刷新时 spring-cloud-context 会重跑所有 `EnvironmentPostProcessor`：少了签到行的那次
///   变更会被拒绝，应用继续用旧配置。
public class ConfigSourceCheckPostProcessor implements EnvironmentPostProcessor, Ordered {

  /// 签到检查开关，由启动器设为 `true`。
  public static final String REQUIRED_PROPERTY = "patra.config-source.required";

  /// 排在 Spring Boot 加载配置（含 Nacos 导入）之后。
  public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

  /// `patra-common.yaml` 的签到字段。
  static final String COMMON_PROPERTY = "patra.config-source.common";

  /// `{服务名}.yaml` 的签到字段。
  static final String SERVICE_PROPERTY = "patra.config-source.service";

  private static final String CAUSE = "Nacos 不可达且本地没有快照，或该文件不存在";

  /// SCA 的 `NacosConfigDataLoader` 给导入的 Nacos 配置起的名字：`分组@dataId`。分组和 dataId
  /// 只能用字母、数字和 `-_.:`，所以 jar 里配置文件的属性源（名字带空格和引号）不会被认错。
  /// SCA 自己的 `NacosPropertySource` 只登记在 `NacosPropertySourceRepository`，不进环境。
  private static final Pattern NACOS_SOURCE_NAME = Pattern.compile("[\\w.:-]+@([\\w.:-]+)");

  /// 开启了签到检查时，确认两个签到字段都有值；缺了就抛异常，启动中止。
  ///
  /// @param environment 已加载完配置的环境
  /// @param application 正在启动的应用
  /// @throws IllegalStateException 缺签到时，每缺一个写一行原因
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!environment.getProperty(REQUIRED_PROPERTY, Boolean.class, false)) {
      return;
    }
    List<String> missing = new ArrayList<>();
    if (!StringUtils.hasText(environment.getProperty(COMMON_PROPERTY))) {
      missing.add("没拿到 Nacos 配置 patra-common.yaml：" + CAUSE);
    }
    if (!StringUtils.hasText(environment.getProperty(SERVICE_PROPERTY))) {
      String dataId = environment.getProperty("spring.application.name") + ".yaml";
      missing.add("没拿到本服务的 Nacos 配置 " + dataId + "：" + CAUSE);
    }
    missing.addAll(unresolvedReferences(environment));
    if (!missing.isEmpty()) {
      throw new IllegalStateException(String.join("\n", missing));
    }
  }

  /// 找出 Nacos 配置里解析不了的变量引用，每个属性报一行（只报它第一个解析不了的变量）。
  ///
  /// @param environment 已加载完配置的环境
  /// @return 报错行，没有问题时为空
  private static List<String> unresolvedReferences(ConfigurableEnvironment environment) {
    List<String> problems = new ArrayList<>();
    for (PropertySource<?> source : environment.getPropertySources()) {
      Matcher nacosName = NACOS_SOURCE_NAME.matcher(source.getName());
      if (!nacosName.matches() || !(source instanceof EnumerablePropertySource<?> nacosSource)) {
        continue;
      }
      for (String name : nacosSource.getPropertyNames()) {
        Object value = nacosSource.getProperty(name);
        if (value == null) {
          continue;
        }
        try {
          environment.resolveRequiredPlaceholders(value.toString());
        } catch (PlaceholderResolutionException e) {
          problems.add(
              "Nacos 配置 "
                  + nacosName.group(1)
                  + " 的 "
                  + name
                  + " 引用的变量 "
                  + e.getPlaceholder()
                  + " 没有值：本机放在 ~/.patra/secrets/ 下的 .env 文件里，容器经 env_file 注入");
        }
      }
    }
    return problems;
  }

  /// 排序值，见 [#ORDER]。
  ///
  /// @return 排序值
  @Override
  public int getOrder() {
    return ORDER;
  }
}
