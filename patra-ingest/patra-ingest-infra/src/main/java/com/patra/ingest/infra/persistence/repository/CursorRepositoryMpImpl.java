package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.common.enums.ProvenanceCode;
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

/// 游标（Cursor）仓储实现,基于 MyBatis-Plus。
///
/// 职责:
///
/// - 插入和更新增量游标,包含规范化的 instant/numeric 值
///   - 按复合键(数据源、操作、键、作用域、命名空间)查找游标
///   - 查询特定数据源/操作的最新全局时间游标水位(用于监控和重放起点)
///
/// 设计: 更新后立即执行 selectById 获取数据库衍生字段(如 normalizedInstant)。
///
/// 日志策略: DEBUG 级别记录 insert/update 及关键标识符;查询操作不记录日志。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class CursorRepositoryMpImpl implements CursorRepository {

  private final CursorMapper mapper;
  private final CursorConverter converter;

  /// 保存游标。
  ///
  /// @param cursor 游标实体
  /// @return 持久化后的游标,包含数据库生成的字段
  @Override
  public Cursor save(Cursor cursor) {
    CursorDO dto = converter.toDO(cursor);
    if (dto.getId() == null) {
      mapper.insert(dto);
      if (log.isDebugEnabled()) {
        log.debug(
            "cursor insert provenance={} operation={} key={} scope={} ns={}",
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
            "cursor update id={} key={} scope={} ns={} watermark={}",
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

  /// 按复合键查找游标。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作代码
  /// @param cursorKey 游标键
  /// @param namespaceScopeCode 命名空间作用域代码
  /// @param namespaceKey 命名空间键
  /// @return 游标实体(可选)
  @Override
  public Optional<Cursor> find(
      ProvenanceCode provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey) {
    CursorDO found =
        mapper.selectOne(
            new QueryWrapper<CursorDO>()
                .eq("provenance_code", provenanceCode.getCode())
                .eq("operation_code", operationCode)
                .eq("cursor_key", cursorKey)
                .eq("namespace_scope_code", namespaceScopeCode)
                .eq("namespace_key", namespaceKey));
    boolean foundResult = found != null;
    if (log.isDebugEnabled()) {
      log.debug(
          "query cursor by provenance={} operation={} key={} scope={} ns={}, found={}",
          provenanceCode,
          operationCode,
          cursorKey,
          namespaceScopeCode,
          namespaceKey,
          foundResult);
    }
    return Optional.ofNullable(found).map(converter::toDomain);
  }

  /// 查找数据源/操作的最新全局时间游标水位(规范化时间戳)。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作代码(可为 null)
  /// @return 最新水位时间戳(可选)
  @Override
  public Optional<Instant> findLatestGlobalTimeWatermark(
      ProvenanceCode provenanceCode, String operationCode) {
    QueryWrapper<CursorDO> wrapper = new QueryWrapper<>();
    wrapper.eq("provenance_code", provenanceCode.getCode());
    if (operationCode != null) {
      wrapper.eq("operation_code", operationCode);
    }
    wrapper
        .eq("cursor_type_code", "TIME")
        .eq("namespace_scope_code", "GLOBAL")
        .orderByDesc("updated_at")
        .last("LIMIT 1");
    CursorDO one = mapper.selectOne(wrapper);
    Instant watermark = one != null ? one.getNormalizedInstant() : null;
    if (log.isDebugEnabled()) {
      log.debug(
          "query latest global TIME watermark provenance={} operation={}, watermark={}",
          provenanceCode,
          operationCode,
          watermark);
    }
    return Optional.ofNullable(watermark);
  }
}
