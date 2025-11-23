package com.patra.starter.batch.autoconfigure;

import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch 自动配置
 *
 * <p>自动配置 Spring Batch 核心组件：
 *
 * <ul>
 *   <li>JobRepository - 基于数据库的 Job 元数据存储
 *   <li>JobLauncher - Job 启动器（使用 SyncTaskExecutor，XXL-Job 已异步）
 *   <li>JobExplorer - Job 执行历史查询
 *   <li>JobOperator - Job 运维操作接口
 * </ul>
 *
 * <p><strong>关键改进</strong>：JobLauncher 使用 {@link SyncTaskExecutor} 而非 {@code
 * SimpleAsyncTaskExecutor}， 因为 XXL-Job 已提供异步调度能力，无需二次异步。
 *
 * <p>条件激活：{@code patra.batch.enabled=true}（默认启用）
 *
 * @author Patra Lin
 * @since 0.1.0
 */
@Configuration
@ConditionalOnProperty(
    prefix = "patra.batch",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableBatchProcessing
@AutoConfiguration
public class BatchAutoConfiguration extends DefaultBatchConfiguration {

  /**
   * 配置 JobRepository（基于数据库）
   *
   * <p>JobRepository 负责持久化 Spring Batch 元数据（Job 实例、执行记录、步骤信息等）
   *
   * @param dataSource 数据源（Spring Boot 自动配置）
   * @param transactionManager 事务管理器
   * @return JobRepository 实例
   * @throws Exception 工厂 Bean 初始化异常
   */
  @Bean
  public JobRepository jobRepository(
      DataSource dataSource, PlatformTransactionManager transactionManager) throws Exception {
    JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
    factory.setDataSource(dataSource);
    factory.setTransactionManager(transactionManager);
    factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
    factory.setTablePrefix("BATCH_"); // Spring Batch 默认表前缀
    factory.afterPropertiesSet();
    return factory.getObject();
  }

  /**
   * 配置 JobLauncher（同步执行）
   *
   * <p><strong>关键改进</strong>：使用 {@link SyncTaskExecutor} 替代 {@code SimpleAsyncTaskExecutor}
   *
   * <p>原因：
   *
   * <ul>
   *   <li>XXL-Job 已提供异步调度能力（线程池执行）
   *   <li>Spring Batch 二次异步会导致 XXL-Job 无法正确追踪执行状态
   *   <li>同步执行可确保分布式锁在 Job 执行期间有效
   * </ul>
   *
   * @param jobRepository Job 元数据仓库
   * @return JobLauncher 实例
   */
  @Bean
  @Override
  public JobLauncher jobLauncher(JobRepository jobRepository) {
    TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(jobRepository);

    // ⭐ 关键改进：使用 SyncTaskExecutor（XXL-Job 已异步）
    launcher.setTaskExecutor(new SyncTaskExecutor());

    try {
      launcher.afterPropertiesSet();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize JobLauncher", e);
    }

    return launcher;
  }

  /**
   * 配置 JobExplorer（查询 Job 执行历史）
   *
   * <p>JobExplorer 提供只读访问 Job 元数据的能力，用于查询历史执行记录
   *
   * @param dataSource 数据源
   * @param transactionManager 事务管理器
   * @return JobExplorer 实例
   * @throws Exception 工厂 Bean 初始化异常
   */
  @Bean
  public JobExplorer jobExplorer(DataSource dataSource, PlatformTransactionManager transactionManager)
      throws Exception {
    JobExplorerFactoryBean factory = new JobExplorerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setTransactionManager(transactionManager);
    factory.setTablePrefix("BATCH_");
    factory.afterPropertiesSet();
    return factory.getObject();
  }

  /**
   * 配置事务管理器
   *
   * <p>Spring Batch 需要事务支持来管理 Job 元数据
   *
   * @param dataSource 数据源
   * @return 事务管理器
   */
  @Bean
  public PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new JdbcTransactionManager(dataSource);
  }

  /**
   * 配置 JobOperator（运维操作）
   *
   * <p>JobOperator 提供 Job 运维操作能力（启动、停止、重启、查询等）
   *
   * <p><strong>注意</strong>：需要 JobRegistry 支持，由 {@link EnableBatchProcessing} 自动提供
   *
   * @param jobExplorer Job 查询器
   * @param jobLauncher Job 启动器
   * @param jobRepository Job 元数据仓库
   * @return JobOperator 实例（由父类 DefaultBatchConfiguration 提供）
   */
  // JobOperator 由 DefaultBatchConfiguration 自动配置，无需手动创建
}
