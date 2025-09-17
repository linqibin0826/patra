package com.patra.ingest.adapter.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the XXL-JOB framework.
 * <p>
 * This class is responsible for setting up and configuring the {@link XxlJobSpringExecutor},
 * which allows the application to act as a client (executor) for the XXL-JOB distributed
 * task scheduling platform.
 * </p>
 */
@Configuration
public class XxlJobConfig {

    @Value("${server.port}")
    private int serverPort;

    /**
     * Creates and configures the XxlJobSpringExecutor bean.
     * <p>
     * This executor connects to the XXL-JOB admin center, registers itself,
     * and listens for job execution triggers. The configuration properties are
     * injected from the application's configuration files.
     * </p>
     *
     * @param adminAddresses   The comma-separated list of addresses for the XXL-JOB admin center.
     * @param accessToken      The access token for authentication with the admin center.
     * @param appname          The application name used for registration in the admin center.
     * @param ip          The specific ip for the executor to bind to. If empty, it will be auto-detected.
     * @param logPath          The directory path for storing job execution logs.
     * @param logRetentionDays The number of days to retain job execution logs.
     * @return A configured {@link XxlJobSpringExecutor} instance.
     */
    @Bean
    public XxlJobSpringExecutor patraIngestXxlJobExecutor(
            @Value("${xxl.job.admin.addresses}") String adminAddresses,
            @Value("${xxl.job.accessToken:}") String accessToken,
            @Value("${xxl.job.executor.appname}") String appname,
            @Value("${xxl.job.executor.ip:}") String ip,
            @Value("${xxl.job.executor.logpath}") String logPath,
            @Value("${xxl.job.executor.logretentiondays}") int logRetentionDays) {

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setIp(ip);
        // The XXL-JOB executor runs an embedded server for communication.
        // We set its port to be the application's server port + 1 to avoid conflicts.
        executor.setPort(serverPort + 1);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
