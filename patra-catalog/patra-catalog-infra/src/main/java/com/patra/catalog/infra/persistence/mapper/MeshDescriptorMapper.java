package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;

public interface MeshDescriptorMapper extends BaseMapper<MeshDescriptorDO> {

  /// 清空表（TRUNCATE TABLE）。
  void truncateTable();
}
