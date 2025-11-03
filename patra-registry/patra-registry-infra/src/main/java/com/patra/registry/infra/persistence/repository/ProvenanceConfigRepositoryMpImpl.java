package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.vo.provenance.*;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import com.patra.registry.domain.support.RegistryKeyStandardizer;
import com.patra.registry.infra.persistence.converter.ProvenanceEntityConverter;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import com.patra.registry.infra.persistence.mapper.provenance.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 数据源配置仓储实现,基于 MyBatis-Plus。
 *
 * <p>实现策略:
 *
 * <ul>
 *   <li>应用操作级优先于数据源级的配置优先级层次
 *   <li>当操作特定配置缺失时回退到数据源级默认值
 *   <li>统一规范化作用域键,避免跨组件的字符串漂移
 * </ul>
 *
 * <p>日志策略:
 *
 * <ul>
 *   <li>DEBUG 级别记录配置查询和聚合过程
 *   <li>WARN 级别记录数据源未找到等异常情况
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {

  private final RegProvenanceMapper provenanceMapper;
  private final RegProvWindowOffsetCfgMapper windowOffsetCfgMapper;
  private final RegProvPaginationCfgMapper paginationCfgMapper;
  private final RegProvHttpCfgMapper httpCfgMapper;
  private final RegProvBatchingCfgMapper batchingCfgMapper;
  private final RegProvRetryCfgMapper retryCfgMapper;
  private final RegProvRateLimitCfgMapper rateLimitCfgMapper;
  private final ProvenanceEntityConverter converter;

  /**
   * 根据业务代码查询数据源。
   *
   * @param provenanceCode 数据源代码
   * @return 数据源领域对象(可选)
   */
  @Override
  public Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode) {
    String code = provenanceCode.getCode();
    Optional<Provenance> result = provenanceMapper.selectByCode(code).map(converter::toDomain);
    if (log.isDebugEnabled()) {
      log.debug(
          "Provenance lookup for code [{}]: {}", code, result.isPresent() ? "found" : "not found");
    }
    return result;
  }

  /**
   * 列出所有激活的数据源。
   *
   * @return 激活的数据源领域对象列表
   */
  @Override
  public List<Provenance> findAllProvenances() {
    log.debug("Querying all active provenances from database");
    List<Provenance> provenances =
        provenanceMapper.selectAllActive().stream().map(converter::toDomain).toList();
    log.debug(
        "Converting {} ProvenanceDO entities to domain models, codes: {}",
        provenances.size(),
        provenances.stream().map(Provenance::code).toList());
    return provenances;
  }

  /**
   * 查询指定数据源和操作的激活窗口偏移配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return 窗口偏移配置(可选)
   */
  @Override
  public Optional<WindowOffsetConfig> findActiveWindowOffset(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "window offset",
        windowOffsetCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * 查询指定数据源和操作的激活分页配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return 分页配置(可选)
   */
  @Override
  public Optional<PaginationConfig> findActivePagination(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "pagination",
        paginationCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * 查询指定数据源和操作的激活 HTTP 配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return HTTP 配置(可选)
   */
  @Override
  public Optional<HttpConfig> findActiveHttpConfig(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "HTTP",
        httpCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * 查询指定数据源和操作的激活批处理配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return 批处理配置(可选)
   */
  @Override
  public Optional<BatchingConfig> findActiveBatching(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "batching",
        batchingCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * 查询指定数据源和操作的激活重试配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return 重试配置(可选)
   */
  @Override
  public Optional<RetryConfig> findActiveRetry(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "retry",
        retryCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * 查询指定数据源和操作的激活速率限制配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return 速率限制配置(可选)
   */
  @Override
  public Optional<RateLimitConfig> findActiveRateLimit(
      Long provenanceId, String operationType, Instant at) {
    return findActiveConfig(
        provenanceId,
        operationType,
        at,
        "rate limit",
        rateLimitCfgMapper::selectActiveMerged,
        converter::toDomain);
  }

  /**
   * 加载指定操作的完整数据源配置聚合。
   *
   * <p>将所有配置切片(窗口、分页、HTTP、批处理、重试、速率限制)组装为单个聚合, 在可用时应用操作特定的覆盖配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @return 完整的配置聚合(可选)
   */
  @Override
  public Optional<ProvenanceConfiguration> loadConfiguration(
      Long provenanceId, String operationType, Instant at) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Loading provenance configuration for provenanceId [{}] with operationType [{}]",
          provenanceId,
          operationType);
    }

    Optional<Provenance> provenanceOpt = findProvenanceById(provenanceId);
    if (provenanceOpt.isEmpty()) {
      log.warn("Provenance not found for provenanceId [{}]", provenanceId);
      return Optional.empty();
    }

    Instant timestamp = atOrNow(at);
    Provenance provenance = provenanceOpt.get();
    ProvenanceConfiguration configuration =
        assembleConfiguration(provenance, operationType, timestamp);

    if (log.isDebugEnabled()) {
      log.debug(
          "Assembled configuration aggregate for provenance [{}] with {} configs applied",
          provenance.code(),
          countNonNullConfigs(configuration));
    }
    return Optional.of(configuration);
  }

  /**
   * 从各个配置切片组装完整的配置聚合。
   *
   * @param provenance 数据源领域对象
   * @param operationType 操作类型键
   * @param timestamp 生效时间戳
   * @return 组装后的配置聚合
   */
  private ProvenanceConfiguration assembleConfiguration(
      Provenance provenance, String operationType, Instant timestamp) {
    Long provenanceId = provenance.id();
    log.debug(
        "Assembling configuration aggregate for provenance [{}], operationType [{}]",
        provenance.code(),
        operationType);
    return new ProvenanceConfiguration(
        provenance,
        findActiveWindowOffset(provenanceId, operationType, timestamp).orElse(null),
        findActivePagination(provenanceId, operationType, timestamp).orElse(null),
        findActiveHttpConfig(provenanceId, operationType, timestamp).orElse(null),
        findActiveBatching(provenanceId, operationType, timestamp).orElse(null),
        findActiveRetry(provenanceId, operationType, timestamp).orElse(null),
        findActiveRateLimit(provenanceId, operationType, timestamp).orElse(null));
  }

  /**
   * 统计非空配置组件数量。
   *
   * @param configuration 配置聚合
   * @return 非空配置数量
   */
  private int countNonNullConfigs(ProvenanceConfiguration configuration) {
    return (int)
        Stream.of(
                configuration.windowOffset(),
                configuration.pagination(),
                configuration.http(),
                configuration.batching(),
                configuration.retry(),
                configuration.rateLimit())
            .filter(Objects::nonNull)
            .count();
  }

  /**
   * 通用辅助方法,查找具有时态切片和操作作用域合并的激活配置。
   *
   * @param provenanceId 数据源标识
   * @param operationType 操作类型键
   * @param at 查询时间戳(null 表示当前时间)
   * @param configName 配置类型名称(用于日志)
   * @param selector Mapper 选择函数
   * @param converter DO 到领域对象的转换函数
   * @param <DO> 数据库对象类型
   * @param <DOMAIN> 领域对象类型
   * @return 领域配置对象(可选)
   */
  private <DO, DOMAIN> Optional<DOMAIN> findActiveConfig(
      Long provenanceId,
      String operationType,
      Instant at,
      String configName,
      ConfigSelector<DO> selector,
      java.util.function.Function<DO, DOMAIN> converter) {
    Instant timestamp = atOrNow(at);
    String operationKey = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

    Optional<DOMAIN> result = selector.select(provenanceId, operationKey, timestamp).map(converter);

    if (log.isDebugEnabled()) {
      log.debug(
          "{} config for provenance [{}] operation [{}]: {}",
          configName,
          provenanceId,
          operationKey,
          result.isPresent() ? "found" : "not found");
    }
    return result;
  }

  /**
   * 根据 ID 查询数据源领域对象。
   *
   * @param provenanceId 数据源标识
   * @return 数据源领域对象(可选)
   */
  private Optional<Provenance> findProvenanceById(Long provenanceId) {
    if (provenanceId == null) {
      log.debug("Provenance ID is null, skipping database query");
      return Optional.empty();
    }
    log.debug("Querying provenance by ID [{}] from database", provenanceId);
    RegProvenanceDO entity = provenanceMapper.selectById(provenanceId);
    if (entity == null) {
      log.debug("Provenance entity not found for ID [{}]", provenanceId);
      return Optional.empty();
    }
    log.debug(
        "Converting ProvenanceDO to domain model for ID [{}], code [{}]",
        provenanceId,
        entity.getProvenanceCode());
    return Optional.of(converter.toDomain(entity));
  }

  /**
   * 返回提供的时间戳,如果为 null 则返回当前时间。
   *
   * @param at 要检查的时间戳
   * @return 时间戳或当前时间
   */
  private Instant atOrNow(Instant at) {
    return at != null ? at : Instant.now();
  }

  /**
   * 配置选择器操作的函数式接口。
   *
   * @param <DO> 数据库对象类型
   */
  @FunctionalInterface
  private interface ConfigSelector<DO> {
    /**
     * 从数据库选择激活的配置。
     *
     * @param provenanceId 数据源标识
     * @param operationKey 规范化的操作键
     * @param timestamp 生效时间戳
     * @return 数据库对象(可选)
     */
    Optional<DO> select(Long provenanceId, String operationKey, Instant timestamp);
  }
}
