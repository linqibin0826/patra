package com.patra.ingest.adapter.scheduler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 执行器配置。
 *
 * <p>为 Patra-Ingest 服务配置 XXL-Job 执行器,包括: - 执行器注册和心跳 - 日志路径和访问令牌 - 任务处理器自动扫描
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "xxl.job",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class XxlJobConfig {

  /** 管理端服务器地址 */
  @Value("${xxl.job.admin.addresses}")
  private String adminAddresses;

  /** 执行器绑定 IP */
  @Value("${xxl.job.executor.ip:}")
  private String ip;

  /** 访问令牌 */
  @Value("${xxl.job.accessToken:}")
  private String accessToken;

  /** 执行器日志路径 */
  @Value("${xxl.job.executor.logpath:logs/xxl-job}")
  private String logPath;

  /** 日志保留天数 */
  @Value("${xxl.job.executor.logretentiondays:30}")
  private int logRetentionDays;

  /** 应用服务端口 */
  @Value("${server.port}")
  private int serverPort;

  /** 应用名称 */
  @Value("${spring.application.name}")
  private String appName;

  /** 配置 XXL-Job 执行器 Bean。 */
  @Bean
  public XxlJobSpringExecutor xxlJobExecutor() {
    log.info("正在初始化 XXL-Job 执行器配置...");

    XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
    xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
    xxlJobSpringExecutor.setAppname(appName);
    xxlJobSpringExecutor.setIp(ip);
    // 约定: 执行器端口 = 服务端口 + 1,避免与主服务冲突
    int executorPort = serverPort + 1;
    xxlJobSpringExecutor.setPort(executorPort);
    xxlJobSpringExecutor.setAccessToken(accessToken);
    xxlJobSpringExecutor.setLogPath(logPath);
    xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

    log.info(
        "XXL-Job 执行器初始化完成, appName={}, adminAddresses={}, port={}",
        appName,
        adminAddresses,
        executorPort);

    return xxlJobSpringExecutor;
  }
}
