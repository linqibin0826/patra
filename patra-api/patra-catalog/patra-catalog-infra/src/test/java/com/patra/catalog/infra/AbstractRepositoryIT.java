package com.patra.catalog.infra;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository 集成测试基类
 *
 * <p>提供统一的 Testcontainers MySQL 环境和表结构初始化。
 *
 * <p><b>特性</b>：
 * <ul>
 *   <li>使用 Testcontainers MySQL 8.0.35
 *   <li>使用 Flyway 自动执行迁移脚本初始化表结构
 *   <li>所有测试共享同一个容器实例（性能优化）
 *   <li>与生产环境使用相同的 Flyway 脚本（确保一致性）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@SpringBootTest(classes = AbstractRepositoryIT.TestConfig.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ActiveProfiles("test")
public abstract class AbstractRepositoryIT {

  @Configuration
  @SpringBootApplication(
      scanBasePackages = "com.patra.catalog.infra.test",  // 只扫描测试包，避免加载不需要的 Bean
      exclude = {
        org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration.class,
        org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration.class,
        org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration.class
      })
  @MapperScan("com.patra.catalog.infra.persistence.mapper")
  static class TestConfig {

    @org.springframework.context.annotation.Bean
    public com.zaxxer.hikari.HikariDataSource dataSource() {
      // 等待容器启动
      MYSQL_CONTAINER.start();

      com.zaxxer.hikari.HikariDataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
      dataSource.setJdbcUrl(MYSQL_CONTAINER.getJdbcUrl());
      dataSource.setUsername(MYSQL_CONTAINER.getUsername());
      dataSource.setPassword(MYSQL_CONTAINER.getPassword());
      dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
      return dataSource;
    }
  }

  @Container
  protected static final MySQLContainer<?> MYSQL_CONTAINER =
      new MySQLContainer<>("mysql:8.0.35")
          .withDatabaseName("patra_catalog_test")
          .withUsername("test_user")
          .withPassword("test_pass")
          .withCommand(
              "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
}
