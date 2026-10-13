package dev.linqibin.patra.starter.nacos;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/// 签到检查：确认两份 Nacos 配置都到了，没到就让启动失败。
///
/// 每份 Nacos 配置里写一行签到，值为文件名：`patra-common.yaml` 写
/// `patra.config-source.common`，`{服务名}.yaml` 写 `patra.config-source.service`。jar 里永远
/// 不写，所以签到在，就说明对应的文件确实从 Nacos（或本地快照）读到了。SCA 拿不到配置时只打
/// warn、返回空配置，靠这一步兜底。
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
    if (!missing.isEmpty()) {
      throw new IllegalStateException(String.join("\n", missing));
    }
  }

  /// 排序值，见 [#ORDER]。
  ///
  /// @return 排序值
  @Override
  public int getOrder() {
    return ORDER;
  }
}
