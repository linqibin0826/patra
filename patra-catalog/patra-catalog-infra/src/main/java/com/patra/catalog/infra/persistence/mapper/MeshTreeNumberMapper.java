package com.patra.catalog.infra.persistence.mapper;

import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;

/// MeSH 树形编号表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshTreeNumberMapper extends PatraBaseMapper<MeshTreeNumberDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
