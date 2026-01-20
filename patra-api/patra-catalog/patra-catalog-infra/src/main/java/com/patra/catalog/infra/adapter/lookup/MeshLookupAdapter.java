package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.port.lookup.MeshLookupPort;
import com.patra.catalog.infra.adapter.persistence.dao.MeshDescriptorDao;
import com.patra.catalog.infra.adapter.persistence.dao.MeshQualifierDao;
import com.patra.catalog.infra.adapter.persistence.entity.MeshDescriptorEntity;
import com.patra.catalog.infra.adapter.persistence.entity.MeshQualifierEntity;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// MeSH 数据查找适配器。
///
/// **职责**：
///
/// - 实现 `MeshLookupPort` 接口
/// - 按 UI 批量查找 MeSH Descriptor 和 Qualifier 的内部 ID
///
/// **性能优化**：
///
/// 使用批量查询避免 N+1 问题，一次查询获取所有匹配的 ID。
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
public class MeshLookupAdapter implements MeshLookupPort {

  private final MeshDescriptorDao meshDescriptorDao;
  private final MeshQualifierDao meshQualifierDao;

  @Override
  public Map<String, Long> findDescriptorIdsByUi(Collection<String> descriptorUis) {
    if (descriptorUis == null || descriptorUis.isEmpty()) {
      return Map.of();
    }

    List<MeshDescriptorEntity> entities = meshDescriptorDao.findAllByUiIn(descriptorUis);
    Map<String, Long> result = new HashMap<>(entities.size());
    for (MeshDescriptorEntity entity : entities) {
      result.put(entity.getUi(), entity.getId());
    }
    return result;
  }

  @Override
  public Map<String, Long> findQualifierIdsByUi(Collection<String> qualifierUis) {
    if (qualifierUis == null || qualifierUis.isEmpty()) {
      return Map.of();
    }

    List<MeshQualifierEntity> entities = meshQualifierDao.findAllByUiIn(qualifierUis);
    Map<String, Long> result = new HashMap<>(entities.size());
    for (MeshQualifierEntity entity : entities) {
      result.put(entity.getUi(), entity.getId());
    }
    return result;
  }
}
