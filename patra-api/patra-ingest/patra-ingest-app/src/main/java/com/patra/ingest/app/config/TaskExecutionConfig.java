package com.patra.ingest.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 任务执行配置（Task Execution Config）。
 * <p>
 * 职责：提供任务执行相关的基础设施 Bean，包括：
 * <ul>
 *   <li>心跳调度器（ScheduledExecutorService）：用于定时续租</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Configuration
public class TaskExecutionConfig {

    /**
     * 心跳调度器（用于租约续租）。
     * <p>
     * 使用单线程调度器，避免并发续租导致的资源浪费。
     * 每个任务启动时注册一个定时任务，任务完成后需手动取消。
     * </p>
     *
     * @return ScheduledExecutorService
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService heartbeatScheduler() {
        return Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread thread = new Thread(r, "heartbeat-scheduler");
                    thread.setDaemon(true);
                    return thread;
                });
    }
}
