package com.patra.catalog.infra.persistence.mapper;

import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;

/// MeSH 概念表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshConceptMapper extends PatraBaseMapper<MeshConceptDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
