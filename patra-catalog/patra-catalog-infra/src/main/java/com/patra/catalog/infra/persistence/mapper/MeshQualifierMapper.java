package com.patra.catalog.infra.persistence.mapper;

import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;

/// MeSH 限定词 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshQualifierMapper extends PatraBaseMapper<MeshQualifierDO> {

  /// 清空限定词表（TRUNCATE TABLE）。
  ///
  /// **警告**：DDL 操作，会隐式提交事务，无法回滚。
  void truncateTable();
}
