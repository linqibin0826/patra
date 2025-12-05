package com.patra.catalog.infra.persistence.mapper;

import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;
import java.util.Collection;
import org.apache.ibatis.annotations.Param;

/// 载体年度发文统计 Mapper 接口 — 对载体发文统计表的数据访问操作。
///
/// 继承 `PatraBaseMapper` 以获得批量插入能力（`insertBatchSomeColumn`）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenuePublicationStatsMapper extends PatraBaseMapper<VenuePublicationStatsDO> {

  /// 物理删除指定 venue 的所有年度统计。
  ///
  /// **注意**：此方法绕过 `@TableLogic` 执行物理删除，用于子表的级联删除场景。
  /// SQL 定义在 `VenuePublicationStatsMapper.xml` 中。
  ///
  /// @param venueIds 载体 ID 集合
  /// @return 删除的记录数
  int physicalDeleteByVenueIds(@Param("venueIds") Collection<Long> venueIds);

  /// 清空表（TRUNCATE TABLE）。
  ///
  /// **警告**：DDL 操作，会隐式提交事务，无法回滚。
  void truncateTable();
}
