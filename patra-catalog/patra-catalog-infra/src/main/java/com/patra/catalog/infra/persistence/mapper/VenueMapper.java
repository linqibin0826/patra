package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.VenueDO;

/// 出版载体 Mapper 接口 — 对载体表的数据访问操作。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueMapper extends BaseMapper<VenueDO> {

  /// 清空表（TRUNCATE TABLE）。
  ///
  /// **警告**：DDL 操作，会隐式提交事务，无法回滚。
  void truncateTable();
}
