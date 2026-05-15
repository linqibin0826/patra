package dev.linqibin.patra.ingest.infra.adapter.persistence;

import dev.linqibin.patra.ingest.domain.model.entity.Cursor;
import dev.linqibin.patra.ingest.domain.port.CursorRepository;
import dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper.CursorJpaMapper;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.CursorDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.CursorEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 游标（Cursor）仓储实现，基于 JPA。
///
/// 职责：
///
/// - 插入和更新增量游标，包含规范化的 instant/numeric 值
/// - 按复合键（数据源、操作、键、作用域、命名空间）查找游标
/// - 查询特定数据源/操作的最新全局时间游标水位（用于监控和重放起点）
///
/// 设计：更新后立即通过 JPA save 返回获取数据库衍生字段（如 normalizedInstant）。
///
/// 日志策略：DEBUG 级别记录 insert/update 及关键标识符；查询操作不记录日志。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class CursorRepositoryAdapter implements CursorRepository {

  /// Cursor JPA Repository
  private final CursorDao cursorDao;

  /// 领域实体与 JPA 实体转换器
  private final CursorJpaMapper cursorJpaMapper;

  /// 保存游标。
  ///
  /// @param cursor 游标实体
  /// @return 持久化后的游标，包含数据库生成的字段
  @Override
  public Cursor save(Cursor cursor) {
    CursorEntity entity = cursorJpaMapper.toEntity(cursor);

    if (cursor.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      if (log.isDebugEnabled()) {
        log.debug(
            "cursor insert provenance={} operation={} key={} scope={} ns={}",
            entity.getProvenanceCode(),
            entity.getOperationCode(),
            entity.getCursorKey(),
            entity.getNamespaceScopeCode(),
            entity.getNamespaceKey());
      }
    } else {
      // 更新：使用现有 ID
      entity.setId(cursor.getId());
      if (log.isDebugEnabled()) {
        log.debug(
            "cursor update id={} key={} scope={} ns={} watermark={}",
            entity.getId(),
            entity.getCursorKey(),
            entity.getNamespaceScopeCode(),
            entity.getNamespaceKey(),
            entity.getNormalizedInstant());
      }
    }

    CursorEntity saved = cursorDao.save(entity);
    return cursorJpaMapper.toAggregate(saved);
  }

  /// 按复合键查找游标。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作代码
  /// @param cursorKey 游标键
  /// @param namespaceScopeCode 命名空间作用域代码
  /// @param namespaceKey 命名空间键
  /// @return 游标实体（可选）
  @Override
  public Optional<Cursor> find(
      ProvenanceCode provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey) {
    Optional<CursorEntity> found =
        cursorDao
            .findByProvenanceCodeAndOperationCodeAndCursorKeyAndNamespaceScopeCodeAndNamespaceKey(
                provenanceCode.getCode(),
                operationCode,
                cursorKey,
                namespaceScopeCode,
                namespaceKey);
    if (log.isDebugEnabled()) {
      log.debug(
          "query cursor by provenance={} operation={} key={} scope={} ns={}, found={}",
          provenanceCode,
          operationCode,
          cursorKey,
          namespaceScopeCode,
          namespaceKey,
          found.isPresent());
    }
    return found.map(cursorJpaMapper::toAggregate);
  }

  /// 查找数据源/操作的最新全局时间游标水位（规范化时间戳）。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作代码（可为 null）
  /// @return 最新水位时间戳（可选）
  @Override
  public Optional<Instant> findLatestGlobalTimeWatermark(
      ProvenanceCode provenanceCode, String operationCode) {
    // 查询数据源下所有 TIME 类型 + GLOBAL 作用域的游标，取最新水位
    List<CursorEntity> cursors =
        operationCode != null
            ? cursorDao.findByProvenanceCodeAndOperationCode(
                provenanceCode.getCode(), operationCode)
            : cursorDao.findByProvenanceCode(provenanceCode.getCode());

    Instant watermark =
        cursors.stream()
            .filter(c -> "TIME".equals(c.getCursorTypeCode()))
            .filter(c -> "GLOBAL".equals(c.getNamespaceScopeCode()))
            .map(CursorEntity::getNormalizedInstant)
            .filter(instant -> instant != null)
            .max(Instant::compareTo)
            .orElse(null);

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
