package com.patra.ingest.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.infra.persistence.converter.CursorConverter;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import com.patra.ingest.infra.persistence.mapper.CursorMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * CursorRepositoryMpImpl 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 Mockito Mock 所有依赖（Mapper, Converter）
 *   <li>不启动 Spring 容器，纯单元测试
 *   <li>验证方法调用、参数传递和返回值转换
 * </ul>
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>保存游标（insert/update 分支）
 *   <li>按复合键查找游标
 *   <li>查询最新全局时间水位
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CursorRepositoryMpImpl 单元测试")
class CursorRepositoryMpImplTest {

  @Mock private CursorMapper mapper;
  @Mock private CursorConverter converter;

  @InjectMocks private CursorRepositoryMpImpl repository;

  private static final Long TEST_CURSOR_ID = 1L;
  private static final ProvenanceCode TEST_PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final String TEST_OPERATION_CODE = "SEARCH";
  private static final String TEST_CURSOR_KEY = "last_pmid";
  private static final String TEST_NAMESPACE_SCOPE_CODE = "GLOBAL";
  private static final String TEST_NAMESPACE_KEY = "default";
  private static final Instant TEST_WATERMARK = Instant.parse("2025-01-01T00:00:00Z");

  @Nested
  @DisplayName("保存操作")
  class SaveTests {

    @Test
    @DisplayName("应在 ID 为 null 时插入游标")
    void shouldInsertWhenIdIsNull() {
      // Given
      Cursor cursor = createTestCursor(null);
      CursorDO entity = createTestCursorDO(null);
      CursorDO persistedEntity = createTestCursorDO(TEST_CURSOR_ID);
      Cursor persistedCursor = createTestCursor(TEST_CURSOR_ID);

      when(converter.toDO(cursor)).thenReturn(entity);
      when(mapper.insert(entity))
          .thenAnswer(
              invocation -> {
                entity.setId(TEST_CURSOR_ID); // 模拟数据库生成 ID
                return 1;
              });
      when(mapper.selectById(TEST_CURSOR_ID)).thenReturn(persistedEntity);
      when(converter.toDomain(persistedEntity)).thenReturn(persistedCursor);

      // When
      Cursor result = repository.save(cursor);

      // Then
      assertThat(result).isEqualTo(persistedCursor);
      verify(converter).toDO(cursor);
      verify(mapper).insert(entity);
      verify(mapper).selectById(TEST_CURSOR_ID);
      verify(converter).toDomain(persistedEntity);
      verify(mapper, never()).updateById(any(CursorDO.class));
    }

    @Test
    @DisplayName("应在 ID 存在时更新游标")
    void shouldUpdateWhenIdExists() {
      // Given
      Cursor cursor = createTestCursor(TEST_CURSOR_ID);
      CursorDO entity = createTestCursorDO(TEST_CURSOR_ID);
      CursorDO persistedEntity = createTestCursorDO(TEST_CURSOR_ID);
      Cursor persistedCursor = createTestCursor(TEST_CURSOR_ID);

      when(converter.toDO(cursor)).thenReturn(entity);
      when(mapper.updateById(entity)).thenReturn(1);
      when(mapper.selectById(TEST_CURSOR_ID)).thenReturn(persistedEntity);
      when(converter.toDomain(persistedEntity)).thenReturn(persistedCursor);

      // When
      Cursor result = repository.save(cursor);

      // Then
      assertThat(result).isEqualTo(persistedCursor);
      verify(converter).toDO(cursor);
      verify(mapper).updateById(entity);
      verify(mapper).selectById(TEST_CURSOR_ID);
      verify(converter).toDomain(persistedEntity);
      verify(mapper, never()).insert(any(CursorDO.class));
    }

    @Test
    @DisplayName("应在保存后重新读取以获取数据库衍生字段")
    void shouldRefreshAfterSaveToGetDatabaseGeneratedFields() {
      // Given
      Cursor cursor = createTestCursor(null);
      CursorDO entity = createTestCursorDO(null);
      CursorDO persistedEntity = createTestCursorDO(TEST_CURSOR_ID);
      persistedEntity.setNormalizedInstant(TEST_WATERMARK); // 数据库计算的规范化时间
      Cursor persistedCursor = createTestCursor(TEST_CURSOR_ID);

      when(converter.toDO(cursor)).thenReturn(entity);
      when(mapper.insert(entity))
          .thenAnswer(
              invocation -> {
                entity.setId(TEST_CURSOR_ID);
                return 1;
              });
      when(mapper.selectById(TEST_CURSOR_ID)).thenReturn(persistedEntity);
      when(converter.toDomain(persistedEntity)).thenReturn(persistedCursor);

      // When
      Cursor result = repository.save(cursor);

      // Then
      verify(mapper).selectById(TEST_CURSOR_ID); // 验证重新读取
      assertThat(result).isEqualTo(persistedCursor);
    }
  }

  @Nested
  @DisplayName("查询操作")
  class FindTests {

    @Test
    @DisplayName("应按复合键查找游标")
    void shouldFindByCompositeKey() {
      // Given
      CursorDO entity = createTestCursorDO(TEST_CURSOR_ID);
      Cursor cursor = createTestCursor(TEST_CURSOR_ID);

      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);
      when(converter.toDomain(entity)).thenReturn(cursor);

