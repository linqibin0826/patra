package com.patra.catalog.infra.persistence.mapper;

import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.util.Collection;
import org.apache.ibatis.annotations.Param;

/// 载体标识符 Mapper 接口 — 对载体标识符表的数据访问操作。
///
/// 继承 `PatraBaseMapper` 以获得批量插入能力（`insertBatchSomeColumn`）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueIdentifierMapper extends PatraBaseMapper<VenueIdentifierDO> {

  /// 物理删除指定 venue 的所有标识符。
  ///
  /// **注意**：此方法绕过 `@TableLogic` 执行物理删除，用于子表的级联删除场景。
  /// SQL 定义在 `VenueIdentifierMapper.xml` 中。
  ///
  /// @param venueIds 载体 ID 集合
  /// @return 删除的记录数
  int physicalDeleteByVenueIds(@Param("venueIds") Collection<Long> venueIds);

  /// 清空表（TRUNCATE TABLE）。
  ///
  /// **警告**：DDL 操作，会隐式提交事务，无法回滚。
  void truncateTable();
}
