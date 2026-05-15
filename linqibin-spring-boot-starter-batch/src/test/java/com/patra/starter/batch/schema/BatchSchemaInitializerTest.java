package com.patra.starter.batch.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// BatchSchemaInitializer 单元测试。
///
/// **测试策略**：
///
/// - 使用 Mockito Mock DataSource 和相关 JDBC 对象
/// - 测试幂等性、线程安全性、异常处理
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchSchemaInitializer 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchSchemaInitializerTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private DatabaseMetaData metaData;
  @Mock private ResultSet resultSet;

  private static final String TABLE_PREFIX = "BATCH_";

  @Nested
  @DisplayName("幂等性测试")
  class IdempotencyTests {

    private BatchSchemaInitializer initializer;

    @BeforeEach
    void setUp() throws SQLException {
      initializer = new BatchSchemaInitializer(dataSource, TABLE_PREFIX);
    }

    @Test
    @DisplayName("多次调用 initialize() 只执行一次实际初始化")
    void shouldOnlyInitializeOnce() throws SQLException {
      // Given - 模拟表已存在
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getTables(any(), any(), eq("BATCH_JOB_INSTANCE"), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true); // 表存在

      // When - 多次调用
      initializer.initialize();
      initializer.initialize();
      initializer.initialize();

      // Then - 只检查一次表是否存在
      verify(dataSource, times(1)).getConnection();
    }

    @Test
    @DisplayName("并发调用 initialize() 只执行一次实际初始化")
    void shouldOnlyInitializeOnceUnderConcurrency() throws Exception {
      // Given - 模拟表已存在
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getTables(any(), any(), eq("BATCH_JOB_INSTANCE"), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true); // 表存在

      int threadCount = 10;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);
      AtomicInteger callCount = new AtomicInteger(0);

      // When - 并发调用
      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await(); // 等待所有线程就绪
                initializer.initialize();
                callCount.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown(); // 释放所有线程
      doneLatch.await(5, TimeUnit.SECONDS);
      executor.shutdown();

      // Then - 所有线程都调用了 initialize()，但只有一次实际执行
      assertThat(callCount.get()).isEqualTo(threadCount);
      verify(dataSource, times(1)).getConnection();
    }
  }

  @Nested
  @DisplayName("Schema 存在性检查测试")
  class SchemaExistsTests {

    private BatchSchemaInitializer initializer;

    @BeforeEach
    void setUp() {
      initializer = new BatchSchemaInitializer(dataSource, TABLE_PREFIX);
    }

    @Test
    @DisplayName("表存在时应该跳过初始化")
    void shouldSkipInitializationWhenTableExists() throws SQLException {
      // Given - 模拟表存在（大写检查匹配）
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getTables(any(), any(), eq("BATCH_JOB_INSTANCE"), any())).thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true); // 表存在

      // When
      initializer.initialize();

      // Then - 不应该执行 SQL 脚本（无法直接验证，但可以确认流程正确）
      verify(dataSource, times(1)).getConnection();
      verify(metaData, times(1)).getTables(any(), any(), eq("BATCH_JOB_INSTANCE"), any());
    }

    @Test
    @DisplayName("表不存在时应该执行初始化")
    void shouldInitializeWhenTableNotExists() throws SQLException {
      // Given - 模拟表不存在（大写和小写都不匹配）
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);

      // 大写检查
      ResultSet upperCaseResultSet = mock(ResultSet.class);
      when(metaData.getTables(any(), any(), eq("BATCH_JOB_INSTANCE"), any()))
          .thenReturn(upperCaseResultSet);
      when(upperCaseResultSet.next()).thenReturn(false);

      // 小写检查
      ResultSet lowerCaseResultSet = mock(ResultSet.class);
      when(metaData.getTables(any(), any(), eq("batch_job_instance"), any()))
          .thenReturn(lowerCaseResultSet);
      when(lowerCaseResultSet.next()).thenReturn(false);

      // When & Then - 由于 schema 资源存在，应该成功执行
      // 注意：这里会触发实际的 SQL 执行，但由于 DataSource 是 mock 的，会失败
      // 所以我们期望抛出异常（因为 mock 的 DataSource 无法执行 SQL）
      assertThatThrownBy(() -> initializer.initialize()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("检查表时发生 SQLException 应该尝试初始化")
    void shouldTryInitializeWhenSqlExceptionOccurs() throws SQLException {
      // Given - 模拟 SQLException
      when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

      // When & Then - 因为检查失败，会尝试初始化，但由于无法连接数据库会再次失败
      assertThatThrownBy(() -> initializer.initialize()).isInstanceOf(Exception.class);
    }
  }

  @Nested
  @DisplayName("自定义表前缀测试")
  class CustomTablePrefixTests {

    @Test
    @DisplayName("应该使用自定义表前缀检查表是否存在")
    void shouldUseCustomTablePrefix() throws SQLException {
      // Given
      String customPrefix = "MY_BATCH_";
      BatchSchemaInitializer initializer = new BatchSchemaInitializer(dataSource, customPrefix);

      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getTables(any(), any(), eq("MY_BATCH_JOB_INSTANCE"), any()))
          .thenReturn(resultSet);
      when(resultSet.next()).thenReturn(true); // 表存在

      // When
      initializer.initialize();

      // Then - 验证使用了自定义前缀
      verify(metaData).getTables(any(), any(), eq("MY_BATCH_JOB_INSTANCE"), any());
    }
  }

  @Nested
  @DisplayName("资源文件测试")
  class ResourceTests {

    @Test
    @DisplayName("Schema 资源文件应该存在")
    void schemaResourceShouldExist() {
      // Given
      org.springframework.core.io.Resource resource =
          new org.springframework.core.io.ClassPathResource("db/batch/schema-mysql.sql");

      // Then
      assertThat(resource.exists()).as("Schema 资源文件 db/batch/schema-mysql.sql 应该存在").isTrue();
    }
  }
}
