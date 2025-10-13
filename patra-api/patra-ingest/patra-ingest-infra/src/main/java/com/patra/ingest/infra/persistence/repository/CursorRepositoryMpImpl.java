package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.infra.persistence.converter.CursorConverter;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import com.patra.ingest.infra.persistence.mapper.CursorMapper;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 游标（Cursor）仓储实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>增量游标插入 / 更新（含规范化 instant/numeric）。
 *   <li>按（provenance, operation, key, scope, namespace）组合键查找。
 *   <li>查询特定来源/操作的全局时间型游标最新水位（监控 / 回放起点）。
 * </ul>
 *
 * <p>设计：更新后立即 selectById 以获取数据库派生字段（如 normalizedInstant）。
 *
 * <p>日志策略：DEBUG 输出 insert/update 关键标识；不打印查询日志。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CursorRepositoryMpImpl implements CursorRepository {

  private final CursorMapper mapper;
  private final CursorConverter converter;

  /**
   * 保存游标。
   *
   * @param cursor 游标聚合
   * @return 持久化后最新聚合
   */
  @Override
  public Cursor save(Cursor cursor) {
    CursorDO dto = converter.toDO(cursor);
    if (dto.getId() == null) {
      mapper.insert(dto);
      if (log.isDebugEnabled()) {
        log.debug(
            "[INGEST][INFRA] cursor insert provenance={} operation={} key={} scope={} ns={}",
            dto.getProvenanceCode(),
            dto.getOperationCode(),
            dto.getCursorKey(),
            dto.getNamespaceScopeCode(),
            dto.getNamespaceKey());
      }
    } else {
      mapper.updateById(dto);
      if (log.isDebugEnabled()) {
        log.debug(
            "[INGEST][INFRA] cursor update id={} key={} scope={} ns={} watermark={}",
            dto.getId(),
            dto.getCursorKey(),
            dto.getNamespaceScopeCode(),
            dto.getNamespaceKey(),
            dto.getNormalizedInstant());
      }
    }
    CursorDO persisted = mapper.selectById(dto.getId());
    return converter.toDomain(persisted);
  }

  /** 组合键查找游标。 */
  @Override
  public Optional<Cursor> find(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey) {
    CursorDO found =
        mapper.selectOne(
            new QueryWrapper<CursorDO>()
                .eq("provenance_code", provenanceCode)
                .eq("operation_code", operationCode)
                .eq("cursor_key", cursorKey)
                .eq("namespace_scope_code", namespaceScopeCode)
                .eq("namespace_key", namespaceKey));
    return Optional.ofNullable(found).map(converter::toDomain);
  }

  /** 查询来源 / 操作的最新全局 TIME 类型游标水位（标准化时间）。 */
  @Override
  public Optional<Instant> findLatestGlobalTimeWatermark(
      String provenanceCode, String operationCode) {
    QueryWrapper<CursorDO> wrapper = new QueryWrapper<>();
    wrapper.eq("provenance_code", provenanceCode);
    if (operationCode != null) {
      wrapper.eq("operation_code", operationCode);
    }
    wrapper
        .eq("cursor_type_code", "TIME")
        .eq("namespace_scope_code", "GLOBAL")
        .orderByDesc("updated_at")
        .last("LIMIT 1");
    CursorDO one = mapper.selectOne(wrapper);
    return Optional.ofNullable(one).map(CursorDO::getNormalizedInstant);
  }
}
