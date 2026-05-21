package dev.linqibin.starter.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/// 双数据源集成测试。
///
/// 验证配置了 `linqibin.starter.batch.datasource.*` 后，Spring Batch 使用独立数据源。
///
/// 注意：此测试只验证 batchDataSource 的创建和配置。
/// 主数据源由 Spring Boot DataSourceAutoConfiguration 管理，不在此测试范围内。
@SpringBootTest(
    properties = {
      // Batch 独立数据源（元数据）：使用 Testcontainers PostgreSQL
      "linqibin.starter.batch.datasource.url=jdbc:tc:postgresql:17:///batch_meta_db",
      "linqibin.starter.batch.datasource.username=test",
      "linqibin.starter.batch.datasource.password=test",
      "linqibin.starter.batch.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
      "linqibin.starter.batch.datasource.hikari.maximum-pool-size=3",
      "linqibin.starter.batch.datasource.hikari.minimum-idle=1",
      // Spring Batch 配置
      "spring.batch.jdbc.initialize-schema=always",
      "spring.batch.job.enabled=false",
      // Patra 配置
      "linqibin.starter.batch.enabled=true",
      "linqibin.starter.batch.table-prefix=BATCH_",
      // 禁用 Redisson（测试环境）
      "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4",
      "linqibin.starter.redisson.observability.metrics-enabled=false",
      // 覆盖主数据源为简单的 Testcontainers PostgreSQL URL（不依赖类路径资源）
      "spring.datasource.url=jdbc:tc:postgresql:17:///test_batch",
      "spring.datasource.username=test",
      "spring.datasource.password=test",
      "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
      // 禁用 Flyway（batch starter 无迁移脚本，不需要数据库模式初始化）
      "spring.flyway.enabled=false"
    })
@ActiveProfiles("test")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DualDataSourceIT {

  @Autowired private ApplicationContext applicationContext;

  @Autowired
  @Qualifier("batchDataSource")
  private DataSource batchDataSource;

  @Autowired private JobRepository jobRepository;

  @Autowired private JobExplorer jobExplorer;

  @Test
  @DisplayName("配置独立数据源时：batchDataSource Bean 应被创建")
  void batchDataSource_ShouldBeCreated() {
    // Then: batchDataSource Bean 应存在
    assertThat(applicationContext.containsBean("batchDataSource")).isTrue();
    assertThat(batchDataSource).isNotNull();
  }

  @Test
  @DisplayName("配置独立数据源时：batchDataSource 应使用 HikariDataSource")
  void batchDataSource_ShouldBeHikariDataSource() {
    // Then: batchDataSource 应该是 HikariDataSource 类型
    assertThat(batchDataSource).isInstanceOf(HikariDataSource.class);
  }

  @Test
  @DisplayName("配置独立数据源时：batchDataSource 应连接到正确的数据库")
  void batchDataSource_ShouldConnectToCorrectDatabase() {
    // When
    HikariDataSource hikariDS = (HikariDataSource) batchDataSource;

    // Then: 验证连接到 batch_meta_db
    assertThat(hikariDS.getJdbcUrl()).contains("batch_meta_db");
    assertThat(hikariDS.getPoolName()).isEqualTo("batch-hikari-pool");
  }

  @Test
  @DisplayName("配置独立数据源时：batchDataSource 应使用自定义 Hikari 配置")
  void batchDataSource_ShouldUseCustomHikariConfig() {
    // When
    HikariDataSource hikariDS = (HikariDataSource) batchDataSource;

    // Then: 验证自定义配置生效
    assertThat(hikariDS.getMaximumPoolSize()).isEqualTo(3);
    assertThat(hikariDS.getMinimumIdle()).isEqualTo(1);
  }

  @Test
  @DisplayName("配置独立数据源时：batchTransactionManager Bean 应被创建")
  void batchTransactionManager_ShouldBeCreated() {
    // Then: batchTransactionManager Bean 应存在
    assertThat(applicationContext.containsBean("batchTransactionManager")).isTrue();
  }

  @Test
  @DisplayName("配置独立数据源时：JobRepository 应正常创建")
  void jobRepository_ShouldBeCreated() {
    // Then
    assertThat(jobRepository).isNotNull();
  }

  @Test
  @DisplayName("配置独立数据源时：JobExplorer 应正常创建")
  void jobExplorer_ShouldBeCreated() {
    // Then
    assertThat(jobExplorer).isNotNull();
  }
}
