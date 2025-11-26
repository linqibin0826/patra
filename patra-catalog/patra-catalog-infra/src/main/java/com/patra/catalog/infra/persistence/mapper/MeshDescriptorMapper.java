package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/// MeSH 主题词表 Mapper 接口。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorMapper extends BaseMapper<MeshDescriptorDO> {

  /// 清空表（TRUNCATE TABLE）。
  void truncateTable();

  /// 批量插入主题词（单条 SQL 语句）。
  ///
  /// 性能说明：使用 JDBC 批量 INSERT（单语句，多行值）。
  ///
  /// 注意：调用方需要预先生成 ID 并填充到 DO 中。
  ///
  /// @param list 要插入的主题词列表
  /// @return 插入的行数
  int insertBatch(@Param("list") List<MeshDescriptorDO> list);
}
