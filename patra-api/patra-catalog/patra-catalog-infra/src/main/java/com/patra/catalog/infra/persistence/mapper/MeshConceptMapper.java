package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;

public interface MeshConceptMapper extends BaseMapper<MeshConceptDO> {

  /// 清空表（TRUNCATE TABLE）。
  void truncateTable();
}
