package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;

/// MeSH 入口术语表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshEntryTermMapper extends BaseMapper<MeshEntryTermDO> {

  /// 清空表(TRUNCATE TABLE)。
  void truncateTable();
}
