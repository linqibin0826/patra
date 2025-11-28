package com.patra.catalog.infra.persistence.mapper;

import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;

/// MeSH 主题词表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorMapper extends PatraBaseMapper<MeshDescriptorDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
