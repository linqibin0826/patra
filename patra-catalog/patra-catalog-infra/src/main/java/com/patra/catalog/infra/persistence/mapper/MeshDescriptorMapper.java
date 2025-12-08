package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;

/// MeSH 主题词表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorMapper extends BaseMapper<MeshDescriptorDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
