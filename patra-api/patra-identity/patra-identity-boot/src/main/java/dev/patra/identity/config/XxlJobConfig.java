package dev.patra.identity.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// XXL-Job 执行器配置：identity 的定时任务（过期令牌清理，以后的登录记录清理、注销到期执行）。
///
/// 执行器注册到调度中心（地址在 Nacos 公共配置 `xxl.job.admin.addresses`），按 `@XxlJob` 注解自动发现任务。
/// 执行器端口约定 = 应用端口 + 1，即 6401。测试里 `xxl.job.enabled: false` 关掉。
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "xxl.job",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class XxlJobConfig {

  /// 管理端服务器地址
  @Value("${xxl.job.admin.addresses}")
  private String adminAddresses;

  /// 执行器绑定 IP
  @Value("${xxl.job.executor.ip:}")
  private String ip;

  /// 访问令牌
  @Value("${xxl.job.accessToken:}")
  private String accessToken;

  /// 执行器日志路径
  @Value("${xxl.job.executor.logpath:logs/xxl-job}")
  private String logPath;

  /// 日志保留天数
  @Value("${xxl.job.executor.logretentiondays:30}")
  private int logRetentionDays;

  /// 应用服务端口
  @Value("${server.port}")
  private int serverPort;

  /// 应用名称
  @Value("${spring.application.name}")
  private String appName;

  /// 配置 XXL-Job 执行器 Bean。
  ///
  /// @return 执行器
  @Bean
  public XxlJobSpringExecutor xxlJobExecutor() {
    XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
    executor.setAdminAddresses(adminAddresses);
    executor.setAppname(appName);
    executor.setIp(ip);
    int executorPort = serverPort + 1;
    executor.setPort(executorPort);
    executor.setAccessToken(accessToken);
    executor.setLogPath(logPath);
    executor.setLogRetentionDays(logRetentionDays);
    log.info(
        "XXL-Job 执行器初始化完成，appName={}，adminAddresses={}，port={}",
        appName,
        adminAddresses,
        executorPort);
    return executor;
  }
}