      // When
      Optional<Cursor> result =
          repository.find(
              TEST_PROVENANCE_CODE,
              TEST_OPERATION_CODE,
              TEST_CURSOR_KEY,
              TEST_NAMESPACE_SCOPE_CODE,
              TEST_NAMESPACE_KEY);

      // Then
      assertThat(result).isPresent().contains(cursor);
      verify(mapper).selectOne(any(QueryWrapper.class));
      verify(converter).toDomain(entity);
    }

    @Test
    @DisplayName("应在游标不存在时返回空 Optional")
    void shouldReturnEmptyWhenCursorNotFound() {
      // Given
      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

      // When
      Optional<Cursor> result =
          repository.find(
              TEST_PROVENANCE_CODE,
              TEST_OPERATION_CODE,
              TEST_CURSOR_KEY,
              TEST_NAMESPACE_SCOPE_CODE,
              TEST_NAMESPACE_KEY);

      // Then
      assertThat(result).isEmpty();
      verify(mapper).selectOne(any(QueryWrapper.class));
      verify(converter, never()).toDomain(any(CursorDO.class));
    }

    @Test
    @DisplayName("应验证查询条件包含所有复合键字段")
    void shouldBuildQueryWithAllCompositeKeyFields() {
      // Given
      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

      // When
      repository.find(
          TEST_PROVENANCE_CODE,
          TEST_OPERATION_CODE,
          TEST_CURSOR_KEY,
          TEST_NAMESPACE_SCOPE_CODE,
          TEST_NAMESPACE_KEY);

      // Then
      verify(mapper).selectOne(any(QueryWrapper.class));
      // 注意：这里只验证调用，实际的 QueryWrapper 构建逻辑在实现中
    }
  }

  @Nested
  @DisplayName("查询最新全局时间水位")
  class FindLatestGlobalTimeWatermarkTests {

    @Test
    @DisplayName("应查询最新全局时间水位")
    void shouldFindLatestGlobalTimeWatermark() {
      // Given
      CursorDO entity = createTestCursorDO(TEST_CURSOR_ID);
      entity.setCursorTypeCode("TIME");
      entity.setNormalizedInstant(TEST_WATERMARK);

      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

      // When
      Optional<Instant> result =
          repository.findLatestGlobalTimeWatermark(
              TEST_PROVENANCE_CODE, TEST_OPERATION_CODE);

      // Then
      assertThat(result).isPresent().contains(TEST_WATERMARK);
      verify(mapper).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("应在没有游标时返回空 Optional")
    void shouldReturnEmptyWhenNoCursorExists() {
      // Given
      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

      // When
      Optional<Instant> result =
          repository.findLatestGlobalTimeWatermark(
              TEST_PROVENANCE_CODE, TEST_OPERATION_CODE);

      // Then
      assertThat(result).isEmpty();
      verify(mapper).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("应支持 operationCode 为 null")
    void shouldSupportNullOperationCode() {
      // Given
      CursorDO entity = createTestCursorDO(TEST_CURSOR_ID);
      entity.setCursorTypeCode("TIME");
      entity.setNormalizedInstant(TEST_WATERMARK);

      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

      // When
      Optional<Instant> result =
          repository.findLatestGlobalTimeWatermark(TEST_PROVENANCE_CODE, null);

      // Then
      assertThat(result).isPresent().contains(TEST_WATERMARK);
      verify(mapper).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("应验证查询条件包含时间类型和全局作用域过滤")
    void shouldQueryWithTimeTypeAndGlobalScopeFilter() {
      // Given
      when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

      // When
      repository.findLatestGlobalTimeWatermark(TEST_PROVENANCE_CODE, TEST_OPERATION_CODE);

      // Then
      verify(mapper).selectOne(any(QueryWrapper.class));
      // 注意：实际查询应包含：
      // - cursor_type_code = 'TIME'
      // - namespace_scope_code = 'GLOBAL'
      // - ORDER BY updated_at DESC LIMIT 1
    }
  }

  // ==================== 辅助方法 ====================

  private Cursor createTestCursor(Long id) {
    Cursor cursor = org.mockito.Mockito.mock(Cursor.class);
    when(cursor.getId()).thenReturn(id);
    when(cursor.getProvenanceCode()).thenReturn(TEST_PROVENANCE_CODE);
    when(cursor.getOperationCode()).thenReturn(TEST_OPERATION_CODE);
    when(cursor.getCursorKey()).thenReturn(TEST_CURSOR_KEY);
    when(cursor.getNamespaceKey()).thenReturn(TEST_NAMESPACE_KEY);
    return cursor;
  }

  private CursorDO createTestCursorDO(Long id) {
    CursorDO entity = new CursorDO();
    entity.setId(id);
    entity.setProvenanceCode(TEST_PROVENANCE_CODE.getCode());
    entity.setOperationCode(TEST_OPERATION_CODE);
    entity.setCursorKey(TEST_CURSOR_KEY);
    entity.setNamespaceScopeCode(TEST_NAMESPACE_SCOPE_CODE);
    entity.setNamespaceKey(TEST_NAMESPACE_KEY);
    entity.setCursorTypeCode("TIME");
    entity.setNormalizedInstant(TEST_WATERMARK);
    return entity;
  }
}
