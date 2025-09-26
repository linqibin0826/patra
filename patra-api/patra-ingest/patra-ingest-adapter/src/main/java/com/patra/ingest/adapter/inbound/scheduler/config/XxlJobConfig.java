package com.patra.ingest.adapter.inbound.scheduler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 执行器配置。
 *
 * <p>为 Patra-Ingest 服务配置 XXL-Job 执行器，包含：
 * - 执行器注册和心跳配置
 * - 日志路径和访问令牌配置
 * - 任务处理器自动扫描配置</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Configuration
public class XxlJobConfig {
    
    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;
    
    @Value("${xxl.job.executor.ip:}")
    private String ip;
    
    @Value("${xxl.job.accessToken:}")
    private String accessToken;
    
    @Value("${xxl.job.executor.logpath:logs/xxl-job}")
    private String logPath;
    
    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Value("${server.port}")
    private int serverPort;

    @Value("${spring.application.name}")
    private String appName;
    
    /**
     * 配置 XXL-Job 执行器。
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info("初始化 XXL-Job 执行器配置...");
        
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appName);
        xxlJobSpringExecutor.setIp(ip);
        int executorPort = serverPort + 1;
        xxlJobSpringExecutor.setPort(executorPort);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        
        log.info("XXL-Job 执行器配置完成: appName={}, adminAddresses={}, port={}", 
                appName, adminAddresses, executorPort);
        
        return xxlJobSpringExecutor;
    }
}
