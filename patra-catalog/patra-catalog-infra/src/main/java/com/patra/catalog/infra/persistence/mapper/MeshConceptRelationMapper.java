package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshConceptRelationDO;

/// MeSH 概念关系表 Mapper 接口。
///
/// @author linqibin
/// @since 0.2.0
public interface MeshConceptRelationMapper extends BaseMapper<MeshConceptRelationDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
