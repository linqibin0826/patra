package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshEntryCombinationDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/// MeSH 组合条目表 Mapper 接口。
///
/// @author linqibin
/// @since 0.2.1
public interface MeshEntryCombinationMapper extends BaseMapper<MeshEntryCombinationDO> {

  /// 清空表（TRUNCATE TABLE）。
  void truncateTable();

  /// 批量插入组合条目（单条 SQL 语句）。
  ///
  /// 性能说明：使用 JDBC 批量 INSERT（单语句，多行值）。
  ///
  /// @param list 要插入的组合条目列表
  /// @return 插入的行数
  int insertBatch(@Param("list") List<MeshEntryCombinationDO> list);
}
