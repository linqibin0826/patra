package com.patra.starter.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * BatchAutoConfiguration 的测试
 *
 * <p>验证 Spring Batch 核心组件的自动配置功能
 *
 * @author Patra Lin
 * @since 0.1.0
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.batch.jdbc.initialize-schema=always",
    "spring.batch.job.enabled=false",
    "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2",
    "patra.batch.enabled=true",
    "patra.redisson.observability.metrics-enabled=false"
})
class BatchAutoConfigurationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  @DisplayName("应该自动配置 JobRepository Bean")
  void shouldAutoConfigureJobRepository() {
    // When
    JobRepository jobRepository = applicationContext.getBean(JobRepository.class);

    // Then
    assertThat(jobRepository).isNotNull();
  }

  @Test
  @DisplayName("应该自动配置 JobLauncher Bean")
  void shouldAutoConfigureJobLauncher() {
    // When
    JobLauncher jobLauncher = applicationContext.getBean(JobLauncher.class);

    // Then
    assertThat(jobLauncher).isNotNull();
    assertThat(jobLauncher).isInstanceOf(TaskExecutorJobLauncher.class);
  }

  @Test
  @DisplayName("JobLauncher 应该使用 SyncTaskExecutor（关键改进点）")
  void jobLauncher_ShouldUseSyncTaskExecutor() throws Exception {
    // Given
    JobLauncher jobLauncher = applicationContext.getBean(JobLauncher.class);

    // When - 通过反射获取 TaskExecutor
    Field taskExecutorField = TaskExecutorJobLauncher.class.getDeclaredField("taskExecutor");
    taskExecutorField.setAccessible(true);
    TaskExecutor taskExecutor = (TaskExecutor) taskExecutorField.get(jobLauncher);

    // Then - 验证使用的是 SyncTaskExecutor（而非 SimpleAsyncTaskExecutor）
    assertThat(taskExecutor)
        .as("JobLauncher 应使用 SyncTaskExecutor（XXL-Job 已异步）")
        .isInstanceOf(SyncTaskExecutor.class);
  }

  @Test
  @DisplayName("应该自动配置 JobExplorer Bean")
  void shouldAutoConfigureJobExplorer() {
    // When
    JobExplorer jobExplorer = applicationContext.getBean(JobExplorer.class);

    // Then
    assertThat(jobExplorer).isNotNull();
  }

  @Test
  @DisplayName("应该自动配置 JobOperator Bean")
  void shouldAutoConfigureJobOperator() {
    // When
    JobOperator jobOperator = applicationContext.getBean(JobOperator.class);

    // Then
    assertThat(jobOperator).isNotNull();
  }

  @Test
  @DisplayName("应该自动配置 DataSource Bean")
  void shouldAutoConfigureDataSource() {
    // When
    DataSource dataSource = applicationContext.getBean(DataSource.class);

    // Then
    assertThat(dataSource).isNotNull();
  }
}
