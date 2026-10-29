package dev.patra.registry.infra.dictionary.persistence;

import dev.patra.registry.domain.dictionary.model.vo.ReferenceStandard;
import dev.patra.registry.domain.dictionary.port.repository.ReferenceStandardRepository;
import dev.patra.registry.infra.dictionary.persistence.dao.ReferenceStandardDao;
import dev.patra.registry.infra.dictionary.persistence.mapper.ReferenceStandardJpaMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 来源标准查询仓储实现，基于 JPA。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class ReferenceStandardRepositoryAdapter implements ReferenceStandardRepository {

  private final ReferenceStandardDao dao;
  private final ReferenceStandardJpaMapper mapper;

  @Override
  public Optional<ReferenceStandard> findByDictTypeCodeAndStandardCode(
      String dictTypeCode, String standardCode) {
    if (dictTypeCode == null
        || dictTypeCode.isBlank()
        || standardCode == null
        || standardCode.isBlank()) {
      return Optional.empty();
    }
    return dao.findByDictTypeCodeAndStandardCode(dictTypeCode, standardCode).map(mapper::toDomain);
  }

  @Override
  public Optional<ReferenceStandard> findCanonicalByDictTypeCode(String dictTypeCode) {
    if (dictTypeCode == null || dictTypeCode.isBlank()) {
      return Optional.empty();
    }
    return dao.findCanonicalByDictTypeCode(dictTypeCode).map(mapper::toDomain);
  }
}
