package com.patra.registry.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.*;
import com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter;
import com.patra.registry.infra.persistence.entity.provenance.*;
import com.patra.registry.infra.persistence.mapper.provenance.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ProvenanceConfigRepositoryMpImpl 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 Mockito Mock 所有依赖 (Mapper, Converter)
 *   <li>不启动 Spring 容器，纯单元测试
 *   <li>验证方法调用、参数传递和返回值转换
 * </ul>
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>数据源查询 (findProvenanceByCode, findAllProvenances)
 *   <li>各种配置查询 (窗口偏移、分页、HTTP、批处理、重试、速率限制)
 *   <li>完整配置聚合 (loadConfiguration)
 *   <li>处理 null 参数 (operationType, at)
 *   <li>操作类型键规范化
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProvenanceConfigRepositoryMpImpl 单元测试")
class ProvenanceConfigRepositoryMpImplTest {

  @Mock private RegProvenanceMapper provenanceMapper;
  @Mock private RegProvWindowOffsetCfgMapper windowOffsetCfgMapper;
  @Mock private RegProvPaginationCfgMapper paginationCfgMapper;
  @Mock private RegProvHttpCfgMapper httpCfgMapper;
  @Mock private RegProvBatchingCfgMapper batchingCfgMapper;
  @Mock private RegProvRetryCfgMapper retryCfgMapper;
  @Mock private RegProvRateLimitCfgMapper rateLimitCfgMapper;
  @Mock private ProvenanceEntityConverter converter;

  @InjectMocks private ProvenanceConfigRepositoryMpImpl repository;

  private static final ProvenanceCode TEST_PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final Long TEST_PROVENANCE_ID = 1L;
  private static final String TEST_OPERATION_TYPE = "search";
  private static final String TEST_OPERATION_KEY = "SEARCH";
  private static final Instant TEST_TIMESTAMP = Instant.parse("2025-01-01T00:00:00Z");

  @Nested
  @DisplayName("findProvenanceByCode 场景")
  class FindProvenanceByCodeTests {

    @Test
    @DisplayName("应成功查找数据源")
    void shouldFindProvenanceSuccessfully() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED", "PubMed");
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.of(provenanceDO));

      Provenance mockProvenance = org.mockito.Mockito.mock(Provenance.class);
      when(converter.toDomain(provenanceDO)).thenReturn(mockProvenance);

