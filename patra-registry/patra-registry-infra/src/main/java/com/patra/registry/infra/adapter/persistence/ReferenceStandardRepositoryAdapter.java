package com.patra.registry.infra.adapter.persistence;

import com.patra.registry.domain.model.vo.reference.ReferenceStandard;
import com.patra.registry.domain.port.ReferenceStandardRepository;
import com.patra.registry.infra.adapter.persistence.converter.mapper.ReferenceStandardJpaMapper;
import com.patra.registry.infra.adapter.persistence.dao.reference.ReferenceStandardDao;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 来源标准查询仓储实现,基于 JPA。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class ReferenceStandardRepositoryAdapter implements ReferenceStandardRepository {

  private final ReferenceStandardDao dao;
  private final ReferenceStandardJpaMapper mapper;

  /// 通过标准代码查询来源标准。
  ///
  /// @param standardCode 标准代码
  /// @return 可选的来源标准
  @Override
  public Optional<ReferenceStandard> findByCode(String standardCode) {
    if (standardCode == null || standardCode.isBlank()) {
      return Optional.empty();
    }
    return dao.findByStandardCode(standardCode).map(mapper::toDomain);
  }
}
