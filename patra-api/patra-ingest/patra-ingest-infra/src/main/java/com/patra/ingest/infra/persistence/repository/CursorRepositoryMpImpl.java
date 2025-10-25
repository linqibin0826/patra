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
 * MyBatis-Plus implementation of CursorRepository.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Insert and update incremental cursors with normalized instant/numeric values.
 *   <li>Find cursors by composite key (provenance, operation, key, scope, namespace).
 *   <li>Query latest global time cursor watermark for specific provenance/operation (for monitoring
 *       and replay starting point).
 * </ul>
 *
 * <p>Design: After update, immediately selectById to retrieve database-derived fields such as
 * normalizedInstant.
 *
 * <p>Logging strategy: DEBUG for insert/update with key identifiers; no query logging.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CursorRepositoryMpImpl implements CursorRepository {

  private final CursorMapper mapper;
  private final CursorConverter converter;

  /**
   * Saves a cursor.
   *
   * @param cursor cursor entity
   * @return persisted cursor with database-generated fields
   */
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

  /** Finds cursor by composite key. */
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

  /** Finds latest global TIME cursor watermark for provenance/operation (normalized timestamp). */
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
