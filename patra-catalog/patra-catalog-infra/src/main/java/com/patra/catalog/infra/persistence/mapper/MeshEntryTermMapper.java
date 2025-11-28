package com.patra.catalog.infra.persistence.mapper;

import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;

/// MeSH 入口术语表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshEntryTermMapper extends PatraBaseMapper<MeshEntryTermDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
