package com.patra.catalog.infra.persistence.mapper;

import com.patra.catalog.infra.persistence.entity.MeshEntryCombinationDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;

/// MeSH 组合条目表 Mapper 接口。
///
/// @author linqibin
/// @since 0.2.1
public interface MeshEntryCombinationMapper extends PatraBaseMapper<MeshEntryCombinationDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
