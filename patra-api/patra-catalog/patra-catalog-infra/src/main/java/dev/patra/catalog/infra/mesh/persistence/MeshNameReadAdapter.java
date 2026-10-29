package dev.patra.catalog.infra.mesh.persistence;

import dev.patra.catalog.domain.mesh.port.read.MeshNameReadPort;
import dev.patra.catalog.infra.mesh.persistence.dao.MeshDescriptorDao;
import dev.patra.catalog.infra.mesh.persistence.dao.MeshQualifierDao;
import dev.patra.catalog.infra.mesh.persistence.entity.MeshDescriptorEntity;
import dev.patra.catalog.infra.mesh.persistence.entity.MeshQualifierEntity;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// MeSH 名称读适配器。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class MeshNameReadAdapter implements MeshNameReadPort {

  private final MeshDescriptorDao descriptorDao;
  private final MeshQualifierDao qualifierDao;

  /// 按名称批量查主题词 UI。
  ///
  /// @param names 主题词名称
  /// @return 名称 → UI
  @Override
  public Map<String, String> findDescriptorUisByNameIn(Collection<String> names) {
    if (names == null || names.isEmpty()) {
      return Map.of();
    }
    return descriptorDao.findAllByNameIn(names).stream()
        .collect(
            Collectors.toMap(
                MeshDescriptorEntity::getName,
                MeshDescriptorEntity::getUi,
                (first, next) -> first));
  }

  /// 按名称批量查限定词 UI。
  ///
  /// @param names 限定词名称
  /// @return 名称 → UI
  @Override
  public Map<String, String> findQualifierUisByNameIn(Collection<String> names) {
    if (names == null || names.isEmpty()) {
      return Map.of();
    }
    return qualifierDao.findAllByNameIn(names).stream()
        .collect(
            Collectors.toMap(
                MeshQualifierEntity::getName, MeshQualifierEntity::getUi, (first, next) -> first));
  }
}
