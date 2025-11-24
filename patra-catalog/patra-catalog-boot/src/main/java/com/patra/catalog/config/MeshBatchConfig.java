package com.patra.catalog.config;

import org.springframework.context.annotation.Configuration;

/**
 * MeSH 批处理框架配置。
 *
 * <p>**设计目的**：
 *
 * <ul>
 *   <li>使用 Spring Batch 5.2 的标准 JobRepository（基于数据库，支持断点续传）
 *   <li>支持 chunk-oriented processing，每个 chunk 独立事务
 *   <li>完全依赖 Spring Boot 自动配置（零配置）
 * </ul>
 *
 * <p>**自动配置组件**（无需手动创建 Bean）：
 *
 * <ul>
 *   <li>{@code JobRepository} - 基于 DataSource 的标准实现，支持断点续传和状态跟踪
 *   <li>{@code JobLauncher} - 默认的同步任务启动器
 *   <li>{@code PlatformTransactionManager} - 使用应用的 DataSourceTransactionManager
 * </ul>
 *
 * <p>**Spring Batch 元数据表**（6 张表，由 Spring Boot 自动创建或 Flyway 管理）：
 *
 * <ul>
 *   <li>BATCH_JOB_INSTANCE - 作业实例表
 *   <li>BATCH_JOB_EXECUTION - 作业执行表
 *   <li>BATCH_JOB_EXECUTION_PARAMS - 作业参数表
 *   <li>BATCH_STEP_EXECUTION - 步骤执行表
 *   <li>BATCH_STEP_EXECUTION_CONTEXT - 步骤执行上下文表（存储断点信息）
 *   <li>BATCH_JOB_EXECUTION_CONTEXT - 作业执行上下文表
 * </ul>
 *
 * <p>**断点续传机制**：
 *
 * <ul>
 *   <li>每个 chunk 完成后，Spring Batch 自动保存进度到 BATCH_STEP_EXECUTION_CONTEXT
 *   <li>作业失败后重启，自动从上次成功的 chunk 继续
 *   <li>无需手动管理进度（进度完全由 Spring Batch 管理）
 * </ul>
 *
 * <p>**注意事项**：
 *
 * <ul>
 *   <li>在 Spring Batch 5.x + Spring Boot 3.x 中，不使用 {@code @EnableBatchProcessing} 注解
 *   <li>Spring Boot 自动配置已足够，除非需要自定义 JobRepository 或 JobLauncher
 *   <li>元数据表可由 Spring Boot 自动创建（开发环境）或 Flyway 管理（生产环境）
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Configuration
public class MeshBatchConfig {
  // Spring Boot 自动配置以下 Bean（无需手动创建）：
  // - JobRepository（基于 DataSource）
  // - JobLauncher
  // - PlatformTransactionManager（使用应用的 DataSourceTransactionManager）
  //
  // 如需自定义配置，可在此添加 @Bean 方法覆盖默认配置
}
