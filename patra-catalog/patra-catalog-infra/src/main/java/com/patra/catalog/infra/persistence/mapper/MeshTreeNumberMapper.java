package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;

/// MeSH 树形编号表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshTreeNumberMapper extends BaseMapper<MeshTreeNumberDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
