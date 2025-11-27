package com.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.infra.config.IngestMySQLContainerInitializer;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import com.patra.ingest.infra.persistence.mapper.CursorMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// CursorRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + MySQL 8 测试游标持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 MySQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：save（insert/update）、find、findLatestGlobalTimeWatermark
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = IngestMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  CursorRepositoryAdapter.class,
  TestMybatisPlusAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
@ComponentScan("com.patra.ingest.infra.persistence.converter")
@MapperScan("com.patra.ingest.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("CursorRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CursorRepositoryAdapterIT {

  @Autowired private CursorRepositoryAdapter repository;

  @Autowired private CursorMapper cursorMapper;

  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_CODE = "HARVEST";
  private static final String TEST_CURSOR_KEY = "updated_at";
  private static final String TEST_NAMESPACE_SCOPE_CODE = "GLOBAL";
  private static final String TEST_NAMESPACE_KEY =
      "0000000000000000000000000000000000000000000000000000000000000000";
  private static final Instant TEST_WATERMARK = Instant.parse("2025-01-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    // 清理现有数据（使用 ne(id, 0) 条件绕过全表操作禁止）
    cursorMapper.delete(Wrappers.<CursorDO>lambdaQuery().ne(CursorDO::getId, 0L));
  }

  @Nested
  @DisplayName("保存操作")
  class SaveTests {

    @Test
    @DisplayName("应在通过 Mapper 插入后通过 find 查询到游标")
    void shouldFindCursorAfterMapperInsert() {
      // Given: 通过 Mapper 直接插入测试数据
      CursorDO cursorDO = createTestCursorDO();
      cursorMapper.insert(cursorDO);

      // When
      Optional<Cursor> result =
          repository.find(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE),
              TEST_OPERATION_CODE,
              TEST_CURSOR_KEY,
              TEST_NAMESPACE_SCOPE_CODE,
              TEST_NAMESPACE_KEY);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getProvenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(result.get().getCursorKey()).isEqualTo(TEST_CURSOR_KEY);
    }

    @Test
    @DisplayName("应正确映射游标类型")
    void shouldMapCursorTypeCorrectly() {
      // Given
      CursorDO cursorDO = createTestCursorDO();
      cursorDO.setCursorTypeCode("TIME");
      cursorMapper.insert(cursorDO);

      // When
      Optional<Cursor> result =
          repository.find(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE),
              TEST_OPERATION_CODE,
              TEST_CURSOR_KEY,
              TEST_NAMESPACE_SCOPE_CODE,
              TEST_NAMESPACE_KEY);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getCursorType()).isNotNull();
    }
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按复合键查找游标")
    void shouldFindByCompositeKey() {
      // Given
      CursorDO cursorDO = createTestCursorDO();
      cursorMapper.insert(cursorDO);

      // When
      Optional<Cursor> result =
          repository.find(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE),
              TEST_OPERATION_CODE,
              TEST_CURSOR_KEY,
              TEST_NAMESPACE_SCOPE_CODE,
              TEST_NAMESPACE_KEY);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getProvenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(result.get().getCursorKey()).isEqualTo(TEST_CURSOR_KEY);
    }

    @Test
    @DisplayName("应在游标不存在时返回空 Optional")
    void shouldReturnEmptyWhenCursorNotFound() {
      // Given: 不插入任何游标

      // When
      Optional<Cursor> result =
          repository.find(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE),
              TEST_OPERATION_CODE,
              TEST_CURSOR_KEY,
              TEST_NAMESPACE_SCOPE_CODE,
              TEST_NAMESPACE_KEY);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("查询最新全局时间水位")
  class FindLatestGlobalTimeWatermarkTests {

    @Test
    @DisplayName("应查询最新全局时间水位")
    void shouldFindLatestGlobalTimeWatermark() {
      // Given
      CursorDO cursorDO = createTestCursorDO();
      cursorDO.setNormalizedInstant(TEST_WATERMARK);
      cursorMapper.insert(cursorDO);

      // When
      Optional<Instant> result =
          repository.findLatestGlobalTimeWatermark(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE), TEST_OPERATION_CODE);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(TEST_WATERMARK);
    }

    @Test
    @DisplayName("应在没有游标时返回空 Optional")
    void shouldReturnEmptyWhenNoCursorExists() {
      // Given: 不插入任何游标

      // When
      Optional<Instant> result =
          repository.findLatestGlobalTimeWatermark(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE), TEST_OPERATION_CODE);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应支持 operationCode 为 null")
    void shouldSupportNullOperationCode() {
      // Given
      CursorDO cursorDO = createTestCursorDO();
      cursorDO.setNormalizedInstant(TEST_WATERMARK);
      cursorMapper.insert(cursorDO);

      // When
      Optional<Instant> result =
          repository.findLatestGlobalTimeWatermark(
              ProvenanceCode.parse(TEST_PROVENANCE_CODE), null);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(TEST_WATERMARK);
    }
  }

  // ==================== 辅助方法 ====================

  private CursorDO createTestCursorDO() {
    CursorDO cursor = new CursorDO();
    cursor.setProvenanceCode(TEST_PROVENANCE_CODE);
    cursor.setOperationCode(TEST_OPERATION_CODE);
    cursor.setCursorKey(TEST_CURSOR_KEY);
    cursor.setNamespaceScopeCode(TEST_NAMESPACE_SCOPE_CODE);
    cursor.setNamespaceKey(TEST_NAMESPACE_KEY);
    cursor.setCursorTypeCode("TIME");
    cursor.setCursorValue(TEST_WATERMARK.toString());
    cursor.setNormalizedInstant(TEST_WATERMARK);
    return cursor;
  }
}