      // When
      Optional<Provenance> result = repository.findProvenanceByCode(TEST_PROVENANCE_CODE);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(mockProvenance);
      verify(provenanceMapper).selectByCode("PUBMED");
      verify(converter).toDomain(provenanceDO);
    }

    @Test
    @DisplayName("应在数据源不存在时返回空 Optional")
    void shouldReturnEmptyWhenProvenanceNotFound() {
      // Given
      when(provenanceMapper.selectByCode("PUBMED")).thenReturn(Optional.empty());

      // When
      Optional<Provenance> result = repository.findProvenanceByCode(TEST_PROVENANCE_CODE);

      // Then
      assertThat(result).isEmpty();
      verify(provenanceMapper).selectByCode("PUBMED");
      verify(converter, never()).toDomain(any(RegProvenanceDO.class));
    }
  }

  @Nested
  @DisplayName("findAllProvenances 场景")
  class FindAllProvenancesTests {

    @Test
    @DisplayName("应成功查询所有激活的数据源")
    void shouldFindAllActiveProvenances() {
      // Given
      List<RegProvenanceDO> provenanceDOs =
          List.of(
              createProvenanceDO(1L, "PUBMED", "PubMed"),
              createProvenanceDO(2L, "EPMC", "Europe PMC"));
      when(provenanceMapper.selectAllActive()).thenReturn(provenanceDOs);

      Provenance provenance1 = org.mockito.Mockito.mock(Provenance.class);
      Provenance provenance2 = org.mockito.Mockito.mock(Provenance.class);
      when(converter.toDomain(provenanceDOs.get(0))).thenReturn(provenance1);
      when(converter.toDomain(provenanceDOs.get(1))).thenReturn(provenance2);

      // When
      List<Provenance> result = repository.findAllProvenances();

      // Then
      assertThat(result).hasSize(2).containsExactly(provenance1, provenance2);
      verify(provenanceMapper).selectAllActive();
      verify(converter).toDomain(provenanceDOs.get(0));
      verify(converter).toDomain(provenanceDOs.get(1));
    }

    @Test
    @DisplayName("应在没有激活数据源时返回空列表")
    void shouldReturnEmptyListWhenNoActiveProvenances() {
      // Given
      when(provenanceMapper.selectAllActive()).thenReturn(List.of());

      // When
      List<Provenance> result = repository.findAllProvenances();

      // Then
      assertThat(result).isEmpty();
      verify(provenanceMapper).selectAllActive();
      verify(converter, never()).toDomain(any(RegProvenanceDO.class));
    }
  }

  @Nested
  @DisplayName("findActiveWindowOffset 场景")
  class FindActiveWindowOffsetTests {

    @Test
    @DisplayName("应成功查询窗口偏移配置")
    void shouldFindWindowOffsetSuccessfully() {
      // Given
      RegProvWindowOffsetCfgDO cfgDO = createWindowOffsetCfgDO();
      when(windowOffsetCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(cfgDO));

      WindowOffsetConfig config = org.mockito.Mockito.mock(WindowOffsetConfig.class);
      when(converter.toDomain(cfgDO)).thenReturn(config);

      // When
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(
              TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(config);
      verify(windowOffsetCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(converter).toDomain(cfgDO);
    }

    @Test
    @DisplayName("应在配置不存在时返回空 Optional")
    void shouldReturnEmptyWhenConfigNotFound() {
      // Given
      when(windowOffsetCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());

      // When
      Optional<WindowOffsetConfig> result =
          repository.findActiveWindowOffset(
              TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isEmpty();
      verify(windowOffsetCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(converter, never()).toDomain(any(RegProvWindowOffsetCfgDO.class));
    }

    @Test
    @DisplayName("应在 at 参数为 null 时使用当前时间")
    void shouldUseCurrentTimeWhenAtIsNull() {
      // Given
      when(windowOffsetCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());

      // When
      repository.findActiveWindowOffset(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, null);

      // Then: 应使用当前时间 (验证调用了带 Instant 参数的方法)
      verify(windowOffsetCfgMapper)
          .selectActiveMerged(eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class));
    }

    @Test
    @DisplayName("应将 null 操作类型转换为 ALL")
    void shouldConvertNullOperationTypeToAll() {
      // Given
      when(windowOffsetCfgMapper.selectActiveMerged(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(Optional.empty());

      // When
      repository.findActiveWindowOffset(TEST_PROVENANCE_ID, null, TEST_TIMESTAMP);

      // Then
      verify(windowOffsetCfgMapper).selectActiveMerged(TEST_PROVENANCE_ID, "ALL", TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("findActivePagination 场景")
  class FindActivePaginationTests {

    @Test
    @DisplayName("应成功查询分页配置")
    void shouldFindPaginationSuccessfully() {
      // Given
      RegProvPaginationCfgDO cfgDO = createPaginationCfgDO();
      when(paginationCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(cfgDO));

      PaginationConfig config = org.mockito.Mockito.mock(PaginationConfig.class);
      when(converter.toDomain(cfgDO)).thenReturn(config);

      // When
      Optional<PaginationConfig> result =
          repository.findActivePagination(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(config);
      verify(paginationCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应在配置不存在时返回空 Optional")
    void shouldReturnEmptyWhenPaginationNotFound() {
      // Given
      when(paginationCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());

      // When
      Optional<PaginationConfig> result =
          repository.findActivePagination(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findActiveHttpConfig 场景")
  class FindActiveHttpConfigTests {

    @Test
    @DisplayName("应成功查询 HTTP 配置")
    void shouldFindHttpConfigSuccessfully() {
      // Given
      RegProvHttpCfgDO cfgDO = createHttpCfgDO();
      when(httpCfgMapper.selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(cfgDO));

      HttpConfig config = org.mockito.Mockito.mock(HttpConfig.class);
      when(converter.toDomain(cfgDO)).thenReturn(config);

      // When
      Optional<HttpConfig> result =
          repository.findActiveHttpConfig(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(config);
      verify(httpCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("findActiveBatching 场景")
  class FindActiveBatchingTests {

    @Test
    @DisplayName("应成功查询批处理配置")
    void shouldFindBatchingSuccessfully() {
      // Given
      RegProvBatchingCfgDO cfgDO = createBatchingCfgDO();
      when(batchingCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(cfgDO));

      BatchingConfig config = org.mockito.Mockito.mock(BatchingConfig.class);
      when(converter.toDomain(cfgDO)).thenReturn(config);

      // When
      Optional<BatchingConfig> result =
          repository.findActiveBatching(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(config);
      verify(batchingCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("findActiveRetry 场景")
  class FindActiveRetryTests {

    @Test
    @DisplayName("应成功查询重试配置")
    void shouldFindRetrySuccessfully() {
      // Given
      RegProvRetryCfgDO cfgDO = createRetryCfgDO();
      when(retryCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(cfgDO));

      RetryConfig config = org.mockito.Mockito.mock(RetryConfig.class);
      when(converter.toDomain(cfgDO)).thenReturn(config);

      // When
      Optional<RetryConfig> result =
          repository.findActiveRetry(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(config);
      verify(retryCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("findActiveRateLimit 场景")
  class FindActiveRateLimitTests {

    @Test
    @DisplayName("应成功查询速率限制配置")
    void shouldFindRateLimitSuccessfully() {
      // Given
      RegProvRateLimitCfgDO cfgDO = createRateLimitCfgDO();
      when(rateLimitCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(cfgDO));

      RateLimitConfig config = org.mockito.Mockito.mock(RateLimitConfig.class);
      when(converter.toDomain(cfgDO)).thenReturn(config);

      // When
      Optional<RateLimitConfig> result =
          repository.findActiveRateLimit(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(config);
      verify(rateLimitCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
    }
  }

  @Nested
  @DisplayName("loadConfiguration 完整聚合场景")
  class LoadConfigurationTests {

    @Test
    @DisplayName("应成功加载完整配置聚合")
    void shouldLoadCompleteConfigurationSuccessfully() {
      // Given: Mock 数据源查询
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED", "PubMed");
      when(provenanceMapper.selectById(TEST_PROVENANCE_ID)).thenReturn(provenanceDO);
      Provenance provenance = org.mockito.Mockito.mock(Provenance.class);
      when(provenance.id()).thenReturn(TEST_PROVENANCE_ID);
      when(converter.toDomain(provenanceDO)).thenReturn(provenance);

      // Mock 各种配置查询
      RegProvWindowOffsetCfgDO windowOffsetDO = createWindowOffsetCfgDO();
      when(windowOffsetCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(windowOffsetDO));
      WindowOffsetConfig windowOffset = org.mockito.Mockito.mock(WindowOffsetConfig.class);
      when(converter.toDomain(windowOffsetDO)).thenReturn(windowOffset);

      RegProvPaginationCfgDO paginationDO = createPaginationCfgDO();
      when(paginationCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(paginationDO));
      PaginationConfig pagination = org.mockito.Mockito.mock(PaginationConfig.class);
      when(converter.toDomain(paginationDO)).thenReturn(pagination);

      RegProvHttpCfgDO httpDO = createHttpCfgDO();
      when(httpCfgMapper.selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(httpDO));
      HttpConfig http = org.mockito.Mockito.mock(HttpConfig.class);
      when(converter.toDomain(httpDO)).thenReturn(http);

      RegProvBatchingCfgDO batchingDO = createBatchingCfgDO();
      when(batchingCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(batchingDO));
      BatchingConfig batching = org.mockito.Mockito.mock(BatchingConfig.class);
      when(converter.toDomain(batchingDO)).thenReturn(batching);

      RegProvRetryCfgDO retryDO = createRetryCfgDO();
      when(retryCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(retryDO));
      RetryConfig retry = org.mockito.Mockito.mock(RetryConfig.class);
      when(converter.toDomain(retryDO)).thenReturn(retry);

      RegProvRateLimitCfgDO rateLimitDO = createRateLimitCfgDO();
      when(rateLimitCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(rateLimitDO));
      RateLimitConfig rateLimit = org.mockito.Mockito.mock(RateLimitConfig.class);
      when(converter.toDomain(rateLimitDO)).thenReturn(rateLimit);

      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      ProvenanceConfiguration config = result.get();
      assertThat(config.provenance()).isEqualTo(provenance);
      assertThat(config.windowOffset()).isEqualTo(windowOffset);
      assertThat(config.pagination()).isEqualTo(pagination);
      assertThat(config.http()).isEqualTo(http);
      assertThat(config.batching()).isEqualTo(batching);
      assertThat(config.retry()).isEqualTo(retry);
      assertThat(config.rateLimit()).isEqualTo(rateLimit);

      // 验证所有 Mapper 都被调用
      verify(provenanceMapper).selectById(TEST_PROVENANCE_ID);
      verify(windowOffsetCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(paginationCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(httpCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(batchingCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(retryCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
      verify(rateLimitCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应在数据源不存在时返回空 Optional")
    void shouldReturnEmptyWhenProvenanceNotFound() {
      // Given: 数据源不存在
      when(provenanceMapper.selectById(TEST_PROVENANCE_ID)).thenReturn(null);

      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isEmpty();
      verify(provenanceMapper).selectById(TEST_PROVENANCE_ID);

      // 验证后续配置查询未执行
      verify(windowOffsetCfgMapper, never())
          .selectActiveMerged(anyLong(), anyString(), any(Instant.class));
      verify(paginationCfgMapper, never())
          .selectActiveMerged(anyLong(), anyString(), any(Instant.class));
      verify(httpCfgMapper, never()).selectActiveMerged(anyLong(), anyString(), any(Instant.class));
      verify(batchingCfgMapper, never())
          .selectActiveMerged(anyLong(), anyString(), any(Instant.class));
      verify(retryCfgMapper, never())
          .selectActiveMerged(anyLong(), anyString(), any(Instant.class));
      verify(rateLimitCfgMapper, never())
          .selectActiveMerged(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("应在部分配置缺失时创建部分聚合")
    void shouldCreatePartialConfigurationWhenSomeConfigsMissing() {
      // Given: 只有数据源和窗口偏移配置
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED", "PubMed");
      when(provenanceMapper.selectById(TEST_PROVENANCE_ID)).thenReturn(provenanceDO);
      Provenance provenance = org.mockito.Mockito.mock(Provenance.class);
      when(provenance.id()).thenReturn(TEST_PROVENANCE_ID);
      when(converter.toDomain(provenanceDO)).thenReturn(provenance);

      RegProvWindowOffsetCfgDO windowOffsetDO = createWindowOffsetCfgDO();
      when(windowOffsetCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.of(windowOffsetDO));
      WindowOffsetConfig windowOffset = org.mockito.Mockito.mock(WindowOffsetConfig.class);
      when(converter.toDomain(windowOffsetDO)).thenReturn(windowOffset);

      // 其他配置返回空
      when(paginationCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());
      when(httpCfgMapper.selectActiveMerged(TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());
      when(batchingCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());
      when(retryCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());
      when(rateLimitCfgMapper.selectActiveMerged(
              TEST_PROVENANCE_ID, TEST_OPERATION_KEY, TEST_TIMESTAMP))
          .thenReturn(Optional.empty());

      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isPresent();
      ProvenanceConfiguration config = result.get();
      assertThat(config.provenance()).isEqualTo(provenance);
      assertThat(config.windowOffset()).isEqualTo(windowOffset);
      assertThat(config.pagination()).isNull();
      assertThat(config.http()).isNull();
      assertThat(config.batching()).isNull();
      assertThat(config.retry()).isNull();
      assertThat(config.rateLimit()).isNull();
    }

    @Test
    @DisplayName("应在 at 参数为 null 时使用当前时间")
    void shouldUseCurrentTimeWhenAtIsNull() {
      // Given
      RegProvenanceDO provenanceDO = createProvenanceDO(TEST_PROVENANCE_ID, "PUBMED", "PubMed");
      when(provenanceMapper.selectById(TEST_PROVENANCE_ID)).thenReturn(provenanceDO);
      Provenance provenance = org.mockito.Mockito.mock(Provenance.class);
      when(provenance.id()).thenReturn(TEST_PROVENANCE_ID);
      when(converter.toDomain(provenanceDO)).thenReturn(provenance);

      when(windowOffsetCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());
      when(paginationCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());
      when(httpCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());
      when(batchingCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());
      when(retryCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());
      when(rateLimitCfgMapper.selectActiveMerged(
              eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class)))
          .thenReturn(Optional.empty());

      // When: at 参数为 null
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(TEST_PROVENANCE_ID, TEST_OPERATION_TYPE, null);

      // Then: 应使用当前时间
      assertThat(result).isPresent();
      verify(windowOffsetCfgMapper)
          .selectActiveMerged(eq(TEST_PROVENANCE_ID), eq(TEST_OPERATION_KEY), any(Instant.class));
    }

    @Test
    @DisplayName("应在 provenanceId 为 null 时返回空 Optional")
    void shouldReturnEmptyWhenProvenanceIdIsNull() {
      // When
      Optional<ProvenanceConfiguration> result =
          repository.loadConfiguration(null, TEST_OPERATION_TYPE, TEST_TIMESTAMP);

      // Then
      assertThat(result).isEmpty();
      verify(provenanceMapper, never()).selectById(anyLong());
    }
  }

  @Nested
  @DisplayName("操作类型键规范化场景")
  class OperationKeyNormalizationTests {

    @Test
    @DisplayName("应将小写操作类型转换为大写操作键")
    void shouldConvertLowercaseOperationTypeToUppercase() {
      // Given
      when(windowOffsetCfgMapper.selectActiveMerged(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(Optional.empty());

      // When: 传入小写操作类型 "search"
      repository.findActiveWindowOffset(TEST_PROVENANCE_ID, "search", TEST_TIMESTAMP);

      // Then: 应转换为大写 "SEARCH"
      verify(windowOffsetCfgMapper)
          .selectActiveMerged(TEST_PROVENANCE_ID, "SEARCH", TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("应将空白操作类型转换为 ALL")
    void shouldConvertBlankOperationTypeToAll() {
      // Given
      when(paginationCfgMapper.selectActiveMerged(anyLong(), anyString(), any(Instant.class)))
          .thenReturn(Optional.empty());

      // When: 传入空白操作类型
      repository.findActivePagination(TEST_PROVENANCE_ID, "   ", TEST_TIMESTAMP);

      // Then: 应转换为 "ALL"
      verify(paginationCfgMapper).selectActiveMerged(TEST_PROVENANCE_ID, "ALL", TEST_TIMESTAMP);
    }
  }

  // ==================== 辅助方法 ====================

  private RegProvenanceDO createProvenanceDO(Long id, String code, String name) {
    RegProvenanceDO provenance = new RegProvenanceDO();
    provenance.setId(id);
    provenance.setProvenanceCode(code);
    provenance.setProvenanceName(name);
    provenance.setIsActive(true);
    provenance.setLifecycleStatusCode("ACTIVE");
    return provenance;
  }

  private RegProvWindowOffsetCfgDO createWindowOffsetCfgDO() {
    RegProvWindowOffsetCfgDO cfg = new RegProvWindowOffsetCfgDO();
    cfg.setProvenanceId(TEST_PROVENANCE_ID);
    cfg.setOperationType(TEST_OPERATION_KEY);
    cfg.setWindowModeCode("SLIDING");
    cfg.setWindowSizeValue(30);
    cfg.setWindowSizeUnitCode("DAY");
    cfg.setLookbackValue(7);
    cfg.setLookbackUnitCode("DAY");
    cfg.setLifecycleStatusCode("ACTIVE");
    return cfg;
  }

  private RegProvPaginationCfgDO createPaginationCfgDO() {
    RegProvPaginationCfgDO cfg = new RegProvPaginationCfgDO();
    cfg.setProvenanceId(TEST_PROVENANCE_ID);
    cfg.setOperationType(TEST_OPERATION_KEY);
    cfg.setPaginationModeCode("PAGE_SIZE");
    cfg.setPageSizeValue(100);
    cfg.setMaxPagesPerExecution(1000);
    cfg.setLifecycleStatusCode("ACTIVE");
    return cfg;
  }

  private RegProvHttpCfgDO createHttpCfgDO() {
    RegProvHttpCfgDO cfg = new RegProvHttpCfgDO();
    cfg.setProvenanceId(TEST_PROVENANCE_ID);
    cfg.setOperationType(TEST_OPERATION_KEY);
    cfg.setTimeoutConnectMillis(30000);
    cfg.setTimeoutReadMillis(60000);
    cfg.setTimeoutTotalMillis(300000);
    cfg.setTlsVerifyEnabled(true);
    cfg.setLifecycleStatusCode("ACTIVE");
    return cfg;
  }

  private RegProvBatchingCfgDO createBatchingCfgDO() {
    RegProvBatchingCfgDO cfg = new RegProvBatchingCfgDO();
    cfg.setProvenanceId(TEST_PROVENANCE_ID);
    cfg.setOperationType(TEST_OPERATION_KEY);
    cfg.setDetailFetchBatchSize(50);
    cfg.setIdsParamName("id");
    cfg.setIdsJoinDelimiter(",");
    cfg.setMaxIdsPerRequest(200);
    cfg.setLifecycleStatusCode("ACTIVE");
    return cfg;
  }

  private RegProvRetryCfgDO createRetryCfgDO() {
    RegProvRetryCfgDO cfg = new RegProvRetryCfgDO();
    cfg.setProvenanceId(TEST_PROVENANCE_ID);
    cfg.setOperationType(TEST_OPERATION_KEY);
    cfg.setMaxRetryTimes(3);
    cfg.setBackoffPolicyTypeCode("EXPONENTIAL");
    cfg.setInitialDelayMillis(1000);
    cfg.setMaxDelayMillis(30000);
    cfg.setExpMultiplierValue(2.0);
    cfg.setRetryOnNetworkError(true);
    cfg.setLifecycleStatusCode("ACTIVE");
    return cfg;
  }

  private RegProvRateLimitCfgDO createRateLimitCfgDO() {
    RegProvRateLimitCfgDO cfg = new RegProvRateLimitCfgDO();
    cfg.setProvenanceId(TEST_PROVENANCE_ID);
    cfg.setOperationType(TEST_OPERATION_KEY);
    cfg.setMaxConcurrentRequests(10);
    cfg.setPerCredentialQpsLimit(3);
    cfg.setLifecycleStatusCode("ACTIVE");
    return cfg;
  }
}
