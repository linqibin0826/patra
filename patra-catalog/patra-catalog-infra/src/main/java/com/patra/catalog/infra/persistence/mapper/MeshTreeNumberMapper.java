package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/// MeSH 树形编号表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshTreeNumberMapper extends BaseMapper<MeshTreeNumberDO> {

  /// 清空表（TRUNCATE TABLE）。
  void truncateTable();

  /// 批量插入树形编号（单条 SQL 语句）。
  ///
  /// 性能说明：使用 JDBC 批量 INSERT（单语句，多行值）。
  ///
  /// @param list 要插入的树形编号列表
  /// @return 插入的行数
  int insertBatch(@Param("list") List<MeshTreeNumberDO> list);
}
