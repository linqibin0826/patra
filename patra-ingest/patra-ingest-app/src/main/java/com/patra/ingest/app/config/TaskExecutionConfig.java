package com.patra.ingest.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 任务执行引擎配置类。
 * <p>
 * 职责：提供任务执行引擎所需的基础 Bean（Clock、Executor 等）。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>Clock：提供统一的时间源，便于测试时 Mock。</li>
 *   <li>可扩展：后续可添加线程池、限流器等配置。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Configuration
public class TaskExecutionConfig {

    /**
     * 提供系统时钟（UTC）。
     * <p>
     * 使用 Clock.systemUTC() 作为默认时钟源，便于测试时替换为固定时钟。
     * </p>
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
