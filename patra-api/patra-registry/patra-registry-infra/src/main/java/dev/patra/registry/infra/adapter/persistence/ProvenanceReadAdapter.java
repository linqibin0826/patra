package dev.patra.registry.infra.adapter.persistence;

import dev.patra.common.ProvenanceCode;
import dev.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import dev.patra.registry.domain.port.ProvenanceReadPort;
import dev.patra.registry.infra.adapter.persistence.dao.provenance.ProvenanceDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 数据源读端口实现，基于 JPA。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceReadAdapter implements ProvenanceReadPort {

  private final ProvenanceDao provenanceDao;

  /// 按数据源代码解析数据源 ID。
  ///
  /// @param code 数据源代码
  /// @return 数据源 ID
  /// @throws ProvenanceNotFoundException 数据源代码不存在时
  @Override
  public long requireIdByCode(ProvenanceCode code) {
    String value = code.getCode();
    log.debug("Querying provenance ID for code [{}] from database", value);
    return provenanceDao
        .findByProvenanceCode(value)
        .map(
            entity -> {
              log.debug("Resolved provenance code [{}] to ID [{}]", value, entity.getId());
              return entity.getId();
            })
        .orElseThrow(
            () -> {
              log.warn("Provenance code not found: {}", value);
              return new ProvenanceNotFoundException("Provenance code not found: " + value);
            });
  }
}
