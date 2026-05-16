package dev.linqibin.starter.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.batch.core.JobOperatorHelper;
import dev.linqibin.starter.test.container.initializer.MySQLContainerInitializer;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

/// BatchAutoConfiguration 集成测试。
///
/// 验证 Spring Batch 核心组件的自动配置功能。
/// 使用 MySQL TestContainer 确保与生产环境一致。
///
/// @author Patra Lin
/// @since 0.1.0
@SpringBootTest(
    properties = {
      "spring.batch.jdbc.initialize-schema=always",
      "spring.batch.job.enabled=false",
      "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4",
      "patra.batch.enabled=true",
      "patra.redisson.observability.metrics-enabled=false"
    })
@ContextConfiguration(initializers = MySQLContainerInitializer.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class BatchAutoConfigurationIT {

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

  @Test
  @DisplayName("应该自动配置 JobOperatorHelper Bean")
  void shouldAutoConfigureJobOperatorHelper() {
    // When
    JobOperatorHelper helper = applicationContext.getBean(JobOperatorHelper.class);

    // Then
    assertThat(helper).isNotNull();
  }

  @Test
  @DisplayName("自定义 batchObservabilityBeanPostProcessor 应根据 ObservationRegistry 条件化注册")
  void customBatchObservabilityBeanPostProcessor_ShouldBeConditionalOnObservationRegistry() {
    // Given - 检查 ObservationRegistry 是否存在
    String[] observationRegistryBeans =
        applicationContext.getBeanNamesForType(ObservationRegistry.class);
    boolean hasObservationRegistry = observationRegistryBeans.length > 0;

    // When - 检查自定义 Bean（方法名）是否注册
    boolean hasCustomBean = applicationContext.containsBean("batchObservabilityBeanPostProcessor");

    // Then - 仅验证我们自定义 Bean 的条件化行为
    // 注意：Spring Batch 6+ 可能会提供框架默认的 BatchObservabilityBeanPostProcessor，
    // 该 Bean 不受本自定义方法条件控制。
    if (hasObservationRegistry) {
      assertThat(hasCustomBean)
          .as("当 ObservationRegistry 存在时，自定义 batchObservabilityBeanPostProcessor 应被注册")
          .isTrue();
    } else {
      assertThat(hasCustomBean)
          .as("当 ObservationRegistry 不存在时，自定义 batchObservabilityBeanPostProcessor 不应被注册")
          .isFalse();
    }
  }
}
