package com.patra.ingest.adapter.inbound.scheduler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job executor configuration.
 *
 * <p>Configures the XXL-Job executor for the Patra-Ingest service, including: - Executor
 * registration and heartbeat - Log path and access token - Task handler auto-scan
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

  /** Admin server addresses */
  @Value("${xxl.job.admin.addresses}")
  private String adminAddresses;

  /** Executor bind IP */
  @Value("${xxl.job.executor.ip:}")
  private String ip;

  /** Access token */
  @Value("${xxl.job.accessToken:}")
  private String accessToken;

  /** Executor log path */
  @Value("${xxl.job.executor.logpath:logs/xxl-job}")
  private String logPath;

  /** Log retention days */
  @Value("${xxl.job.executor.logretentiondays:30}")
  private int logRetentionDays;

  /** Application server port */
  @Value("${server.port}")
  private int serverPort;

  /** Application name */
  @Value("${spring.application.name}")
  private String appName;

  /** Configures the XXL-Job executor bean. */
  @Bean
  public XxlJobSpringExecutor xxlJobExecutor() {
    log.info("[INGEST][ADAPTER] Initializing XXL-Job executor configuration...");

    XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
    xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
    xxlJobSpringExecutor.setAppname(appName);
    xxlJobSpringExecutor.setIp(ip);
    // Convention: executor port = server port + 1 to avoid conflicts with the main service
    int executorPort = serverPort + 1;
    xxlJobSpringExecutor.setPort(executorPort);
    xxlJobSpringExecutor.setAccessToken(accessToken);
    xxlJobSpringExecutor.setLogPath(logPath);
    xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

    log.info(
        "[INGEST][ADAPTER] XXL-Job executor initialized, appName={}, adminAddresses={}, port={}",
        appName,
        adminAddresses,
        executorPort);

    return xxlJobSpringExecutor;
  }
}
